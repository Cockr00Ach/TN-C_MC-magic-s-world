package com.tnc.tnc.life.seagarden;

import com.tnc.tnc.TNMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

import java.util.EnumMap;

@Mod.EventBusSubscriber(modid="tnc", bus=Mod.EventBusSubscriber.Bus.MOD)
public final class SeaGarden {
    public enum Kind {
        LANTERN("tidebreath_lantern"), BINDWEED("undertow_bindweed"), FAN("waking_tide_fan"),
        BLOSSOM("moonreef_blossom"), ANEMONE("veilspore_anemone"), KELP("titan_tidekelp");
        public final String id;
        Kind(String id) { this.id=id; }
        public SeaPlantBlock block() { return BLOCKS.get(this); }
    }
    private static final EnumMap<Kind,SeaPlantBlock> BLOCKS=new EnumMap<>(Kind.class);
    private static final EnumMap<Kind,BlockItem> ITEMS=new EnumMap<>(Kind.class);

    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, registry -> {
            for (Kind kind : Kind.values()) {
                var block=kind==Kind.KELP ? new TidekelpBlock() : new SeaPlantBlock(kind);
                BLOCKS.put(kind,block);
                registry.register(ResourceLocation.fromNamespaceAndPath("tnc",kind.id),block);
            }
        });
        event.register(Registries.ITEM, registry -> {
            for (Kind kind : Kind.values()) {
                var item=new BlockItem(kind.block(), new Item.Properties());
                ITEMS.put(kind,item);
                registry.register(ResourceLocation.fromNamespaceAndPath("tnc",kind.id),item);
            }
        });
    }

    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(TNMod.TNC_TAB.getKey())) ITEMS.values().forEach(event::accept);
    }
    private SeaGarden() {}
}
