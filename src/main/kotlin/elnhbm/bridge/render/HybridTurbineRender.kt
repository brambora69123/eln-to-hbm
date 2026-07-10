package elnhbm.bridge.render

import elnhbm.bridge.descriptor.HybridTurbineDescriptor
import mods.eln.mechanical.ShaftRender
import mods.eln.misc.Coordinate
import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.sound.LoopedSound
import java.io.DataInputStream

class HybridTurbineRender(entity: TransparentNodeEntity, desc: TransparentNodeDescriptor) :
    ShaftRender(entity, desc) {

    private val turbineDesc = desc as HybridTurbineDescriptor

    private var fluidRate = 0f
    private var operational = false
    private var efficiency = 0f

    private var soundLooper: LoopedSound? = null

    init {
        // Register our own turbine sound. ELN's `sound` field is `internal` and
        // cannot be overridden from this module, so we drive it here directly.
        volumeSetting.target = 1f
        volumeSetting.position = 0f
        soundLooper = TurbineSoundLooper("eln:steam_turbine", coordinate())
        addLoopedSound(soundLooper)
    }

    private inner class TurbineSoundLooper(sound: String, coord: Coordinate) : LoopedSound(sound, coord) {
        override fun getPitch(): Float {
            val max = turbineDesc.nominalSteamRate
            val frac = if (max > 0.0) (fluidRate / max).coerceIn(0.0, 1.0) else 0.0
            return (0.05 + 0.95 * frac).toFloat()
        }

        override fun getVolume(): Float {
            return volumeSetting.position
        }
    }

    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream)
        fluidRate = stream.readFloat()
        operational = stream.readBoolean()
        efficiency = stream.readFloat()
        volumeSetting.target = if (operational) (0.15f + efficiency * 0.85f).coerceIn(0f, 1f) else 0f
    }

    override fun draw() {
        draw {}
    }
}