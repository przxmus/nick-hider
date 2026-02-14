package dev.przxmus.nickhider.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.StringUtil;
import com.mojang.blaze3d.platform.NativeImage;
import dev.przxmus.nickhider.NickHider;

public final class SkinResolutionService {
    private static final Gson GSON = new Gson();

    private final Path cacheDirectory;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Map<String, ResolvedSkin> memoryCache = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public SkinResolutionService(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "nickhider-skin-fetcher");
            thread.setDaemon(true);
            return thread;
        });
    }

    public ResolvedSkin resolveOrFallback(String username, UUID fallbackUuid) {
        if (StringUtil.isNullOrEmpty(username)) {
            return defaultSkin(fallbackUuid);
        }

        String normalized = username.toLowerCase(Locale.ROOT);
        ResolvedSkin cached = memoryCache.get(normalized);
        if (cached != null) {
            return cached;
        }

        ResolvedSkin diskCached = loadFromDisk(normalized);
        if (diskCached != null) {
            memoryCache.put(normalized, diskCached);
            return diskCached;
        }

        enqueueFetch(normalized);
        return defaultSkin(fallbackUuid);
    }

    public void clearRuntimeCache() {
        memoryCache.clear();
    }

    private void enqueueFetch(String normalizedUsername) {
        if (!inFlight.add(normalizedUsername)) {
            return;
        }

        CompletableFuture.runAsync(() -> fetchAndStore(normalizedUsername), executor)
                .whenComplete((unused, throwable) -> {
                    inFlight.remove(normalizedUsername);
                    if (throwable != null) {
                        NickHider.LOGGER.warn("Skin fetch failed for {}", normalizedUsername, throwable);
                    }
                });
    }

    private void fetchAndStore(String normalizedUsername) {
        try {
            Files.createDirectories(cacheDirectory);

            String uuidNoDashes = lookupUuid(normalizedUsername);
            if (uuidNoDashes == null) {
                return;
            }

            SkinLookupResult lookupResult = lookupSkin(uuidNoDashes);
            if (lookupResult == null || lookupResult.skinUrl == null) {
                return;
            }

            byte[] skinBytes = downloadTexture(lookupResult.skinUrl);
            if (skinBytes == null) {
                return;
            }

            Path skinPngPath = skinTexturePath(normalizedUsername);
            Path metaPath = metaPath(normalizedUsername);
            Path capePngPath = capeTexturePath(normalizedUsername);
            Path elytraPngPath = elytraTexturePath(normalizedUsername);

            if (!writeProcessedSkinTexture(skinPngPath, skinBytes, normalizedUsername)) {
                return;
            }
            writeOptionalTexture(capePngPath, lookupResult.capeUrl);
            writeOptionalTexture(elytraPngPath, lookupResult.elytraUrl);

            JsonObject meta = new JsonObject();
            meta.addProperty("model", lookupResult.modelName);
            try (Writer writer = Files.newBufferedWriter(metaPath, StandardCharsets.UTF_8)) {
                GSON.toJson(meta, writer);
            }
        } catch (Exception ex) {
            NickHider.LOGGER.warn("Failed to fetch skin for {}", normalizedUsername, ex);
        }
    }

    private boolean writeProcessedSkinTexture(Path skinPath, byte[] skinBytes, String normalizedUsername) {
        try {
            NativeImage image = NativeImage.read(skinBytes);
            NativeImage processed = processLegacySkin(image, normalizedUsername);
            if (processed == null) {
                return false;
            }

            try (processed) {
                processed.writeToFile(skinPath);
            }
            return true;
        } catch (IOException ex) {
            NickHider.LOGGER.warn("Failed to process downloaded skin for {}", normalizedUsername, ex);
            return false;
        }
    }

    private byte[] downloadTexture(String textureUrl) throws IOException, InterruptedException {
        HttpRequest imageRequest = HttpRequest.newBuilder(URI.create(textureUrl))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<byte[]> imageResponse = httpClient.send(imageRequest, HttpResponse.BodyHandlers.ofByteArray());
        if (imageResponse.statusCode() != 200) {
            return null;
        }
        return imageResponse.body();
    }

    private void writeOptionalTexture(Path texturePath, String textureUrl) throws IOException, InterruptedException {
        if (textureUrl == null || textureUrl.isBlank()) {
            Files.deleteIfExists(texturePath);
            return;
        }

        byte[] bytes = downloadTexture(textureUrl);
        if (bytes == null) {
            Files.deleteIfExists(texturePath);
            return;
        }

        Files.write(texturePath, bytes);
    }

    private String lookupUuid(String username) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                .GET()
                .timeout(Duration.ofSeconds(8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            return null;
        }

        JsonObject object = JsonParser.parseString(response.body()).getAsJsonObject();
        return object.has("id") ? object.get("id").getAsString() : null;
    }

    private SkinLookupResult lookupSkin(String uuidNoDashes) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuidNoDashes + "?unsigned=false"))
                .GET()
                .timeout(Duration.ofSeconds(8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            return null;
        }

        JsonObject profile = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!profile.has("properties")) {
            return null;
        }

        for (var element : profile.getAsJsonArray("properties")) {
            JsonObject property = element.getAsJsonObject();
            if (!"textures".equals(property.get("name").getAsString())) {
                continue;
            }

            String encoded = property.get("value").getAsString();
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            JsonObject texturesRoot = JsonParser.parseString(decoded).getAsJsonObject();
            if (!texturesRoot.has("textures") || !texturesRoot.getAsJsonObject("textures").has("SKIN")) {
                return null;
            }

            JsonObject textures = texturesRoot.getAsJsonObject("textures");
            JsonObject skin = textures.getAsJsonObject("SKIN");
            String skinUrl = skin.get("url").getAsString();
            String model = ResolvedSkin.MODEL_DEFAULT;
            if (skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")) {
                model = skin.getAsJsonObject("metadata").get("model").getAsString();
            }

            String capeUrl = null;
            if (textures.has("CAPE")) {
                JsonObject cape = textures.getAsJsonObject("CAPE");
                if (cape.has("url")) {
                    capeUrl = cape.get("url").getAsString();
                }
            }

            String elytraUrl = null;
            if (textures.has("ELYTRA")) {
                JsonObject elytra = textures.getAsJsonObject("ELYTRA");
                if (elytra.has("url")) {
                    elytraUrl = elytra.get("url").getAsString();
                }
            }

            return new SkinLookupResult(skinUrl, model, capeUrl, elytraUrl);
        }

        return null;
    }

    private ResolvedSkin loadFromDisk(String normalizedUsername) {
        Path pngPath = skinTexturePath(normalizedUsername);
        Path metaPath = metaPath(normalizedUsername);
        Path capePath = capeTexturePath(normalizedUsername);
        Path elytraPath = elytraTexturePath(normalizedUsername);

        if (!Files.exists(pngPath) || !Files.exists(metaPath)) {
            return null;
        }

        String modelName = ResolvedSkin.MODEL_DEFAULT;
        try (Reader reader = Files.newBufferedReader(metaPath, StandardCharsets.UTF_8)) {
            JsonObject meta = JsonParser.parseReader(reader).getAsJsonObject();
            if (meta.has("model")) {
                modelName = meta.get("model").getAsString();
            }
        } catch (Exception ex) {
            NickHider.LOGGER.warn("Failed to read skin metadata {}", metaPath, ex);
        }

        try (InputStream input = Files.newInputStream(pngPath)) {
            NativeImage image = NativeImage.read(input);
            NativeImage processed = processLegacySkin(image, normalizedUsername);
            if (processed == null) {
                return null;
            }

            Object location = createResourceLocation(NickHider.MOD_ID, "cached_skin/" + normalizedUsername);
            Minecraft minecraft = Minecraft.getInstance();
            TextureManager textureManager = minecraft.getTextureManager();
            registerTexture(textureManager, location, createDynamicTexture(processed));

            Object capeLocation = registerOptionalTexture(capePath, "cached_cape/" + normalizedUsername, textureManager);
            Object elytraLocation = registerOptionalTexture(elytraPath, "cached_elytra/" + normalizedUsername, textureManager);
            return new ResolvedSkin(location, modelName, capeLocation, elytraLocation);
        } catch (IOException ex) {
            NickHider.LOGGER.warn("Failed to load cached skin {}", pngPath, ex);
            return null;
        }
    }

    private Object registerOptionalTexture(Path path, String textureKey, TextureManager textureManager) {
        if (!Files.exists(path)) {
            return null;
        }

        try (InputStream input = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(input);
            Object location = createResourceLocation(NickHider.MOD_ID, textureKey);
            registerTexture(textureManager, location, createDynamicTexture(image));
            return location;
        } catch (IOException ex) {
            NickHider.LOGGER.warn("Failed to load cached texture {}", path, ex);
            return null;
        }
    }

    private Path skinTexturePath(String normalizedUsername) {
        return cacheDirectory.resolve(normalizedUsername + ".png");
    }

    private Path capeTexturePath(String normalizedUsername) {
        return cacheDirectory.resolve(normalizedUsername + "-cape.png");
    }

    private Path elytraTexturePath(String normalizedUsername) {
        return cacheDirectory.resolve(normalizedUsername + "-elytra.png");
    }

    private Path metaPath(String normalizedUsername) {
        return cacheDirectory.resolve(normalizedUsername + ".json");
    }

    private NativeImage processLegacySkin(NativeImage source, String normalizedUsername) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width != 64 || (height != 32 && height != 64)) {
            source.close();
            NickHider.LOGGER.warn("Discarding incorrectly sized ({}x{}) skin texture for {}", width, height, normalizedUsername);
            return null;
        }

        boolean legacy = height == 32;
        NativeImage image = source;
        if (legacy) {
            NativeImage expanded = new NativeImage(64, 64, true);
            expanded.copyFrom(source);
            source.close();
            image = expanded;

            image.fillRect(0, 32, 64, 32, 0);
            image.copyRect(4, 16, 16, 32, 4, 4, true, false);
            image.copyRect(8, 16, 16, 32, 4, 4, true, false);
            image.copyRect(0, 20, 24, 32, 4, 12, true, false);
            image.copyRect(4, 20, 16, 32, 4, 12, true, false);
            image.copyRect(8, 20, 8, 32, 4, 12, true, false);
            image.copyRect(12, 20, 16, 32, 4, 12, true, false);
            image.copyRect(44, 16, -8, 32, 4, 4, true, false);
            image.copyRect(48, 16, -8, 32, 4, 4, true, false);
            image.copyRect(40, 20, 0, 32, 4, 12, true, false);
            image.copyRect(44, 20, -8, 32, 4, 12, true, false);
            image.copyRect(48, 20, -16, 32, 4, 12, true, false);
            image.copyRect(52, 20, -8, 32, 4, 12, true, false);
        }

        setNoAlpha(image, 0, 0, 32, 16);
        if (legacy) {
            doNotchTransparencyHack(image, 32, 0, 64, 32);
        }
        setNoAlpha(image, 0, 16, 64, 32);
        setNoAlpha(image, 16, 48, 48, 64);
        return image;
    }

    private static void doNotchTransparencyHack(NativeImage image, int minX, int minY, int maxX, int maxY) {
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                int pixel = getPixel(image, x, y);
                if (((pixel >> 24) & 255) < 128) {
                    return;
                }
            }
        }

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                setPixel(image, x, y, getPixel(image, x, y) & 0x00FFFFFF);
            }
        }
    }

    private static void setNoAlpha(NativeImage image, int minX, int minY, int maxX, int maxY) {
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                setPixel(image, x, y, getPixel(image, x, y) | 0xFF000000);
            }
        }
    }

    private ResolvedSkin defaultSkin(UUID fallbackUuid) {
        Object location = resolveDefaultSkinLocation(fallbackUuid);
        String modelName = resolveDefaultSkinModel(fallbackUuid);
        return new ResolvedSkin(location, modelName, null, null);
    }

    private static int getPixel(NativeImage image, int x, int y) {
        try {
            Method method = NativeImage.class.getMethod("getPixelRGBA", int.class, int.class);
            return (int) method.invoke(image, x, y);
        } catch (ReflectiveOperationException ignored) {}

        try {
            Method method = NativeImage.class.getMethod("getPixel", int.class, int.class);
            return (int) method.invoke(image, x, y);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to read native image pixel", ex);
        }
    }

    private static void setPixel(NativeImage image, int x, int y, int value) {
        try {
            Method method = NativeImage.class.getMethod("setPixelRGBA", int.class, int.class, int.class);
            method.invoke(image, x, y, value);
            return;
        } catch (ReflectiveOperationException ignored) {}

        try {
            Method method = NativeImage.class.getMethod("setPixel", int.class, int.class, int.class);
            method.invoke(image, x, y, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to write native image pixel", ex);
        }
    }

    private static Object createResourceLocation(String namespace, String path) {
        try {
            Class<?> resourceLocationClass = Class.forName("net.minecraft.resources.ResourceLocation");
            return constructResourceLocation(resourceLocationClass, namespace, path);
        } catch (ReflectiveOperationException ignored) {}

        try {
            Class<?> identifierClass = Class.forName("net.minecraft.util.Identifier");
            return constructResourceLocation(identifierClass, namespace, path);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to create resource identifier", ex);
        }
    }

    private static Object constructResourceLocation(Class<?> locationClass, String namespace, String path) throws ReflectiveOperationException {
        try {
            Constructor<?> ctor = locationClass.getDeclaredConstructor(String.class, String.class);
            ctor.setAccessible(true);
            return ctor.newInstance(namespace, path);
        } catch (NoSuchMethodException ignored) {}

        for (String methodName : new String[] {"fromNamespaceAndPath", "of", "tryBuild"}) {
            try {
                Method method = locationClass.getMethod(methodName, String.class, String.class);
                return method.invoke(null, namespace, path);
            } catch (NoSuchMethodException ignored) {}
        }

        Method parseMethod = locationClass.getMethod("parse", String.class);
        return parseMethod.invoke(null, namespace + ":" + path);
    }

    private static DynamicTexture createDynamicTexture(NativeImage image) {
        try {
            Constructor<DynamicTexture> ctor = DynamicTexture.class.getConstructor(NativeImage.class);
            return ctor.newInstance(image);
        } catch (ReflectiveOperationException ignored) {}

        try {
            Constructor<DynamicTexture> ctor = DynamicTexture.class.getConstructor(Supplier.class, NativeImage.class);
            @SuppressWarnings("unchecked")
            Supplier<String> idSupplier = () -> "nickhider-runtime";
            return ctor.newInstance(idSupplier, image);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to create dynamic texture", ex);
        }
    }

    private static void registerTexture(TextureManager textureManager, Object location, DynamicTexture texture) {
        for (Method method : textureManager.getClass().getMethods()) {
            if (!"register".equals(method.getName()) || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (!parameterTypes[0].isInstance(location)) {
                continue;
            }
            if (!method.getParameterTypes()[1].isAssignableFrom(texture.getClass())) {
                continue;
            }
            try {
                method.invoke(textureManager, location, texture);
                return;
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {}
        }
        throw new IllegalStateException("Unable to register texture for current Minecraft version");
    }

    private static Object resolveDefaultSkinLocation(UUID fallbackUuid) {
        try {
            Method method = DefaultPlayerSkin.class.getMethod("getDefaultSkin", UUID.class);
            return method.invoke(null, fallbackUuid);
        } catch (ReflectiveOperationException ignored) {}

        for (String methodName : new String[] {"getDefaultSkin", "getDefaultTexture"}) {
            try {
                Method method = DefaultPlayerSkin.class.getMethod(methodName);
                return method.invoke(null);
            } catch (ReflectiveOperationException ignored) {}
        }

        throw new IllegalStateException("Unable to resolve default skin location");
    }

    private static String resolveDefaultSkinModel(UUID fallbackUuid) {
        try {
            Method method = DefaultPlayerSkin.class.getMethod("getSkinModelName", UUID.class);
            Object value = method.invoke(null, fallbackUuid);
            if (value instanceof String str) {
                return str;
            }
        } catch (ReflectiveOperationException ignored) {}
        return ResolvedSkin.MODEL_DEFAULT;
    }

    private record SkinLookupResult(String skinUrl, String modelName, String capeUrl, String elytraUrl) {}
}
