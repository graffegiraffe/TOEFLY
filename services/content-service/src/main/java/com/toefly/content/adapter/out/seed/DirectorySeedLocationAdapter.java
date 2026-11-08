package com.toefly.content.adapter.out.seed;

import com.toefly.content.application.port.out.SeedFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Reads the seed bundle from a directory ({@code toefly.content.seed.location=dir:/path}). */
public class DirectorySeedLocationAdapter extends BundleSeedLocationAdapter {

    private final Path root;

    public DirectorySeedLocationAdapter(Path root) {
        this.root = root;
    }

    @Override
    protected Optional<byte[]> find(String path) {
        return Files.isRegularFile(root.resolve(path)) ? Optional.of(read(path)) : Optional.empty();
    }

    @Override
    protected byte[] read(String path) {
        try {
            return Files.readAllBytes(root.resolve(path));
        } catch (IOException e) {
            throw new SeedFileException("cannot read " + path, e);
        }
    }
}
