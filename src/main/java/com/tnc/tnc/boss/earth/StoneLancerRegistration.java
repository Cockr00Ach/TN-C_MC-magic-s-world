package com.tnc.tnc.boss.earth;

import com.tnc.tnc.TNMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class StoneLancerRegistration {
    public static EntityType<StoneLancerEntity> TYPE;
    public static Item EGG;
    @SubscribeEvent public static void register(RegisterEvent e){
        e.register(Registries.ENTITY_TYPE,h->{
            TYPE=EntityType.Builder.<StoneLancerEntity>of(StoneLancerEntity::new,MobCategory.MONSTER)
                    .sized(29.7F,26.66F).fireImmune().clientTrackingRange(32).updateInterval(2).build("tnc:stone_lancer");
            h.register(ResourceLocation.fromNamespaceAndPath("tnc","stone_lancer"),TYPE);
        });
        e.register(Registries.ITEM,h->{
            EGG=new ForgeSpawnEggItem(()->TYPE,0x53463A,0xE8AF4E,new Item.Properties());
            h.register(ResourceLocation.fromNamespaceAndPath("tnc","stone_lancer_spawn_egg"),EGG);
        });
    }
    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent e){e.put(TYPE,StoneLancerEntity.attributes().build());}
    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent e){if(e.getTabKey().equals(TNMod.TNC_TAB.getKey()))e.accept(EGG);}
}
