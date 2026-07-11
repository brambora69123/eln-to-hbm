package elnhbm.bridge

import cpw.mods.fml.common.event.FMLInitializationEvent
import cpw.mods.fml.common.event.FMLPostInitializationEvent
import cpw.mods.fml.common.event.FMLPreInitializationEvent
import elnhbm.bridge.descriptor.HybridTurbineDescriptor
import mods.eln.Eln

open class CommonProxy {

    open fun preInit(event: FMLPreInitializationEvent) {
        BridgeConfig.load(event.suggestedConfigurationFile)
    }

    open fun init(event: FMLInitializationEvent) {}

    open fun postInit(event: FMLPostInitializationEvent) {
        if (!elnPresent()) {
            BridgeMod.LOG.error(
                "Electrical Age (ELN, mod id 'Eln') was not found. " +
                    "HBM-ELN Bridge will not register the hybrid turbine."
            )
            return
        }
        registerDescriptors()
    }

    /**
     * ELN is referenced only at runtime (its classes live in ELN's jar on the shared
     * mod classloader). We avoid declaring it via Forge's @Mod dependency (which
     * lower-cases `Eln` to `eln` and can never match). Instead we probe for
     * ELN's main class here, before any ELN type is touched, so a missing ELN
     * degrades to a clear log line instead of a NoClassDefFoundError.
     */
    private fun elnPresent(): Boolean = try {
        Class.forName("mods.eln.Eln")
        true
    } catch (_: Throwable) {
        false
    }

    private fun registerDescriptors() {
        val obj = Eln.obj ?: run {
            BridgeMod.LOG.warn("Eln.obj is null, cannot load turbine model")
            return
        }
        val turbineObj = obj.getObj("Turbine") ?: run {
            BridgeMod.LOG.warn("Could not load Turbine model from ELN obj")
            return
        }

        val large = HybridTurbineDescriptor(
            name = "Hybrid Steam Turbine",
            obj = turbineObj,
            isLarge = true
        )
        Eln.transparentNodeItem.addDescriptor(BridgeConfig.largeDamageId, large)
        BridgeMod.LOG.info("Registered large Hybrid Turbine (damage ${BridgeConfig.largeDamageId})")

        val small = HybridTurbineDescriptor(
            name = "Hybrid Steam Turbine (Small)",
            obj = turbineObj,
            isLarge = false
        )
        Eln.transparentNodeItem.addDescriptor(BridgeConfig.smallDamageId, small)
        BridgeMod.LOG.info("Registered small Hybrid Turbine (damage ${BridgeConfig.smallDamageId})")
    }
}