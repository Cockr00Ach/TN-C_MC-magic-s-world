package com.tnc.tnc.mixin;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ConquestCropCompatibilityTest {
    @Test
    void guardCoversCropSuperclassAsWellAsOrdinaryBushes() throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream("com/tnc/tnc/mixin/ConquestBushMixin.class")) {
            assertNotNull(in);
            String constants = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertTrue(constants.contains("com.conquestrefabricated.content.blocks.block.plants.AbstractCropsBlock"),
                    "The crashing crop superclass must be guarded, not only Bush");
            assertTrue(constants.contains("com.conquestrefabricated.content.blocks.block.plants.Bush"));
        }
    }
}
