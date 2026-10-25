package org.voxelhorizons.pack;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.Executors;

/** Small, read-only HTTP endpoint for the currently published Java resource pack. */
public final class ResourcePackHost {
    public static final String RESOURCE_PATH = "/resourcepack.zip";
    private final Path hostedPack;
    private final Logger logger;
    private HttpServer server;
    private byte[] sha1;
    private String bindAddress;
    private int port;
    private volatile boolean requestDebug;
    private Path bedrockRemote;
    private byte[] bedrockSecret;
    private Path projectionExport;

    public ResourcePackHost(Path dataRoot) {
        this(dataRoot, Logger.getLogger("VoxelCore"));
    }

    public ResourcePackHost(Path dataRoot, Logger logger) {
        if (dataRoot == null || logger == null) throw new IllegalArgumentException("Host arguments cannot be null");
        this.hostedPack = dataRoot.resolve("resource-pack-host").resolve("resourcepack.zip");
        this.logger = logger;
    }

    public synchronized void start(String bindAddress, int port) throws IOException {
        if (bindAddress == null || bindAddress.trim().isEmpty()) throw new IllegalArgumentException("Bind address cannot be blank");
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Port must be from 0 through 65535");
        if (server != null && bindAddress.equals(this.bindAddress) && port == this.port) return;
        stop();
        Files.createDirectories(hostedPack.getParent());
        HttpServer candidate = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        candidate.createContext(RESOURCE_PATH, this::servePack);
        candidate.createContext("/bedrock/", this::serveBedrockRemote);
        candidate.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "VoxelCore-pack-http");
            thread.setDaemon(true);
            return thread;
        }));
        candidate.start();
        server = candidate;
        this.bindAddress = bindAddress;
        this.port = port;
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            bindAddress = null;
            port = 0;
        }
    }

    public synchronized boolean isRunning() { return server != null; }
    public synchronized String bindAddress() { return bindAddress; }
    public synchronized int port() { return server == null ? -1 : server.getAddress().getPort(); }
    public synchronized long hostedPackSize() throws IOException {
        return Files.isRegularFile(hostedPack) ? Files.size(hostedPack) : -1L;
    }
    public void setRequestDebug(boolean enabled) { requestDebug = enabled; }

    /** Atomically swaps the public file only after the new build is complete. */
    public synchronized void publish(Path completedPack) throws IOException {
        if (completedPack == null || !Files.isRegularFile(completedPack)) {
            throw new IOException("Resource pack does not exist: " + completedPack);
        }
        Files.createDirectories(hostedPack.getParent());
        Path staged = hostedPack.resolveSibling("resourcepack.zip.tmp");
        Files.copy(completedPack, staged, StandardCopyOption.REPLACE_EXISTING);
        byte[] nextHash;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (java.io.InputStream input = Files.newInputStream(staged)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            nextHash = digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-1 is not available", impossible);
        }
        try {
            Files.move(staged, hostedPack, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(staged, hostedPack, StandardCopyOption.REPLACE_EXISTING);
        }
        sha1 = nextHash;
    }

    /** Loads the last atomically published pack after a server restart. */
    public synchronized boolean loadExisting() throws IOException {
        if (!Files.isRegularFile(hostedPack)) return false;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (java.io.InputStream input = Files.newInputStream(hostedPack)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            sha1 = digest.digest();
            return true;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-1 is not available", impossible);
        }
    }

    public synchronized byte[] sha1() { return sha1 == null ? null : sha1.clone(); }
    public synchronized String sha1Hex() {
        if (sha1 == null) return "unavailable";
        StringBuilder hex = new StringBuilder(sha1.length * 2);
        for (byte value : sha1) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return hex.toString();
    }
    public Path hostedPack() { return hostedPack; }

    /** Serve a signed Bedrock remote bundle (B17) to pullers holding the derived bearer token. */
    public synchronized void setBedrockRemote(Path exportDirectory, byte[] secret) throws IOException {
        org.voxelhorizons.pack.RemoteBedrockBundle.requireSecret(secret);
        this.bedrockRemote = exportDirectory;
        this.bedrockSecret = secret.clone();
    }

    /**
     * Serve live furniture/vehicle projection feeds (B17) to a proxy Geyser. Point the
     * companions' bedrock.projection.geyser-data-directory at this folder; files are read
     * from its voxelcore-projection/ subfolder and signed per response.
     */
    public synchronized void setProjectionExport(Path geyserStyleFolder) {
        this.projectionExport = geyserStyleFolder == null ? null : geyserStyleFolder.resolve("voxelcore-projection");
    }

    private boolean serveProjection(HttpExchange exchange, String name, byte[] secret) throws IOException {
        if (!name.startsWith("projection/")) return false;
        String file = name.substring("projection/".length());
        String kind = file.endsWith(".json") ? file.substring(0, file.length() - 5) : "";
        Path folder;
        synchronized (this) { folder = projectionExport; }
        Path feed = folder == null ? null : folder.resolve(kind + ".json");
        if (!org.voxelhorizons.pack.RemoteBedrockBundle.PROJECTION_KINDS.contains(kind) || feed == null
                || Files.isSymbolicLink(feed) || !Files.isRegularFile(feed)) {
            exchange.sendResponseHeaders(404, -1);
            return true;
        }
        long issuedAt = Files.getLastModifiedTime(feed).toMillis();
        byte[] body = Files.readAllBytes(feed);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-VoxelCore-Issued-At", Long.toString(issuedAt));
        exchange.getResponseHeaders().set("X-VoxelCore-Signature",
                org.voxelhorizons.pack.RemoteBedrockBundle.projectionSignature(secret, kind, issuedAt, body));
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(body); }
        return true;
    }

    private void serveBedrockRemote(HttpExchange exchange) throws IOException {
        int status = 404;
        try {
            Path directory;
            byte[] secret;
            synchronized (this) { directory = bedrockRemote; secret = bedrockSecret; }
            String path = exchange.getRequestURI().getPath();
            String name = path.startsWith("/bedrock/") ? path.substring("/bedrock/".length()) : "";
            if ("GET".equals(exchange.getRequestMethod()) && secret != null && name.startsWith("projection/")) {
                if (!org.voxelhorizons.pack.RemoteBedrockBundle.authorized(
                        exchange.getRequestHeaders().getFirst("Authorization"), secret)) {
                    status = 403;
                    exchange.sendResponseHeaders(403, -1);
                    return;
                }
                serveProjection(exchange, name, secret);
                status = 200;
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod()) || directory == null
                    || !org.voxelhorizons.pack.RemoteBedrockBundle.FILES.contains(name)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            if (!org.voxelhorizons.pack.RemoteBedrockBundle.authorized(
                    exchange.getRequestHeaders().getFirst("Authorization"), secret)) {
                status = 403;
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            Path file = directory.resolve(name);
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] body = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", name.endsWith(".json") ? "application/json" : "application/zip");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, body.length);
            status = 200;
            try (OutputStream output = exchange.getResponseBody()) { output.write(body); }
        } finally {
            if (requestDebug) logger.info("Bedrock remote HTTP request: path=" + exchange.getRequestURI().getPath()
                    + " remote=" + exchange.getRemoteAddress() + " status=" + status);
            exchange.close();
        }
    }

    private void servePack(HttpExchange exchange) throws IOException {
        int status = 500;
        long bytesSent = 0L;
        try {
            if (!RESOURCE_PATH.equals(exchange.getRequestURI().getPath())
                    || !("GET".equals(exchange.getRequestMethod()) || "HEAD".equals(exchange.getRequestMethod()))) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                exchange.sendResponseHeaders(404, -1);
                status = 404;
                return;
            }
            Path file;
            synchronized (this) { file = Files.isRegularFile(hostedPack) ? hostedPack : null; }
            if (file == null) {
                exchange.sendResponseHeaders(404, -1);
                status = 404;
                return;
            }
            long length = Files.size(file);
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/zip");
            headers.set("Content-Disposition", "attachment; filename=resourcepack.zip");
            headers.set("Cache-Control", "no-store");
            headers.set("X-Content-Type-Options", "nosniff");
            if ("HEAD".equals(exchange.getRequestMethod())) {
                headers.set("Content-Length", Long.toString(length));
                exchange.sendResponseHeaders(200, -1);
                status = 200;
                bytesSent = length;
                return;
            }
            exchange.sendResponseHeaders(200, length);
            status = 200;
            try (OutputStream output = exchange.getResponseBody(); java.io.InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    bytesSent += read;
                }
            }
        } catch (IOException failure) {
            logger.log(Level.WARNING, "Resource-pack HTTP request failed: method=" + exchange.getRequestMethod()
                    + " path=" + exchange.getRequestURI().getPath() + " remote="
                    + exchange.getRemoteAddress() + " status=" + status + " bytes=" + bytesSent, failure);
            throw failure;
        } finally {
            if (requestDebug) logger.info("Resource-pack HTTP request: method=" + exchange.getRequestMethod()
                    + " path=" + exchange.getRequestURI().getPath() + " remote=" + exchange.getRemoteAddress()
                    + " status=" + status + " bytes=" + bytesSent);
            exchange.close();
        }
    }
}
