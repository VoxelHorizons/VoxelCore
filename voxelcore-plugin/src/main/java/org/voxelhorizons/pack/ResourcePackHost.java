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
import java.util.concurrent.Executors;

/** Small, read-only HTTP endpoint for the currently published Java resource pack. */
public final class ResourcePackHost {
    public static final String RESOURCE_PATH = "/resourcepack.zip";
    private final Path hostedPack;
    private HttpServer server;
    private byte[] sha1;
    private String bindAddress;
    private int port;

    public ResourcePackHost(Path dataRoot) {
        this.hostedPack = dataRoot.resolve("resource-pack-host").resolve("resourcepack.zip");
    }

    public synchronized void start(String bindAddress, int port) throws IOException {
        if (server != null && bindAddress.equals(this.bindAddress) && port == this.port) return;
        stop();
        Files.createDirectories(hostedPack.getParent());
        HttpServer candidate = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        candidate.createContext(RESOURCE_PATH, this::servePack);
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
    public Path hostedPack() { return hostedPack; }

    private void servePack(HttpExchange exchange) throws IOException {
        try {
            if (!RESOURCE_PATH.equals(exchange.getRequestURI().getPath())
                    || !("GET".equals(exchange.getRequestMethod()) || "HEAD".equals(exchange.getRequestMethod()))) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            Path file;
            synchronized (this) { file = Files.isRegularFile(hostedPack) ? hostedPack : null; }
            if (file == null) {
                exchange.sendResponseHeaders(404, -1);
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
                return;
            }
            exchange.sendResponseHeaders(200, length);
            try (OutputStream output = exchange.getResponseBody(); java.io.InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            }
        } finally {
            exchange.close();
        }
    }
}
