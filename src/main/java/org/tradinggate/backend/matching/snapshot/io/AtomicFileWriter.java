package org.tradinggate.backend.matching.snapshot.io;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

public class AtomicFileWriter {

    public void writeAtomic(Path targetFile, byte[] data) throws IOException {
        Path dir = targetFile.getParent();
        if (dir == null) throw new IOException("Target file has no parent dir: " + targetFile);

        Files.createDirectories(dir);

        String tmpName = targetFile.getFileName() + ".tmp-" + System.nanoTime();
        Path tmpFile = dir.resolve(tmpName);

        try {
            Files.write(tmpFile, data, CREATE_NEW, WRITE);
            moveAtomic(tmpFile, targetFile);
        } finally {
            tryDeleteIfExists(tmpFile);
        }
    }

    private void moveAtomic(Path tmpFile, Path targetFile) throws IOException {
        try {
            Files.move(tmpFile, targetFile, ATOMIC_MOVE, REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmpFile, targetFile, REPLACE_EXISTING);
        }
    }

    private void tryDeleteIfExists(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }
}
