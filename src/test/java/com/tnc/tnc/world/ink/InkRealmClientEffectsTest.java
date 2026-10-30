package com.tnc.tnc.world.ink;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class InkRealmClientEffectsTest {
    @Test void vanillaAndForgeCloudPathsAreDisabled() throws Exception {
        // Inspect client bytecode without bootstrapping Minecraft registries in a plain JVM.
        var node = new ClassNode();
        try (var input = getClass().getClassLoader().getResourceAsStream(
                "com/tnc/tnc/world/ink/InkRealmClientEffects$InkEffects.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        var constructor = node.methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
        boolean disabledHeight = false;
        for (var instruction : constructor.instructions) {
            if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Float height && Float.isNaN(height)) disabledHeight = true;
        }
        assertTrue(disabledHeight, "Dimension must use the vanilla no-cloud height sentinel");
        var cloudHook = node.methods.stream().filter(m -> m.name.equals("renderClouds")).findFirst().orElseThrow();
        boolean consumesClouds = false;
        for (var instruction : cloudHook.instructions) {
            if (instruction.getOpcode() == Opcodes.IRETURN && instruction.getPrevious().getOpcode() == Opcodes.ICONST_1) consumesClouds = true;
        }
        assertTrue(consumesClouds, "Forge cloud hook must consume rendering without drawing");
    }

    @Test void fogPlanesAreFiniteAndBeyondAllNormalViewDistances() {
        assertTrue(Float.isFinite(InkRealmClientEffects.CLEAR_FOG_START));
        assertTrue(Float.isFinite(InkRealmClientEffects.CLEAR_FOG_END));
        assertTrue(InkRealmClientEffects.CLEAR_FOG_START > 32 * 16);
        assertTrue(InkRealmClientEffects.CLEAR_FOG_END > InkRealmClientEffects.CLEAR_FOG_START);
    }

    @Test void clearingOnlyAppliesToInkDimension() {
        assertTrue(InkRealmClientEffects.clearsAtmosphere(id("tnc", "ink_realm")));
        assertFalse(InkRealmClientEffects.clearsAtmosphere(id("minecraft", "overworld")));
        assertFalse(InkRealmClientEffects.clearsAtmosphere(id("minecraft", "the_nether")));
        assertFalse(InkRealmClientEffects.clearsAtmosphere(id("tnc", "water_arena")));
        assertFalse(InkRealmClientEffects.clearsAtmosphere(null));
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
