package org.voxelhorizons.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Downloads (once) and caches the official Minecraft client jar for a version, verified
 * against the SHA-1 published in Mojang's version manifest, so {@link VanillaAssets} can
 * supply vanilla textures/models referenced by content packs.
 */
public final class VanillaClientJar {
    static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final long MAX_JAR_BYTES = 200_000_000L;

    private VanillaClientJar() {}

    /** Returns the cached jar, downloading it first when absent or corrupt. */
    public static Path resolve(Path cacheDir, String version) throws IOException {
        if (version == null || !version.matches("[A-Za-z0-9._-]{1,64}"))
            throw new IOException("Invalid Minecraft version " + version);
        Path jar = cacheDir.resolve("client-" + version + ".jar");
        Path sha = cacheDir.resolve("client-" + version + ".sha1");
        if (Files.isRegularFile(jar) && Files.isRegularFile(sha)
                && new String(Files.readAllBytes(sha), StandardCharsets.US_ASCII).trim().equals(sha1(Files.readAllBytes(jar))))
            return jar;
        JsonObject manifest = json(MANIFEST);
        String versionUrl = null;
        for (JsonElement entry : manifest.getAsJsonArray("versions")) {
            JsonObject object = entry.getAsJsonObject();
            if (version.equals(object.get("id").getAsString())) versionUrl = object.get("url").getAsString();
        }
        if (versionUrl == null) throw new IOException("Minecraft " + version + " is not in Mojang's version manifest");
        JsonObject client = json(versionUrl).getAsJsonObject("downloads").getAsJsonObject("client");
        String expected = client.get("sha1").getAsString().toLowerCase(Locale.ROOT);
        byte[] bytes = download(client.get("url").getAsString(), MAX_JAR_BYTES);
        if (!expected.equals(sha1(bytes))) throw new IOException("Client jar SHA-1 mismatch for " + version);
        Files.createDirectories(cacheDir);
        Path staging = Files.createTempFile(cacheDir, ".client-", ".tmp");
        try {
            Files.write(staging, bytes);
            Files.move(staging, jar, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staging);
        }
        Files.write(sha, expected.getBytes(StandardCharsets.US_ASCII));
        return jar;
    }

    private static JsonObject json(String url) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("Refusing non-HTTPS URL " + url);
        try (Reader reader = new InputStreamReader(new java.io.ByteArrayInputStream(download(url, 20_000_000L)),
                StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static byte[] download(String url, long limit) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("Refusing non-HTTPS URL " + url);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(60_000);
        connection.setRequestProperty("User-Agent", "VoxelCore");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode() + " for " + url);
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[65536];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    if (out.size() > limit) throw new IOException("Download too large: " + url);
                }
                return out.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    static String sha1(byte[] bytes) throws IOException {
        try {
            StringBuilder out = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-1").digest(bytes)) out.append(String.format(Locale.ROOT, "%02x", b & 255));
            return out.toString();
        } catch (java.security.GeneralSecurityException impossible) {
            throw new IOException("SHA-1 unavailable", impossible);
        }
    }
}
