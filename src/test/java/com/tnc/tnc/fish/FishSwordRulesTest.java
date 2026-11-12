package com.tnc.tnc.fish;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.*;
import java.nio.file.*;
import com.google.gson.*;

class FishSwordRulesTest {
    @Test void chancesHaveExactCountsAndFailureBoundaries() {
        for (int chance : new int[]{FishSwordRules.INGOT_CHANCE,FishSwordRules.BLOCK_CHANCE}) {
            int successes = 0;
            for (int roll=0;roll<10000;roll++) if (FishSwordRules.succeeds(roll,chance)) successes++;
            assertEquals(chance,successes);
            assertTrue(FishSwordRules.succeeds(chance-1,chance));
            assertFalse(FishSwordRules.succeeds(chance,chance));
        }
        assertFalse(FishSwordRules.succeeds(-1,500));
        assertEquals(200,FishSwordRules.dropChance(0));
        assertEquals(350,FishSwordRules.dropChance(3));
        assertEquals(200,FishSwordRules.dropChance(-3));
    }
    @Test void metalizationPreservesOriginalAndForeignDataWhileConvertingDurability() {
        CompoundTag original=new CompoundTag();original.putString("id","tnc:living_fish_sword");original.putByte("Count",(byte)1);
        CompoundTag tag=new CompoundTag();tag.putInt("Damage",125);tag.putString("OtherModData","keep");
        CompoundTag display=new CompoundTag();display.putString("Name","{\"text\":\"Fishy\"}");tag.put("display",display);
        ListTag enchants=new ListTag();CompoundTag enchant=new CompoundTag();enchant.putString("id","minecraft:sharpness");enchant.putShort("lvl",(short)3);enchants.add(enchant);tag.put("Enchantments",enchants);
        tag.put(FishSwordEvents.PENDING,new CompoundTag());original.put("tag",tag);
        CompoundTag caps=new CompoundTag();caps.putInt("foreign:level",7);original.put("ForgeCaps",caps);
        CompoundTag result=FishSwordData.metalized(original,250,750);
        assertEquals("tnc:metal_fish_sword",result.getString("id"));
        assertEquals(375,result.getCompound("tag").getInt("Damage"));
        assertEquals(display,result.getCompound("tag").getCompound("display"));
        assertEquals(enchants,result.getCompound("tag").getList("Enchantments",10));
        assertEquals(caps,result.getCompound("ForgeCaps"));
        assertEquals("keep",result.getCompound("tag").getString("OtherModData"));
        assertFalse(result.getCompound("tag").contains(FishSwordEvents.PENDING));
        assertTrue(original.getCompound("tag").contains(FishSwordEvents.PENDING));
        assertEquals("tnc:living_fish_sword",original.getString("id"));
        assertEquals(125,original.getCompound("tag").getInt("Damage"));
    }
    @Test void wearNeverBecomesAFullRepairOrABrokenOutput() {
        assertEquals(0,FishSwordRules.convertedDamage(0,250,750));
        assertEquals(747,FishSwordRules.convertedDamage(249,250,750));
        assertEquals(749,FishSwordRules.convertedDamage(250,250,750));
        assertThrows(IllegalArgumentException.class,()->FishSwordRules.convertedDamage(0,0,750));
        assertTrue(FishSwordRules.LIVING_SPEED>FishSwordRules.METAL_SPEED);
        assertTrue(FishSwordRules.METAL_DAMAGE>FishSwordRules.LIVING_DAMAGE);
    }
    @Test void shippedModelsHaveCompleteTexturesAndBothHandsConfigured() throws Exception {
        Path assets=Path.of("src/main/resources/assets/tnc");
        for(String id:new String[]{"living_fish_sword","metal_fish_sword"}) {
            JsonObject model=JsonParser.parseString(Files.readString(assets.resolve("models/item/"+id+".json"))).getAsJsonObject();
            assertEquals("tnc:item/"+id,model.getAsJsonObject("textures").get("0").getAsString());
            assertTrue(Files.size(assets.resolve("textures/item/"+id+".png"))>100);
            for(String slot:new String[]{"firstperson_righthand","firstperson_lefthand","thirdperson_righthand","thirdperson_lefthand","gui","ground","fixed"}) assertTrue(model.getAsJsonObject("display").has(slot));
            for(JsonElement raw:model.getAsJsonArray("elements")) {
                JsonObject element=raw.getAsJsonObject();
                for(int axis=0;axis<3;axis++) assertTrue(element.getAsJsonArray("from").get(axis).getAsDouble()>=-16 && element.getAsJsonArray("to").get(axis).getAsDouble()<=32);
            }
        }
    }
}
