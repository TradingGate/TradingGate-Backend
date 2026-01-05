package org.tradinggate.backend.matching.snapshot.io;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * - 파일을 가능한 한 원자적으로 기록
 *
 * [정책]
 * - tmp 파일에 먼저 쓰고 rename/move로 교체
 * - 파일시스템이 ATOMIC_MOVE를 지원하면 atomic, 아니면 best-effort로 degrade 한다.
 *   (부분 파일 노출 방지가 목적)
 */
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
