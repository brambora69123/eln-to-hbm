package elnhbm.bridge.descriptor

import elnhbm.bridge.BridgeConfig
import elnhbm.bridge.element.HybridTurbineElement
import elnhbm.bridge.render.HybridTurbineRender
import mods.eln.ghost.GhostGroup
import mods.eln.i18n.I18N.tr
import mods.eln.mechanical.SimpleShaftDescriptor
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.Obj3D
import mods.eln.misc.Utils
import mods.eln.misc.preserveMatrix
import mods.eln.node.transparent.EntityMetaTag
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraftforge.client.IItemRenderer

class HybridTurbineDescriptor(
    name: String,
    obj: Obj3D,
    val isLarge: Boolean
) : SimpleShaftDescriptor(name, HybridTurbineElement::class, HybridTurbineRender::class, EntityMetaTag.Basic) {

    override val obj = obj
    override val static = arrayOf(
        obj.getPart("Cowl"),
        obj.getPart("Stand")
    ).requireNoNulls()
    override val rotating = arrayOf(
        obj.getPart("Shaft"),
        obj.getPart("Fan")
    ).requireNoNulls()

    // Time constant for the fluid-rate interpolator (seconds).
    val inertia: Float = BridgeConfig.inertia
    // Peak-power shaft speed (rad/s); ELN convention is 80% of the absolute max = 200.
    val optimalRads = mods.eln.mechanical.absoluteMaximumShaftSpeed * 0.8
    // Above this speed the governor tapers power to zero (overspeed protection).
    val overspeedCutoffRads: Double = BridgeConfig.overspeedCutoffRads
    // Fraction of the input tank consumed per tick (HBM industrial turbine model).
    // Both variants drain the same fraction; the small one just has smaller tanks,
    // so its throughput scales down proportionally (HBM-consistent feel).
    val consumptionPercent: Double = BridgeConfig.consumptionPercent
    // Tank-size (and thus throughput) scale relative to the large turbine.
    val tankScale: Double = if (isLarge) 1.0 else BridgeConfig.smallScale
    // Base (STEAM tier) tank sizes for this variant, in mB.
    val inputTankSize: Int = (BridgeConfig.inputTankSize * tankScale).toInt().coerceAtLeast(1)
    val outputTankSize: Int = (BridgeConfig.outputTankSize * tankScale).toInt().coerceAtLeast(1)
    // Fixed mechanical (bearing) efficiency.
    val mechanicalEfficiency: Double = BridgeConfig.mechanicalEfficiency
    // Nominal peak steam throughput (mB/s) for HUD/sound normalisation.
    val nominalSteamRate: Double = inputTankSize * consumptionPercent * 20.0

    override var shaftMass = BridgeConfig.baseShaftMass

    init {
        voltageLevelColor = mods.eln.misc.VoltageLevelColor.Neutral
        if (isLarge) {
            modelScale = 3.0f
            shaftMass *= 27.0
            disableCameraOptimization = true

            val gg = GhostGroup()
            for (x in -1..1) {
                for (y in 0..2) {
                    for (z in -1..1) {
                        if (x == 0 && y == 0 && z == 0) continue
                        if (y == 0 && x != 0) continue
                        gg.addElement(x, y, z)
                    }
                }
            }
            ghostGroup = gg
            // Axial shaft ports at the vertical centre of the 3x3x3.
            addShaftGhostPort(Coordinate(0, 1, -1, 0), Direction.ZN, Direction.ZN)
            addShaftGhostPort(Coordinate(0, 1, 1, 0), Direction.ZP, Direction.ZP)
        } else {
            modelScale = 1.0f
            // Single block: no ghost group, so no ghost shaft ports. The shaft
            // connects directly on the block's left/right faces via the default
            // SimpleShaftElement.shaftConnectivity.
        }
    }

    override fun addInformation(stack: ItemStack, player: EntityPlayer, list: MutableList<String>, par4: Boolean) {
        list.add(tr("Converts HBM steam into ELN mechanical shaft power."))
        list.add(tr("Nominal usage ->"))
        val rate = nominalSteamRate
        val scaled = if (rate >= 1000.0) rate / 1000.0 else rate
        val unit = if (rate >= 1000.0) "B/s" else "mB/s"
        list.add("  " + tr("Peak steam input: %1$ %2$", Utils.plotValue(scaled), unit))
        list.add("  " + tr("Input: HBM steam (STEAM / HOTSTEAM / SUPERHOTSTEAM / ULTRAHOTSTEAM)"))
        list.add("  " + tr("Output: the next cooler tier (SPENTSTEAM, etc.)"))
        list.add(Utils.plotRads(tr("  Optimal rads: "), optimalRads))
        list.add(Utils.plotRads(tr("  Max rads: "), mods.eln.mechanical.absoluteMaximumShaftSpeed))
        list.add("  " + tr("Right-click to cycle steam tier"))
        list.add("  " + tr("Needs a load or brake, or it will overspeed"))
        list.add(if (isLarge) tr("3x3x3 multiblock") else tr("Single block"))
    }

    override fun handleRenderType(item: ItemStack, type: IItemRenderer.ItemRenderType): Boolean = true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType, item: ItemStack, helper: IItemRenderer.ItemRendererHelper): Boolean =
        type != IItemRenderer.ItemRenderType.INVENTORY

    override fun renderItem(type: IItemRenderer.ItemRenderType, item: ItemStack, vararg data: Any) {
        if (type == IItemRenderer.ItemRenderType.INVENTORY) {
            super.renderItem(type, item, *data)
        } else {
            objItemScale(obj)
            preserveMatrix {
                Direction.ZN.glRotateXnRef()
                org.lwjgl.opengl.GL11.glTranslatef(0f, -1f, 0f)
                org.lwjgl.opengl.GL11.glScalef(0.6f, 0.6f, 0.6f)
                draw(0.0)
            }
        }
    }
}