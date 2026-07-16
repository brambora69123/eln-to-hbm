package elnhbm.bridge.element

import api.hbm.fluidmk2.IFluidStandardTransceiverMK2
import com.hbm.inventory.fluid.FluidType
import com.hbm.inventory.fluid.Fluids
import com.hbm.inventory.fluid.tank.FluidTank
import com.hbm.inventory.fluid.trait.FT_Coolable
import elnhbm.bridge.BridgeConfig
import elnhbm.bridge.descriptor.HybridTurbineDescriptor
import mods.eln.Eln
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.RcInterpolator
import mods.eln.misc.Utils
import mods.eln.node.NodeBase
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.ThermalLoad
import mods.eln.sim.nbt.NbtElectricalGateInput
import mods.eln.sim.process.destruct.WorldExplosion
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.util.ForgeDirection
import java.io.DataOutputStream

/**
 * A steam turbine that takes HBM steam on one side, expands it through a
 * [FT_Coolable] cooling step, and puts the cooler output back into the HBM
 * pipe network while converting the recovered heat into ELN shaft power.
 *
 * The steam tier is chosen by right-clicking the machine (STEAM -> HOTSTEAM ->
 * SUPERHOTSTEAM -> ULTRAHOTSTEAM, each cooling one tier down on output).
 */
