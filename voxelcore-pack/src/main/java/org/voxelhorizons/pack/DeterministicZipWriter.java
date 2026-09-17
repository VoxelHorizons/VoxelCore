package org.voxelhorizons.pack;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes stable ZIPs and chooses the smaller of stored and maximum-DEFLATE data per entry. */
final class DeterministicZipWriter {
    private DeterministicZipWriter() {}

    static void write(Path output, TreeMap<String, byte[]> entries) {
        try {
            Path parent = output.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (OutputStream raw = Files.newOutputStream(output, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
                 ZipOutputStream zip = new ZipOutputStream(raw)) {
                zip.setLevel(Deflater.BEST_COMPRESSION);
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    byte[] bytes = PackAssetOptimizer.optimize(entry.getKey(), entry.getValue());
                    writeEntry(zip, entry.getKey(), bytes);
                }
            }
        } catch (IOException exception) {
            throw new JavaPackCompileException("Unable to write generated resource pack " + output, exception);
        }
    }

    private static void writeEntry(ZipOutputStream zip, String path, byte[] bytes) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        int compressedSize = compressedSize(bytes);
        ZipEntry entry = new ZipEntry(path);
        entry.setTime(0L);
        entry.setSize(bytes.length);
        entry.setCrc(crc.getValue());
        if (compressedSize < bytes.length) {
            entry.setMethod(ZipEntry.DEFLATED);
            entry.setCompressedSize(compressedSize);
        } else {
            entry.setMethod(ZipEntry.STORED);
            entry.setCompressedSize(bytes.length);
        }
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static int compressedSize(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setInput(input);
            deflater.finish();
            byte[] buffer = new byte[8192];
            int size = 0;
            while (!deflater.finished()) size += deflater.deflate(buffer);
            return size;
        } finally {
            deflater.end();
        }
    }
}
