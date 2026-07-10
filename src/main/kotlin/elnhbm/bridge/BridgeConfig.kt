package elnhbm.bridge

import net.minecraftforge.common.config.Configuration
import java.io.File

object BridgeConfig {
    var largeDamageId: Int = 200
        private set
    var smallDamageId: Int = 201
        private set

    // --- Tank sizes (mB), matching HBM's industrial steam turbine ---
    var inputTankSize: Int = 750_000
        private set
    var outputTankSize: Int = 3_000_000
        private set

    // --- Steam consumption ---
    // Fraction of the (tier-scaled) input tank drained every tick, exactly like
    // HBM's industrial steam turbine (0.2 => 20% per tick).
    var consumptionPercent: Double = 0.2
        private set

    // The small turbine is a fraction of the large one's throughput.
    var smallScale: Double = 1.0 / 9.0
        private set

    // --- Turbine dynamics ---
    // Inertia: time constant for the fluid-rate interpolator used for HUD/sound.
    var inertia: Float = 20f
        private set

    // Peak power lands at 200 rad/s. Above this the governor tapers power to zero
    // by overspeedCutoffRads so the shaft cannot run away to the 250 rad/s
    // explosion limit even with no load.
    var overspeedCutoffRads: Double = 235.0
        private set

    // Fixed mechanical efficiency of the turbine itself (bearings, etc).
    var mechanicalEfficiency: Double = 0.9
        private set

    // --- Heat-energy to ELN joule conversion ---
    // HBM reports heat energy in "HE" units. This scales the recovered heat into
    // ELN shaft joules. With the HBM-matched consumption a large turbine at full
    // STEAM produces ~270k HE/tick, so keep this small.
    var heToJouleFactor: Double = 0.01
        private set

    // --- Shaft inertia (kg·m^2) ---
    var baseShaftMass: Double = 5.0
        private set

    var soundEnabled: Boolean = true
        private set

    // Nominal peak steam throughput (mB/s) for HUD/sound normalisation.
    val nominalSteamRate: Double get() = inputTankSize * consumptionPercent * 20.0

    fun load(configFile: File) {
        val cfg = Configuration(configFile)
        largeDamageId = cfg.getInt("largeDamageId", Configuration.CATEGORY_GENERAL, largeDamageId, 0, 65535, "Damage value for the 3x3x3 turbine")
        smallDamageId = cfg.getInt("smallDamageId", Configuration.CATEGORY_GENERAL, smallDamageId, 0, 65535, "Damage value for the 1x1 turbine")
        inputTankSize = cfg.getInt("inputTankSize", Configuration.CATEGORY_GENERAL, inputTankSize, 1000, 100_000_000, "Input tank capacity (mB, STEAM tier)")
        outputTankSize = cfg.getInt("outputTankSize", Configuration.CATEGORY_GENERAL, outputTankSize, 1000, 100_000_000, "Output tank capacity (mB, STEAM tier)")
        consumptionPercent = cfg.getFloat("consumptionPercent", Configuration.CATEGORY_GENERAL, consumptionPercent.toFloat(), 0.001f, 1.0f, "Fraction of input tank consumed per tick (HBM default 0.2)").toDouble()
        smallScale = cfg.getFloat("smallScale", Configuration.CATEGORY_GENERAL, smallScale.toFloat(), 0.001f, 1.0f, "Small turbine throughput as a fraction of the large one").toDouble()
        inertia = cfg.getFloat("inertia", Configuration.CATEGORY_GENERAL, inertia, 0.1f, 1000f, "Fluid-rate smoothing for HUD/sound (s)")
        overspeedCutoffRads = cfg.getFloat("overspeedCutoffRads", Configuration.CATEGORY_GENERAL, overspeedCutoffRads.toFloat(), 205f, 249f, "Above this rad/s the turbine adds no power").toDouble()
        mechanicalEfficiency = cfg.getFloat("mechanicalEfficiency", Configuration.CATEGORY_GENERAL, mechanicalEfficiency.toFloat(), 0.01f, 10.0f, "Bearing/mechanical efficiency").toDouble()
        heToJouleFactor = cfg.getFloat("heToJouleFactor", Configuration.CATEGORY_GENERAL, heToJouleFactor.toFloat(), 0.00001f, 100_000.0f, "1 HBM heat-unit = X ELN joules").toDouble()
        baseShaftMass = cfg.getFloat("shaftMass", Configuration.CATEGORY_GENERAL, baseShaftMass.toFloat(), 1.0f, 10000.0f, "Base shaft inertia (kg·m^2)").toDouble()
        soundEnabled = cfg.getBoolean("soundEnabled", Configuration.CATEGORY_GENERAL, soundEnabled, "Play turbine sound")
        if (cfg.hasChanged()) cfg.save()
    }
}