class HybridTurbineElement(node: TransparentNode, desc: TransparentNodeDescriptor) :
    mods.eln.mechanical.SimpleShaftElement(node, desc), IFluidStandardTransceiverMK2 {

    private val adesc = desc as HybridTurbineDescriptor

    // HBM fluid tanks (sized per variant via the descriptor).
    val inputTank = FluidTank(Fluids.STEAM, adesc.inputTankSize)
    val outputTank = FluidTank(Fluids.SPENTSTEAM, adesc.outputTankSize)

    // Selected steam tier index (0=STEAM ... 3=ULTRAHOTSTEAM).
    var fluidMode = 0
        private set

    // Smoothed steam throughput (mB/tick) and resulting efficiency, for HUD/sound.
    var fluidRate = 0f
        private set
    var efficiency = 0f
        private set
    var operational = false
        private set

    // Optional signal-cable throttle: 0..1 scales steam consumption. When no
    // signal wire is connected the turbine runs wide open.
    internal val throttle = NbtElectricalGateInput("throttle")

    private val process = TurbineProcess()

    init {
        slowProcessList.add(process)
        electricalLoadList.add(throttle)
        val exp = WorldExplosion(this as mods.eln.mechanical.ShaftElement).machineExplosion()
        slowProcessList.add(mods.eln.mechanical.createShaftWatchdog(this).setDestroys(exp))
    }

    override fun coordonate(): Coordinate = node!!.coordinate

    override fun initialize() {
        super.initialize()
        applyMode()
    }

    // ------------------------------------------------------------------
    // Steam processing
    // ------------------------------------------------------------------

    private inner class TurbineProcess : IProcess {
        val rc = RcInterpolator(adesc.inertia)

        override fun process(time: Double) {
            connectFluids()
            operational = false

            val type = inputTank.tankType
            if (type === Fluids.NONE || !type.hasTrait(FT_Coolable::class.java)) {
                outputTank.tankType = Fluids.NONE
                efficiency = 0f
                stepRate(0.0, time)
                return
            }

            val trait = type.getTrait(FT_Coolable::class.java)
            val coolEff = trait.getEfficiency(FT_Coolable.CoolingType.TURBINE) * adesc.mechanicalEfficiency
            if (coolEff <= 0.0) {
                efficiency = 0f
                stepRate(0.0, time)
                return
            }
            outputTank.tankType = trait.coolsTo

            // Steam is ALWAYS consumed as long as there is input available,
            // regardless of output tank room.  Excess spent-steam that doesn't
            // fit in the output tank is vented (lost).  This prevents the
            // turbine from freezing when the output pipe network is slower than
            // the input — critical for RBMK closed-loop safety.
            val inputOps = (Math.min(Math.ceil(inputTank.fill * adesc.consumptionPercent), inputTank.fill.toDouble()) / trait.amountReq).toInt()

            if (inputOps > 0) {
                inputTank.fill -= inputOps * trait.amountReq
                // Output goes into the tank if there is room; the rest is vented.
                val room = outputTank.maxFill - outputTank.fill
                val produced = inputOps * trait.amountProduced
                outputTank.fill += Math.min(produced, room)
                operational = true

                val th = if (throttle.connectedComponents.isNotEmpty()) throttle.normalized else 1.0
                val powerFactor = speedGovernor(shaft.rads) * th
                efficiency = powerFactor.toFloat()
                val heat = inputOps.toDouble() * trait.heatEnergy * coolEff
                if (powerFactor > 0.0) shaft.energy += heat * BridgeConfig.heToJouleFactor * powerFactor
            } else {
                efficiency = 0f
            }

            stepRate((inputOps.toDouble() * trait.amountReq) / time, time)
        }

        private fun stepRate(ratePerSecond: Double, time: Double) {
            rc.target = ratePerSecond.toFloat()
            rc.step(time.toFloat())
            fluidRate = rc.get()
        }
    }

    /**
     * Speed-dependent power multiplier. Full power at/under [optimalRads], then a
     * smooth cos^3 taper to zero at [overspeedCutoffRads] so the shaft cannot run
     * away to the 250 rad/s explosion limit even without a load.
     */
    private fun speedGovernor(rads: Double): Double {
        val optimal = adesc.optimalRads
        if (rads <= optimal) return 1.0
        val cutoff = adesc.overspeedCutoffRads
        if (rads >= cutoff) return 0.0
        val x = (rads - optimal) / (cutoff - optimal) * (Math.PI / 2)
        val c = Math.cos(x)
        return c * c * c
    }

    // ------------------------------------------------------------------
    // HBM fluid networking (MK2)
    // ------------------------------------------------------------------

    override fun getAllTanks() = arrayOf(inputTank, outputTank)
    override fun getSendingTanks() = arrayOf(outputTank)
    override fun getReceivingTanks() = arrayOf(inputTank)

    override fun isLoaded() = true

    override fun canConnect(type: FluidType, dir: ForgeDirection): Boolean {
        if (type === Fluids.NONE) return false
        return type.hasTrait(FT_Coolable::class.java)
    }

    /**
     * Re-subscribes to the HBM pipe network every slow tick. We scan the core
     * block and every ghost-block neighbour so pipes attached anywhere on the
     * structure feed the turbine.
     */
    private fun connectFluids() {
        val origin = node?.coordinate ?: return
        val world = origin.world() ?: return
        val ox = origin.x
        val oy = origin.y
        val oz = origin.z

        val scan = mutableListOf(Triple(ox, oy, oz))
        adesc.getGhostGroupFront(front)?.let { gg ->
            for (e in gg.elementList) {
                scan.add(Triple(ox + e.x, oy + e.y, oz + e.z))
            }
        }

        for ((bx, by, bz) in scan) {
            for (dir in ForgeDirection.VALID_DIRECTIONS) {
                val nx = bx + dir.offsetX
                val ny = by + dir.offsetY
                val nz = bz + dir.offsetZ
                if (nx == ox && ny == oy && nz == oz) continue
                val inType = inputTank.tankType
                if (inType !== Fluids.NONE) trySubscribe(inType, world, nx, ny, nz, dir)
                val outType = outputTank.tankType
                val outPressure = outputTank.pressure
                if (outType !== Fluids.NONE) tryProvide(outType, outPressure, world, nx, ny, nz, dir)
            }
        }
    }

    // ------------------------------------------------------------------
    // Electrical (signal-cable throttle) + tool probes
    // ------------------------------------------------------------------

    override fun getElectricalLoad(side: Direction, lrdu: LRDU): ElectricalLoad? = throttle
    override fun getThermalLoad(side: Direction, lrdu: LRDU): ThermalLoad? = null

    override fun getConnectionMask(side: Direction, lrdu: LRDU): Int {
        // Accept a signal wire on the underside of the front and back faces,
        // matching ELN's own turbines. Feed a tachometer here to govern speed.
        if (lrdu == LRDU.Down && (side == front || side == front.back())) {
            return NodeBase.maskElectricalGate
        }
        return 0
    }

    override fun multiMeterString(side: Direction): String =
        Utils.plotER(shaft.energy, shaft.rads)

    override fun thermoMeterString(side: Direction): String {
        val scaled = if (fluidRate >= 1000f) fluidRate / 1000f else fluidRate
        val unit = if (fluidRate >= 1000f) "B/s" else "mB/s"
        return Utils.plotPercent(tr(" Eff:"), efficiency.toDouble()) + " " +
            Utils.plotValue(scaled.toDouble()) + " " + unit
    }

    override fun getWaila(): Map<String, String> {
        val info = LinkedHashMap<String, String>()
        info[tr("Mode")] = "${inputTank.tankType.getLocalizedName()} -> ${outputTank.tankType.getLocalizedName()}"
        info[tr("Speed")] = Utils.plotRads("", shaft.rads)
        info[tr("Energy")] = Utils.plotEnergy("", shaft.energy)
        if (Eln.wailaEasyMode) {
            info[tr("Efficiency")] = Utils.plotPercent("", efficiency.toDouble())
            val scaled = if (fluidRate >= 1000f) fluidRate / 1000f else fluidRate
            val unit = if (fluidRate >= 1000f) "B/s" else "mB/s"
            info[tr("Steam")] = Utils.plotValue(scaled.toDouble()) + " " + unit
            info[tr("Throttle")] =
                if (throttle.connectedComponents.isNotEmpty()) Utils.plotPercent("", throttle.normalized) else tr("open")
        }
        return info
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    override fun onBlockActivated(player: EntityPlayer, side: Direction, vx: Float, vy: Float, vz: Float): Boolean {
        // Let meters and the config tool fall through to the default handler.
        val equipped = player.currentEquippedItem
        if (equipped != null && (
                Eln.multiMeterElement.checkSameItemStack(equipped) ||
                Eln.thermometerElement.checkSameItemStack(equipped) ||
                Eln.allMeterElement.checkSameItemStack(equipped) ||
                Eln.configCopyToolElement.checkSameItemStack(equipped)
            )
        ) {
            return false
        }
        if (!player.worldObj.isRemote) {
            cycleMode()
            needPublish()
        }
        return true
    }

    fun cycleMode() {
        fluidMode = (fluidMode + 1) % 4
        applyMode()
    }

    /**
     * Set tank types and tier-scaled sizes for the current mode. Hotter steam
     * uses a much smaller effective tank (HBM's turbine "compressor resize"), so
     * a fixed consumption fraction stays balanced across tiers since hotter
     * steam needs far less input per operation.
     */
    private fun applyMode() {
        when (fluidMode) {
            0 -> { inputTank.tankType = Fluids.STEAM; outputTank.tankType = Fluids.SPENTSTEAM }
            1 -> { inputTank.tankType = Fluids.HOTSTEAM; outputTank.tankType = Fluids.STEAM }
            2 -> { inputTank.tankType = Fluids.SUPERHOTSTEAM; outputTank.tankType = Fluids.HOTSTEAM }
            3 -> { inputTank.tankType = Fluids.ULTRAHOTSTEAM; outputTank.tankType = Fluids.SUPERHOTSTEAM }
        }
        val div = when (fluidMode) {
            0 -> 1
            1 -> 10
            2 -> 100
            else -> 1000
        }
        inputTank.changeTankSize((adesc.inputTankSize / div).coerceAtLeast(1))
        outputTank.changeTankSize((adesc.outputTankSize / div).coerceAtLeast(1))
    }

    // ------------------------------------------------------------------
    // NBT / network
    // ------------------------------------------------------------------

    override fun writeToNBT(nbt: NBTTagCompound) {
        super.writeToNBT(nbt)
        inputTank.writeToNBT(nbt, "input")
        outputTank.writeToNBT(nbt, "output")
        nbt.setInteger("fluidMode", fluidMode)
        nbt.setFloat("fluidRate", fluidRate)
    }

    override fun readFromNBT(nbt: NBTTagCompound) {
        super.readFromNBT(nbt)
        inputTank.readFromNBT(nbt, "input")
        outputTank.readFromNBT(nbt, "output")
        fluidMode = nbt.getInteger("fluidMode")
        fluidRate = nbt.getFloat("fluidRate")
        applyMode()
    }

    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        stream.writeFloat(fluidRate)
        stream.writeBoolean(operational)
        stream.writeFloat(efficiency)
    }
}