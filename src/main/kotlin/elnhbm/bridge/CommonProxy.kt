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
        registerDescriptors()
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