package org.voxelhorizons.pack;

import org.junit.Test;

import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.Assert.*;

public class ResourcePackHostTest {
    @Test
    public void servesPublishedPackAndReportsSha1() throws Exception {
        Path root = Files.createTempDirectory("voxelcore-pack-host");
        Path pack = root.resolve("built.zip");
        byte[] contents = new byte[] { 80, 75, 3, 4, 1, 2, 3 };
        Files.write(pack, contents);
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }

        ResourcePackHost host = new ResourcePackHost(root);
        try {
            host.publish(pack);
            host.start("127.0.0.1", port);
            byte[] downloaded;
            try (java.io.InputStream input = new URL("http://127.0.0.1:" + port
                    + ResourcePackHost.RESOURCE_PATH).openStream()) {
                downloaded = readAll(input);
            }
            assertArrayEquals(contents, downloaded);
            assertArrayEquals(MessageDigest.getInstance("SHA-1").digest(contents), host.sha1());
        } finally {
            host.stop();
            Files.deleteIfExists(host.hostedPack());
            Files.deleteIfExists(host.hostedPack().getParent());
            Files.deleteIfExists(pack);
            Files.deleteIfExists(root);
        }
    }

    @Test
    public void servesOnlyThePackPathAndSupportsHead() throws Exception {
        Path root = Files.createTempDirectory("voxelcore-pack-host");
        Path pack = root.resolve("built.zip");
        Files.write(pack, new byte[] { 1, 2, 3 });
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        ResourcePackHost host = new ResourcePackHost(root);
        try {
            host.publish(pack);
            host.start("127.0.0.1", port);
            java.net.HttpURLConnection head = (java.net.HttpURLConnection) new URL("http://127.0.0.1:" + port
                    + ResourcePackHost.RESOURCE_PATH).openConnection();
            head.setRequestMethod("HEAD");
            assertEquals(200, head.getResponseCode());
            assertEquals("3", head.getHeaderField("Content-Length"));
            head.disconnect();
            java.net.HttpURLConnection missing = (java.net.HttpURLConnection) new URL("http://127.0.0.1:" + port
                    + "/other.zip").openConnection();
            assertEquals(404, missing.getResponseCode());
            missing.disconnect();
        } finally {
            host.stop();
            Files.deleteIfExists(host.hostedPack());
            Files.deleteIfExists(host.hostedPack().getParent());
            Files.deleteIfExists(pack);
            Files.deleteIfExists(root);
        }
    }

    private static byte[] readAll(java.io.InputStream input) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }
}
