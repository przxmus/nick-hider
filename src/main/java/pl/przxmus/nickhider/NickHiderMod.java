package pl.przxmus.nickhider;

/*? if fabric {*/
import net.fabricmc.api.ModInitializer;
/*?}*/

/*? if forge {*/
/*import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import pl.przxmus.nickhider.client.NickHiderForgeClient;
*/
/*?}*/

/*? if neoforge {*/
/*import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
*/
/*?}*/

/*? if forgeLike {*/
@Mod(NickHider.MOD_ID)
public final class NickHiderMod {*/
/*?}*/

/*? if fabric {*/
public final class NickHiderMod implements ModInitializer {
/*?}*/

    private static void bootstrapCommon() {
        /*? if forgeLike {*/
        NickHider.bootstrap(FMLPaths.CONFIGDIR.get());
        /*?}*/
        /*? if fabric {*/
        // TODO: wire platform config path when fabric target is enabled.
        /*?}*/
    }

    /*? if forgeLike {*/
    public NickHiderMod() {
        bootstrapCommon();
        /*? if forge {*/
        NickHiderForgeClient.init();
        /*?}*/
    }
    /*?}*/

    /*? if fabric {*/
    @Override
    public void onInitialize() {
        bootstrapCommon();
    }
    /*?}*/
}
