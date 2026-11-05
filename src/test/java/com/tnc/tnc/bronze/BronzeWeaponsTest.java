package com.tnc.tnc.bronze;

import com.google.gson.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BronzeWeaponsTest {
    @Test void weaponsBelongToExistingWarriorFamiliesWithoutASecondCombatController() throws Exception {
        Path root=Path.of("src/main/resources/data/tnc/tags/items");
        for(var pair:java.util.Map.of("greatsword","greatswords","spear","spears","dao","longswords","dagger","longswords").entrySet()){
            var tag=JsonParser.parseString(Files.readString(root.resolve(pair.getValue()+".json"))).getAsJsonObject();
            assertTrue(tag.getAsJsonArray("values").asList().stream().anyMatch(v->v.getAsString().equals("tnc:bronze_"+pair.getKey())));
        }
        assertFalse(Files.exists(Path.of("src/main/java/com/tnc/tnc/bronze/BronzeCombat.java")));
        assertFalse(Files.exists(Path.of("src/main/java/com/tnc/tnc/bronze/BronzeNetwork.java")));
    }
    @Test void upgradeCopiesPreserveDamageNameEnchantmentsAndForeignData(){
        var original=new net.minecraft.nbt.CompoundTag();original.putInt("Damage",217);
        var display=new net.minecraft.nbt.CompoundTag();display.putString("Name","{\"text\":\"My bronze\"}");original.put("display",display);
        var enchants=new net.minecraft.nbt.ListTag();var enchant=new net.minecraft.nbt.CompoundTag();enchant.putString("id","minecraft:sharpness");enchant.putShort("lvl",(short)3);enchants.add(enchant);original.put("Enchantments",enchants);
        original.putString("OtherModData","keep");var result=original;
        for(int n=1;n<=10;n++){result=BronzeUpgradeRecipe.upgradedTag(result);assertEquals(n,result.getInt(BronzeWeaponItem.UPGRADE));}
        assertFalse(original.contains(BronzeWeaponItem.UPGRADE));assertEquals(217,result.getInt("Damage"));
        assertEquals(display,result.getCompound("display"));assertEquals(enchants,result.getList("Enchantments",10));assertEquals("keep",result.getString("OtherModData"));
        var full=result;assertThrows(IllegalArgumentException.class,()->BronzeUpgradeRecipe.upgradedTag(full));
    }
    @Test void tenUpgradesDoubleEveryWeaponWithoutCompoundingOrRounding(){
        for(var type:BronzeProfile.values()){
            for(int n=0;n<=10;n++)assertEquals(type.damage*(1+n*.1),type.upgraded(n),1e-7);
            assertEquals(type.damage*2,type.upgraded(11),1e-7);
            assertEquals(type.damage,type.upgraded(-1),1e-7);
        }
        assertEquals(4.4,BronzeProfile.DAGGER.upgraded(1),1e-7);
    }
    @Test void throwClampsAtThirtyGameTicks(){
        assertEquals(1,BronzeProfile.throwMultiplier(0));assertEquals(1.5,BronzeProfile.throwMultiplier(15));
        assertEquals(2,BronzeProfile.throwMultiplier(30));assertEquals(2,BronzeProfile.throwMultiplier(72000));
    }
    @Test void shippedModelsUseLegalCuboidsAndEveryRecipeHasTheSpecifiedMaterials() throws Exception {
        Path root=Path.of("src/main/resources");
        for(var type:BronzeProfile.values()){
            var model=JsonParser.parseString(Files.readString(root.resolve("assets/tnc/models/item/"+type.id()+".json"))).getAsJsonObject();
            assertTrue(model.getAsJsonArray("elements").size()>20);
            for(var raw:model.getAsJsonArray("elements")){
                var element=raw.getAsJsonObject();
                for(int axis=0;axis<3;axis++){
                    double lo=element.getAsJsonArray("from").get(axis).getAsDouble(),hi=element.getAsJsonArray("to").get(axis).getAsDouble();
                    assertTrue(lo>=-16&&hi<=32&&lo<hi,type+" illegal model extent");
                }
                for(var face:element.getAsJsonObject("faces").entrySet())for(var uv:face.getValue().getAsJsonObject().getAsJsonArray("uv"))assertTrue(uv.getAsDouble()>=0&&uv.getAsDouble()<=16);
            }
            var recipe=JsonParser.parseString(Files.readString(root.resolve("data/tnc/recipes/"+type.id()+".json"))).getAsJsonObject();
            assertEquals("tnc:"+type.id(),recipe.getAsJsonObject("result").get("item").getAsString());
            assertEquals(3,recipe.getAsJsonArray("pattern").size());
            assertEquals("minecraft:copper_ingot",recipe.getAsJsonObject("key").getAsJsonObject("C").get("item").getAsString());
        }
    }
}
