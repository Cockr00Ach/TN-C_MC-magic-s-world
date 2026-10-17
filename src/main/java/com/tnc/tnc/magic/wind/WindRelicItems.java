package com.tnc.tnc.magic.wind;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** The spell models are also real equipment, available for players and future boss hands. */
@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class WindRelicItems {
    public static Item SWORD,SPEAR;
    @SubscribeEvent public static void register(RegisterEvent event){
        event.register(Registries.ITEM,helper->{
            SWORD=new SwordItem(Tiers.NETHERITE,4,-2.4F,new Item.Properties().fireResistant());
            SPEAR=new SwordItem(Tiers.NETHERITE,6,-2.8F,new Item.Properties().fireResistant());
            helper.register(ResourceLocation.fromNamespaceAndPath("tnc","wind_relic_sword"),SWORD);
            helper.register(ResourceLocation.fromNamespaceAndPath("tnc","wind_relic_spear"),SPEAR);
        });
    }
    @SubscribeEvent public static void tab(BuildCreativeModeTabContentsEvent event){
        if(event.getTabKey()==CreativeModeTabs.COMBAT){event.accept(SWORD);event.accept(SPEAR);}
    }
}
