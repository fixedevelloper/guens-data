package com.guens.fieldcollect.person;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Repli sur disque (développement, tests) quand MinIO n'est pas configuré. */
@Component
@ConditionalOnExpression("'${app.storage.minio.access-key:}'.isEmpty()")
public class FileObjectStorage implements ObjectStorage {

    private final Path root;

    public FileObjectStorage(@Value("${app.storage.documents-dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("Chemin invalide");
        return p;
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
