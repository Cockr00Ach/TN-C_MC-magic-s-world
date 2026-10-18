package com.tnc.tnc.client;

import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelEvent;
import java.util.HashSet;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class TNDirectSpellModelsTest {
    @Test void registerAdditionalUsesExactPlainKeysRequiredByModernFixAndSpellEngine() {
        var keys=new HashSet<ResourceLocation>();
        TNDirectSpellModels.register(new ModelEvent.RegisterAdditional(keys));
        assertEquals(13,keys.size());
        for(var key:keys) {
            assertFalse(key instanceof ModelResourceLocation,"Variant keys incorrectly load blockstates");
            assertTrue(Files.exists(Path.of("src/main/resources/assets/tnc/models/"+key.getPath()+".json")),key.toString());
        }
        assertTrue(keys.contains(ResourceLocation.fromNamespaceAndPath("tnc","projectile/wind_tornado")));
        assertTrue(keys.contains(ResourceLocation.fromNamespaceAndPath("tnc","projectile/earth_pillar")));
    }
}
