package dev.przxmus.nickhider.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import dev.przxmus.nickhider.NickHider;
/*? if fabric {*/
/*import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
*/
/*?}*/
/*? if forge {*/
/*? if <1.21.6 {*/
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
/*?}*/
/*?}*/
/*? if neoforge {*/
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
*/
/*?}*/

public final class NickHiderClient {
    private static final KeyMapping OPEN_CONFIG_KEY = createOpenConfigKey();

    private NickHiderClient() {}

    private static KeyMapping createOpenConfigKey() {
        try {
            Constructor<KeyMapping> legacyCtor = KeyMapping.class.getConstructor(String.class, InputConstants.Type.class, int.class, String.class);
            return legacyCtor.newInstance(
                    "key.nickhider.open_config",
                    InputConstants.Type.KEYSYM,
                    InputConstants.UNKNOWN.getValue(),
                    "key.categories.nickhider"
            );
        } catch (ReflectiveOperationException ignored) {}

        try {
            Class<?> categoryClass = Class.forName("net.minecraft.client.KeyMapping$Category");
            Object category = resolveKeyCategory(categoryClass);
            Constructor<KeyMapping> modernCtor = KeyMapping.class.getConstructor(String.class, InputConstants.Type.class, int.class, categoryClass);
            return modernCtor.newInstance(
                    "key.nickhider.open_config",
                    InputConstants.Type.KEYSYM,
                    InputConstants.UNKNOWN.getValue(),
                    category
            );
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to construct key mapping for current Minecraft version", ex);
        }
    }

    private static Object resolveKeyCategory(Class<?> categoryClass) throws ReflectiveOperationException {
        try {
            Method createMethod = categoryClass.getMethod("create", String.class);
            return createMethod.invoke(null, "key.categories.nickhider");
        } catch (NoSuchMethodException ignored) {}

        if (categoryClass.isEnum()) {
            Object[] constants = categoryClass.getEnumConstants();
            if (constants != null && constants.length > 0) {
                return constants[0];
            }
        }

        throw new IllegalStateException("Unable to resolve key category for current Minecraft version");
    }

    /*? if fabric {*/
    /*public static void initFabric() {
        KeyBindingHelper.registerKeyBinding(OPEN_CONFIG_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(NickHiderClient::onClientTickFabric);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onWorldChange());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onWorldChange());
    }

    private static void onClientTickFabric(Minecraft minecraft) {
        tryOpenConfigScreen(minecraft);
    }*/
    /*?}*/

    /*? if forge && <1.21.6 {*/
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

    /*? if forge && >=1.21.6 {*/
    /*public static void initForge() {
        // Intentionally minimal for broad Forge 1.21.6+ compatibility in Architectury Loom.
    }*/
    /*?}*/

    /*? if neoforge {*/
    /*public static void initNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        // Intentionally minimal for broad API compatibility across NeoForge lines.
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
