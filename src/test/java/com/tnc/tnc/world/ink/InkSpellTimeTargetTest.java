package com.tnc.tnc.world.ink;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

class InkSpellTimeTargetTest {
    @Test void injectionSignaturesMatchTheInstalledSpellEngineVersion()throws Exception{
        try(var jar=new JarFile("libs/spell_engine-0.15.12+1.20.1_mapped_srg_1.20.1.jar")){
            ClassNode node=new ClassNode();try(var in=jar.getInputStream(jar.getJarEntry("net/spell_engine/internals/SpellHelper.class"))){new ClassReader(in).accept(node,ClassReader.SKIP_CODE);}
            assertTrue(node.methods.stream().anyMatch(m->m.name.equals("getCastTimeDetails")&&m.desc.equals("(Lnet/minecraft/world/entity/LivingEntity;Lnet/spell_engine/api/spell/Spell;)Lnet/spell_engine/internals/casting/SpellCast$Duration;")));
            assertTrue(node.methods.stream().anyMatch(m->m.name.equals("getCastDuration")&&m.desc.equals("(Lnet/minecraft/world/entity/LivingEntity;Lnet/spell_engine/api/spell/Spell;Lnet/minecraft/world/item/ItemStack;)F")));
            assertTrue(node.methods.stream().anyMatch(m->m.name.equals("startCasting")&&m.desc.equals("(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;FI)V")));
            assertTrue(node.methods.stream().anyMatch(m->m.name.equals("performSpell")&&m.desc.equals("(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;Ljava/util/List;Lnet/spell_engine/internals/casting/SpellCast$Action;F)V")));
        }
    }
}
