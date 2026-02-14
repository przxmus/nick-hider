package dev.przxmus.nickhider;

/*? if fabric {*/
/*import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
*/
/*?}*/
/*? if forge {*/
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
/*?}*/
/*? if neoforge {*/
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
*/
/*?}*/
import dev.przxmus.nickhider.client.NickHiderClient;

/*? if forgeLike {*/
@Mod(NickHider.MOD_ID)
public final class NickHiderMod {
/*?}*/
/*? if fabric {*/
/*public final class NickHiderMod implements ModInitializer {*/
/*?}*/
    /*? if fabric {*/
    /*@Override
    public void onInitialize() {
        NickHider.bootstrap(FabricLoader.getInstance().getConfigDir());
        NickHiderClient.initFabric();
    }*/
    /*?}*/

    /*? if forge {*/
    public NickHiderMod() {
        NickHider.bootstrap(FMLPaths.CONFIGDIR.get());
        NickHiderClient.initForge(FMLJavaModLoadingContext.get().getModEventBus());
    }
    /*?}*/

    /*? if neoforge {*/
    /*public NickHiderMod(IEventBus modEventBus, ModContainer modContainer) {
        NickHider.bootstrap(FMLPaths.CONFIGDIR.get());
        NickHiderClient.initNeoForge(modEventBus, modContainer);
    }*/
    /*?}*/
}
