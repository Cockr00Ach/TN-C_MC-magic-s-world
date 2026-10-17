package com.tnc.tnc.equipment;

import com.tnc.tnc.TNMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import java.util.List;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class PurityAmuletRegistration {
    public static Item ITEM;
    @SubscribeEvent public static void register(RegisterEvent event){
        event.register(Registries.ITEM,helper->{
            ITEM=new AmuletItem();helper.register(ResourceLocation.fromNamespaceAndPath("tnc","purity_amulet"),ITEM);
        });
    }
    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent event){
        if(event.getTabKey().equals(TNMod.TNC_TAB.getKey()))event.accept(ITEM);
    }
    public static final class AmuletItem extends Item {
        public AmuletItem(){super(new Properties().stacksTo(1).rarity(Rarity.EPIC));}
        @Override public boolean isFoil(ItemStack stack){return true;}
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
            lines.add(Component.translatable("tooltip.tnc.purity_amulet.equip"));
            lines.add(Component.translatable("tooltip.tnc.purity_amulet.immunity"));
            lines.add(Component.translatable("tooltip.tnc.purity_amulet.benefits"));
        }
    }
}
