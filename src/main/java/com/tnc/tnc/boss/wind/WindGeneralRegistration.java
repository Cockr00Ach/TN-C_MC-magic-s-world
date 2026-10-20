package com.tnc.tnc.boss.wind;

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
public final class WindGeneralRegistration {
    public static EntityType<WindGeneralEntity> TYPE;
    public static Item EGG;
    @SubscribeEvent public static void register(RegisterEvent e){
        e.register(Registries.ENTITY_TYPE,h->{
            TYPE=EntityType.Builder.<WindGeneralEntity>of(WindGeneralEntity::new,MobCategory.CREATURE)
                    .sized(.85F,2.9F).clientTrackingRange(12).updateInterval(3).build("tnc:wind_general");
            h.register(ResourceLocation.fromNamespaceAndPath("tnc","wind_general"),TYPE);
        });
        e.register(Registries.ITEM,h->{EGG=new ForgeSpawnEggItem(()->TYPE,0xE8EEEE,0x3D8F91,new Item.Properties());h.register(ResourceLocation.fromNamespaceAndPath("tnc","wind_general_spawn_egg"),EGG);});
    }
    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent e){e.put(TYPE,WindGeneralEntity.attributes().build());}
    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent e){if(e.getTabKey().equals(TNMod.TNC_TAB.getKey()))e.accept(EGG);}
}
