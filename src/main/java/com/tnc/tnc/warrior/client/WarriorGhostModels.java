package com.tnc.tnc.warrior.client;

import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Cheap matching silhouettes for the enlarged translucent attack ghost. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class WarriorGhostModels {
    private static final Set<String> IDS=Set.of("dragon_sword","frost_katana","star_rapier","crimson_warblade","doom_scythe","earthshaker_hammer","sky_halberd",
            "yue_bronze_jian","willow_leaf_dao","crescent_guandao","fangtian_ji","red_tassel_spear",
            "ring_pommel_dao","han_iron_jian","iron_jian_mace","monk_spade","qimei_staff",
            "ashwood_jian","cloudridge_jian","ironwood_mallet","ring_flange_hammer","thunder_bronze_hammer",
            "militia_spear","silver_hook_spear","coiled_dragon_spear","bronze_ge_halberd","single_crescent_halberd",
            "black_iron_halberd","field_scythe","iron_crescent_scythe","white_bone_scythe","eclipse_scythe");
    private static ResourceLocation model(String id){return ResourceLocation.fromNamespaceAndPath("tnc","item/warrior_ghost/"+id);}
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event){for(String id:IDS)event.register(model(id));}
    @SubscribeEvent public static void baked(ModelEvent.BakingCompleted event){
        WarriorModelBlade.clear();
        for(String id:IDS){var item=ResourceLocation.fromNamespaceAndPath("tnc",id);WarriorModelBlade.find(item);WarriorModelGrip.find(item);}
    }
    static BakedModel find(ItemStack stack,BakedModel fallback){
        var key=ForgeRegistries.ITEMS.getKey(stack.getItem());
        if(key==null || !key.getNamespace().equals("tnc") || !IDS.contains(key.getPath()))return fallback;
        var manager=Minecraft.getInstance().getModelManager();var model=manager.getModel(model(key.getPath()));
        return model==manager.getMissingModel()?fallback:model;
    }
}
