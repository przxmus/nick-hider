package dev.przxmus.nickhider.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import dev.przxmus.nickhider.NickHider;
/*? if fabric {*/
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
/*?}*/
/*? if forge {*/
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
/*?}*/
/*? if neoforge {*/
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
*/
/*?}*/

public final class NickHiderClient {
    private static final KeyMapping OPEN_CONFIG_KEY =
            new KeyMapping("key.nickhider.open_config", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.nickhider");

    private NickHiderClient() {}

    /*? if fabric {*/
    public static void initFabric() {
        KeyBindingHelper.registerKeyBinding(OPEN_CONFIG_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(NickHiderClient::onClientTickFabric);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onWorldChange());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onWorldChange());
    }

    private static void onClientTickFabric(Minecraft minecraft) {
        tryOpenConfigScreen(minecraft);
    }
    /*?}*/

    /*? if forge {*/
    public static void initForge(IEventBus modEventBus) {
        modEventBus.addListener(NickHiderClient::onRegisterKeyMappingsForge);
        MinecraftForge.EVENT_BUS.addListener(NickHiderClient::onClientTickForge);
        MinecraftForge.EVENT_BUS.addListener(NickHiderClient::onLoggingInForge);
        MinecraftForge.EVENT_BUS.addListener(NickHiderClient::onLoggingOutForge);

        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new PrivacyConfigScreen(parent))
        );
    }

    private static void onRegisterKeyMappingsForge(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG_KEY);
    }

    private static void onClientTickForge(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tryOpenConfigScreen(Minecraft.getInstance());
        }
    }

    private static void onLoggingInForge(ClientPlayerNetworkEvent.LoggingIn event) {
        onWorldChange();
    }

    private static void onLoggingOutForge(ClientPlayerNetworkEvent.LoggingOut event) {
        onWorldChange();
    }
    /*?}*/

    /*? if neoforge {*/
    /*public static void initNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(NickHiderClient::onRegisterKeyMappingsNeoForge);
        NeoForge.EVENT_BUS.addListener(NickHiderClient::onClientTickNeoForge);
        NeoForge.EVENT_BUS.addListener(NickHiderClient::onLoggingInNeoForge);
        NeoForge.EVENT_BUS.addListener(NickHiderClient::onLoggingOutNeoForge);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (minecraft, parent) -> new PrivacyConfigScreen(parent));
    }

    private static void onRegisterKeyMappingsNeoForge(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG_KEY);
    }

    private static void onClientTickNeoForge(ClientTickEvent.Post event) {
        tryOpenConfigScreen(Minecraft.getInstance());
    }

    private static void onLoggingInNeoForge(ClientPlayerNetworkEvent.LoggingIn event) {
        onWorldChange();
    }

    private static void onLoggingOutNeoForge(ClientPlayerNetworkEvent.LoggingOut event) {
        onWorldChange();
    }*/
    /*?}*/

    private static void tryOpenConfigScreen(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }

        while (OPEN_CONFIG_KEY.consumeClick()) {
            minecraft.setScreen(new PrivacyConfigScreen(minecraft.screen));
        }
    }

    private static void onWorldChange() {
        NickHider.runtime().onWorldChange();
    }
}
