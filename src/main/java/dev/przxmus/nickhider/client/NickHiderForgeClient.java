package dev.przxmus.nickhider.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import dev.przxmus.nickhider.NickHider;

public final class NickHiderForgeClient {
    private static final KeyMapping OPEN_CONFIG_KEY =
            new KeyMapping("key.nickhider.open_config", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.nickhider");

    private NickHiderForgeClient() {}

    public static void init() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(NickHiderForgeClient::onRegisterKeyMappings);

        MinecraftForge.EVENT_BUS.addListener(NickHiderForgeClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(NickHiderForgeClient::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(NickHiderForgeClient::onLoggingOut);

        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new PrivacyConfigScreen(parent))
        );
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG_KEY);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        while (OPEN_CONFIG_KEY.consumeClick()) {
            minecraft.setScreen(new PrivacyConfigScreen(minecraft.screen));
        }
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        NickHider.runtime().onWorldChange();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        NickHider.runtime().onWorldChange();
    }
}
