package com.tnc.tnc.building;

import com.tnc.tnc.TNMod;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Ordinary static building blocks: no ticking, entities or per-frame effects. */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ElementBossBlocks {
    public static final List<String> THEMES = List.of("earth", "water", "dark", "thunder_violet", "thunder_gold", "light");
    public static final int SHADES = 8;
    private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();

    private ElementBossBlocks() {}

    public static String name(String theme, int shade) {
        if (!THEMES.contains(theme) || shade < 1 || shade > SHADES) throw new IllegalArgumentException("Unknown runestone shade");
        return theme + "_boss_runestone_" + shade;
    }

    public static Block block(String theme, int shade) {
        return BLOCKS.get(name(theme, shade));
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            for (String theme : THEMES) for (int shade = 1; shade <= SHADES; shade++) {
                String name = name(theme, shade);
                MapColor color = switch (theme) {
                    case "water" -> MapColor.COLOR_BLUE;
                    case "dark" -> MapColor.COLOR_BLACK;
                    case "thunder_violet" -> MapColor.COLOR_PURPLE;
                    case "thunder_gold" -> MapColor.GOLD;
                    case "light" -> MapColor.QUARTZ;
                    default -> MapColor.TERRACOTTA_BROWN;
                };
                Block block = new Block(BlockBehaviour.Properties.of().mapColor(color)
                        .strength(3.5F, 8.0F).sound(SoundType.STONE).requiresCorrectToolForDrops());
                BLOCKS.put(name, block);
                helper.register(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, name), block);
            }
        });
        event.register(Registries.ITEM, helper -> BLOCKS.forEach((name, block) -> {
            Item item = new BlockItem(block, new Item.Properties());
            ITEMS.put(name, item);
            helper.register(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, name), item);
        }));
    }

    @SubscribeEvent
    public static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(TNMod.TNC_TAB.getKey())) ITEMS.values().forEach(event::accept);
    }
}
