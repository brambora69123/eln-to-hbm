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
    // NOTE: we intentionally do NOT declare `required-after:eln`. Forge 1.7.10's
    // @Mod dependency parser lower-cases the mod id, so `required-after:Eln`
    // becomes `required-after:eln` and can never match ELN's real id `Eln`,
    // which would make the bridge fail to load. Instead we rely on FML's
    // event phases (ELN is always fully initialized before our postInit runs)
    // and a runtime presence guard in CommonProxy.postInit().
    dependencies = "required-after:hbm"
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
