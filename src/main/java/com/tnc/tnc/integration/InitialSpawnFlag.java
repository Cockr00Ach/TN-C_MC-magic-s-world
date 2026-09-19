package com.tnc.tnc.integration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Writes the ysjx_dimension first-login switch without exposing Java I/O to Rhino. */
public final class InitialSpawnFlag {
    private static final String FILE_NAME = ".ysjx_magic_association_initial_spawn";

    private InitialSpawnFlag() {
    }

    public static String disable(Object worldRoot) throws IOException {
        Path flag = Path.of(worldRoot.toString()).resolve(FILE_NAME);
        Files.writeString(
                flag,
                "false\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
        return flag.toString();
    }
}
