package com.tnc.tnc.life.routes;

import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/** Compatibility identities remain loadable, but never enter the current player's progression. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class RetiredLifeContent {
    public static final Set<String> IDS=Set.of("mana_generator","mana_battery","mana_work_lamp","mana_paper_press","mana_cable",
            "dew_rack","mana_bottle_base","egg_rack","plant_sample_clip","forge_copper_frame","forge_input_port","forge_output_port","forge_mana_injector","insulating_forge_clay");
    public static boolean hidden(Item item){var id=ForgeRegistries.ITEMS.getKey(item);return id!=null&&id.getNamespace().equals("tnc")&&IDS.contains(id.getPath());}
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void creative(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event){
        for(String id:IDS){var item=ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc",id));if(item!=null)event.getEntries().remove(new net.minecraft.world.item.ItemStack(item));}
    }
    private RetiredLifeContent(){}
}
