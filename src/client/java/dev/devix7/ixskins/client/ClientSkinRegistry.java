package dev.devix7.ixskins.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.devix7.ixskins.IxSkins;
import dev.devix7.ixskins.SkinSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ClientSkinRegistry {
    private static final int MAX_SKIN_BYTES = 1024 * 1024;

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "IX Skins Downloader");
        thread.setDaemon(true);
        return thread;
    });

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private static final Map<UUID, SkinSource> OVERRIDES = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<LoadedSkin>> LOADING = new ConcurrentHashMap<>();
    private static final Map<String, LoadedSkin> READY = new ConcurrentHashMap<>();

    private ClientSkinRegistry() {
    }

    public static void applySnapshot(Map<UUID, SkinSource> snapshot) {
        OVERRIDES.clear();
        OVERRIDES.putAll(snapshot);
    }

    public static void clearAll() {
        OVERRIDES.clear();
        LOADING.clear();
        READY.clear();
    }

    public static SkinTextures getOverride(UUID uuid, SkinTextures vanilla) {
        if (uuid == null || vanilla == null) {
            return null;
        }

        SkinSource source = OVERRIDES.get(uuid);
        if (source == null) {
            return null;
        }

        String key = source.cacheKey();
        LoadedSkin loaded = READY.get(key);
        if (loaded != null) {
            SkinTextures.Model model = loaded.model() == null ? vanilla.model() : loaded.model();
            return new SkinTextures(loaded.texture(), loaded.textureUrl(), vanilla.capeTexture(), vanilla.elytraTexture(), model, false);
        }

        LOADING.computeIfAbsent(key, ignored -> startLoading(key, source));
        return null;
    }

    private static CompletableFuture<LoadedSkin> startLoading(String key, SkinSource source) {
        CompletableFuture<LoadedSkin> future = CompletableFuture
            .supplyAsync(() -> download(source), EXECUTOR)
            .thenCompose(downloaded -> registerTexture(key, downloaded));

        future.whenComplete((loaded, throwable) -> {
            if (throwable != null) {
                IxSkins.LOGGER.warn("Failed to load IX skin {}", source.display(), throwable);
                LOADING.remove(key);
            } else {
                READY.put(key, loaded);
            }
        });

        return future;
    }

    private static DownloadedSkin download(SkinSource source) {
        try {
            if (source.kind() == SkinSource.Kind.PLAYER) {
                return downloadPlayerSkin(source.value());
            }
            return downloadRawSkin(source.value(), null);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static DownloadedSkin downloadPlayerSkin(String username) throws IOException, InterruptedException {
        String encodedName = URLEncoder.encode(username.trim(), StandardCharsets.UTF_8);
        JsonObject uuidResponse = getJson("https://api.mojang.com/users/profiles/minecraft/" + encodedName);

        if (!uuidResponse.has("id")) {
            throw new IOException("Mojang profile not found for " + username);
        }

        String rawUuid = uuidResponse.get("id").getAsString();
        JsonObject profile = getJson("https://sessionserver.mojang.com/session/minecraft/profile/" + rawUuid + "?unsigned=false");
        JsonArray properties = profile.getAsJsonArray("properties");

        if (properties == null) {
            throw new IOException("No Mojang texture properties for " + username);
        }

        for (JsonElement element : properties) {
            JsonObject property = element.getAsJsonObject();
            if (!"textures".equals(property.get("name").getAsString())) {
                continue;
            }

            String encodedTextures = property.get("value").getAsString();
            String decoded = new String(Base64.getDecoder().decode(encodedTextures), StandardCharsets.UTF_8);
            JsonObject texturesRoot = JsonParser.parseString(decoded).getAsJsonObject();
            JsonObject textures = texturesRoot.getAsJsonObject("textures");

            if (textures == null || !textures.has("SKIN")) {
                throw new IOException("Mojang profile has no skin texture for " + username);
            }

            JsonObject skin = textures.getAsJsonObject("SKIN");
            String url = skin.get("url").getAsString();
            String model = "wide";

            if (skin.has("metadata")) {
                JsonObject metadata = skin.getAsJsonObject("metadata");
                if (metadata.has("model")) {
                    model = metadata.get("model").getAsString();
                }
            }

            return downloadRawSkin(url, model);
        }

        throw new IOException("No Mojang textures property for " + username);
    }

    private static DownloadedSkin downloadRawSkin(String url, String modelName) throws IOException, InterruptedException {
        byte[] bytes = getBytes(url);
        if (bytes.length > MAX_SKIN_BYTES) {
            throw new IOException("Skin image is too large: " + bytes.length + " bytes");
        }
        return new DownloadedSkin(url, bytes, modelFromName(modelName));
    }

    private static CompletableFuture<LoadedSkin> registerTexture(String key, DownloadedSkin downloaded) {
        CompletableFuture<LoadedSkin> future = new CompletableFuture<>();
        MinecraftClient client = MinecraftClient.getInstance();

        client.execute(() -> {
            NativeImage image = null;
            try {
                image = NativeImage.read(new ByteArrayInputStream(downloaded.pngBytes()));
                image = normalizeSkinImage(image);

                Identifier identifier = Identifier.of(IxSkins.MOD_ID, "skins/" + sha1(key));
                NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "IX Skins " + key, image);
                image = null;

                client.getTextureManager().registerTexture(identifier, texture);
                future.complete(new LoadedSkin(identifier, downloaded.textureUrl(), downloaded.model()));
            } catch (Throwable throwable) {
                if (image != null) {
                    image.close();
                }
                future.completeExceptionally(throwable);
            }
        });

        return future;
    }

    private static NativeImage normalizeSkinImage(NativeImage image) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();

        if (width != 64 || (height != 64 && height != 32)) {
            throw new IOException("Skin must be 64x64 or 64x32 PNG, got " + width + "x" + height);
        }

        boolean legacy = height == 32;
        if (legacy) {
            image = convertLegacySkin(image);
        }

        stripAlpha(image, 0, 0, 32, 16);
        if (legacy) {
            stripColor(image, 32, 0, 64, 32);
        }
        stripAlpha(image, 0, 16, 64, 32);
        stripAlpha(image, 16, 48, 48, 64);

        return image;
    }

    private static NativeImage convertLegacySkin(NativeImage image) {
        NativeImage converted = new NativeImage(64, 64, true);
        try {
            converted.fillRect(0, 0, 64, 64, 0);

            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 64; x++) {
                    converted.setColorArgb(x, y, image.getColorArgb(x, y));
                }
            }

            converted.copyRect(4, 16, 16, 32, 4, 4, true, false);
            converted.copyRect(8, 16, 16, 32, 4, 4, true, false);
            converted.copyRect(0, 20, 24, 32, 4, 12, true, false);
            converted.copyRect(4, 20, 16, 32, 4, 12, true, false);
            converted.copyRect(8, 20, 8, 32, 4, 12, true, false);
            converted.copyRect(12, 20, 16, 32, 4, 12, true, false);
            converted.copyRect(44, 16, -8, 32, 4, 4, true, false);
            converted.copyRect(48, 16, -8, 32, 4, 4, true, false);
            converted.copyRect(40, 20, 0, 32, 4, 12, true, false);
            converted.copyRect(44, 20, -8, 32, 4, 12, true, false);
            converted.copyRect(48, 20, -16, 32, 4, 12, true, false);
            converted.copyRect(52, 20, -8, 32, 4, 12, true, false);
        } catch (Throwable throwable) {
            converted.close();
            throw throwable;
        } finally {
            image.close();
        }

        return converted;
    }

    private static void stripColor(NativeImage image, int x1, int y1, int x2, int y2) {
        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                int color = image.getColorArgb(x, y);
                if (((color >>> 24) & 0xFF) < 128) {
                    return;
                }
            }
        }

        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                image.setColorArgb(x, y, image.getColorArgb(x, y) & 0x00FFFFFF);
            }
        }
    }

    private static void stripAlpha(NativeImage image, int x1, int y1, int x2, int y2) {
        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                image.setColorArgb(x, y, image.getColorArgb(x, y) | 0xFF000000);
            }
        }
    }

    private static SkinTextures.Model modelFromName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        if ("slim".equalsIgnoreCase(name)) {
            return SkinTextures.Model.SLIM;
        }

        return SkinTextures.Model.WIDE;
    }

    private static JsonObject getJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "IXSkins/1.0")
            .GET()
            .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();

        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " from " + url);
        }

        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static byte[] getBytes(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "IXSkins/1.0")
            .GET()
            .build();

        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();

        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " from " + url);
        }

        return response.body();
    }

    private static String sha1(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record DownloadedSkin(String textureUrl, byte[] pngBytes, SkinTextures.Model model) {
    }

    private record LoadedSkin(Identifier texture, String textureUrl, SkinTextures.Model model) {
    }
}
