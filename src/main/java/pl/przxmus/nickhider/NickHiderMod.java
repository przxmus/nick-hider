package pl.przxmus.nickhider;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import pl.przxmus.nickhider.client.NickHiderForgeClient;

@Mod(NickHider.MOD_ID)
public final class NickHiderMod {
    public NickHiderMod() {
        NickHider.bootstrap(FMLPaths.CONFIGDIR.get());
        NickHiderForgeClient.init();
    }
}
