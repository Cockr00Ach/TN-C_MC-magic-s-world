package com.tnc.tnc.magic;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FireSpellMigrationTest {
    private ListTag list(String... ids) {
        var result=new ListTag();for(var id:ids)result.add(StringTag.valueOf(id));return result;
    }
    private ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("tnc",path);}
    @Test void oldFireTiersRetainOwnershipHistoryAndManualSlot() {
        var tag=new CompoundTag();tag.put("Learned",list("tnc:giant_fireball","tnc:self_destruct","tnc:meteor_fireball"));
        tag.put("LearningHistory",list("tnc:giant_fireball","tnc:self_destruct","tnc:meteor_fireball"));
        var slots=list("","","","","","","","","tnc:meteor_fireball");tag.put("Loadout",slots);
        tag.putInt("LoadoutPage",1);tag.putInt("PointsSpent",17);tag.putInt("Mana",123);tag.putInt("MaxMana",500);
        var data=new MagicStoneData();data.deserializeNBT(tag);
        for(var name:new String[]{"lava_fireball","molten_skyfall","meteor_fall"}) {
            assertTrue(data.hasLearned(id(name)));assertTrue(data.hasPreviouslyLearned(id(name)));
        }
        assertEquals(id("meteor_fall"),data.getSlot(8));assertEquals(1,data.getLoadoutPage());
        assertEquals(17,data.serializeNBT().getInt("PointsSpent"));assertEquals(123,data.serializeNBT().getInt("Mana"));
        var copy=new MagicStoneData();copy.deserializeNBT(data.serializeNBT());assertEquals(data.serializeNBT(),copy.serializeNBT());
    }
    @Test void aForgottenOldTierDoesNotBecomeLearnedAndKeepsFreeRelearningHistory() {
        var tag=new CompoundTag();tag.put("LearningHistory",list("tnc:self_destruct"));tag.put("ExplicitlyForgotten",list("tnc:self_destruct"));
        var data=new MagicStoneData();data.deserializeNBT(tag);
        assertFalse(data.hasLearned(id("molten_skyfall")));assertTrue(data.isExplicitlyForgotten(id("molten_skyfall")));
        assertTrue(data.hasPreviouslyLearned(id("molten_skyfall")));
    }
    @Test void similarlyNamedForeignSpellIdsArePreserved() {
        var tag=new CompoundTag();tag.put("Learned",list("other:meteor_fireball"));
        var data=new MagicStoneData();data.deserializeNBT(tag);assertTrue(data.hasLearned(ResourceLocation.parse("other:meteor_fireball")));
    }
}
