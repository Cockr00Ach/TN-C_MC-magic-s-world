package com.tnc.tnc.production.energy;

import com.tnc.tnc.TNMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Independent energy registrations; this package never changes the older forge's IDs. */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EnergyContent {
    public static final ResourceLocation GENERATOR_ID = id("mana_generator");
    public static final ResourceLocation CABLE_ID = id("mana_cable");
    public static final ResourceLocation BATTERY_ID = id("mana_battery");
    public static final ResourceLocation WORK_LAMP_ID = id("mana_work_lamp");
    public static final ResourceLocation PAPER_PRESS_ID = id("mana_paper_press");

    public static EnergyBlock GENERATOR;
    public static EnergyBlock CABLE;
    public static EnergyBlock BATTERY;
    public static EnergyBlock WORK_LAMP;
    public static EnergyBlock PAPER_PRESS;
    public static Item GENERATOR_ITEM;
    public static Item CABLE_ITEM;
    public static Item BATTERY_ITEM;
    public static Item WORK_LAMP_ITEM;
    public static Item PAPER_PRESS_ITEM;
    public static BlockEntityType<EnergyBlockEntity> ENERGY_ENTITY;

    private EnergyContent() {}

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, name);
    }

    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            if (GENERATOR != null) return;
            GENERATOR = new EnergyBlock(EnergyBlockEntity.Kind.GENERATOR);
            CABLE = new EnergyBlock(EnergyBlockEntity.Kind.CABLE);
            BATTERY = new EnergyBlock(EnergyBlockEntity.Kind.BATTERY);
            WORK_LAMP = new EnergyBlock(EnergyBlockEntity.Kind.LAMP);
            PAPER_PRESS = new EnergyBlock(EnergyBlockEntity.Kind.PRESS);
            helper.register(GENERATOR_ID, GENERATOR);
            helper.register(CABLE_ID, CABLE);
            helper.register(BATTERY_ID, BATTERY);
            helper.register(WORK_LAMP_ID, WORK_LAMP);
            helper.register(PAPER_PRESS_ID, PAPER_PRESS);
        });
        event.register(Registries.ITEM, helper -> {
            if (GENERATOR_ITEM != null) return;
            GENERATOR_ITEM = new EnergyBlockItem(GENERATOR, EnergyBlockEntity.Kind.GENERATOR);
            CABLE_ITEM = new EnergyBlockItem(CABLE, EnergyBlockEntity.Kind.CABLE);
            BATTERY_ITEM = new EnergyBlockItem(BATTERY, EnergyBlockEntity.Kind.BATTERY);
            WORK_LAMP_ITEM = new EnergyBlockItem(WORK_LAMP, EnergyBlockEntity.Kind.LAMP);
            PAPER_PRESS_ITEM = new EnergyBlockItem(PAPER_PRESS, EnergyBlockEntity.Kind.PRESS);
            helper.register(GENERATOR_ID, GENERATOR_ITEM);
            helper.register(CABLE_ID, CABLE_ITEM);
            helper.register(BATTERY_ID, BATTERY_ITEM);
            helper.register(WORK_LAMP_ID, WORK_LAMP_ITEM);
            helper.register(PAPER_PRESS_ID, PAPER_PRESS_ITEM);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            if (ENERGY_ENTITY != null) return;
            ENERGY_ENTITY = BlockEntityType.Builder.of(EnergyBlockEntity::new,
                    GENERATOR, CABLE, BATTERY, WORK_LAMP, PAPER_PRESS).build(null);
            helper.register(id("energy_node"), ENERGY_ENTITY);
        });
    }

    // Legacy IDs stay registered for decoding, but new worlds use RouteContent.
}
