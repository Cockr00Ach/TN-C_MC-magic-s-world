package com.tnc.tnc.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConquestBushMixinIsolationTest {
    @Test
    void mixinDoesNotReferenceOrdinaryClassesInsideItsReservedPackage() throws IOException {
        ClassLoader loader = getClass().getClassLoader();
        assertNull(loader.getResource("com/tnc/tnc/mixin/ConquestBushPropertyGuard.class"));

        try (InputStream stream = loader.getResourceAsStream(
                "com/tnc/tnc/mixin/ConquestBushMixin.class")) {
            assertNotNull(stream);
            String constantPool = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertFalse(constantPool.contains("ConquestBushPropertyGuard"));
        }
    }
}
