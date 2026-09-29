package com.tnc.tnc.adventure;
import net.minecraft.world.item.*;
import java.util.*;
/** Starter equipment is made by the blacksmith, separately from the wand maker. */
public final class EquipmentOrders {
    public record Recipe(String id,Item item,int ore,int fee){public List<ContractCatalog.Material> materials(){return List.of(new ContractCatalog.Material("minecraft:raw_iron",false,ore),new ContractCatalog.Material("minecraft:coal",false,1));}}
    public static final List<Recipe> ALL=List.of(new Recipe("equipment_helmet",Items.IRON_HELMET,5,20),new Recipe("equipment_chestplate",Items.IRON_CHESTPLATE,8,30),new Recipe("equipment_leggings",Items.IRON_LEGGINGS,7,25),new Recipe("equipment_boots",Items.IRON_BOOTS,4,15),new Recipe("equipment_sword",Items.IRON_SWORD,2,10));
    public static Recipe find(String id){return ALL.stream().filter(r->r.id.equals(id)).findFirst().orElse(null);}
}
