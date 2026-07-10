package elnhbm.bridge

import cpw.mods.fml.common.Mod
import cpw.mods.fml.common.SidedProxy
import cpw.mods.fml.common.event.FMLInitializationEvent
import cpw.mods.fml.common.event.FMLPostInitializationEvent
import cpw.mods.fml.common.event.FMLPreInitializationEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Mod(
    modid = BridgeMod.MODID,
    version = Tags.VERSION,
    name = "HBM-ELN Bridge",
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:hbm;required-after:eln"
)
class BridgeMod {
    companion object {
        const val MODID = "elnhbmbridge"
        val LOG: Logger = LogManager.getLogger(MODID)

        @SidedProxy(
            clientSide = "elnhbm.bridge.ClientProxy",
            serverSide = "elnhbm.bridge.CommonProxy"
        )
        lateinit var proxy: CommonProxy

        @Mod.Instance(MODID)
        lateinit var instance: BridgeMod
    }

    @Mod.EventHandler
    fun preInit(event: FMLPreInitializationEvent) {
        proxy.preInit(event)
    }

    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        proxy.init(event)
    }

    @Mod.EventHandler
    fun postInit(event: FMLPostInitializationEvent) {
        proxy.postInit(event)
    }
}
