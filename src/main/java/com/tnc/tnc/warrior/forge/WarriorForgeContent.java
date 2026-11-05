package com.tnc.tnc.warrior.forge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Independent smithing stations: no registrations or recipe changes in the magic forge. */
@Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WarriorForgeContent {
    public static Block HEARTH, ANVIL, BATH;
    public static Item HAMMER, BILLET, SWORD_GRIP, LONG_GRIP, MACE_GRIP;
    public static BlockEntityType<WarriorForgeEntity> ENTITY;
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("tnc", path); }

    @SubscribeEvent public static void register(RegisterEvent e) {
        e.register(Registries.BLOCK, h -> {
            h.register(id("warrior_hearth"), HEARTH = new WarriorForgeBlock(WarriorForgeBlock.Kind.HEARTH));
            h.register(id("warrior_anvil"), ANVIL = new WarriorForgeBlock(WarriorForgeBlock.Kind.ANVIL));
            h.register(id("warrior_quench_bath"), BATH = new WarriorForgeBlock(WarriorForgeBlock.Kind.BATH));
        });
        e.register(Registries.ITEM, h -> {
            h.register(id("warrior_hearth"), new BlockItem(HEARTH, new Item.Properties()));
            h.register(id("warrior_anvil"), new BlockItem(ANVIL, new Item.Properties()));
            h.register(id("warrior_quench_bath"), new BlockItem(BATH, new Item.Properties()));
            h.register(id("warrior_forge_hammer"), HAMMER = new ForgeHammerItem());
            h.register(id("warrior_forge_billet"), BILLET = new Item(new Item.Properties().stacksTo(1)));
            h.register(id("warrior_sword_grip"), SWORD_GRIP = new Item(new Item.Properties()));
            h.register(id("warrior_long_grip"), LONG_GRIP = new Item(new Item.Properties()));
            h.register(id("warrior_mace_grip"), MACE_GRIP = new Item(new Item.Properties()));
        });
        e.register(Registries.BLOCK_ENTITY_TYPE, h -> {
            ENTITY = BlockEntityType.Builder.of(WarriorForgeEntity::new, HEARTH, ANVIL, BATH).build(null);
            h.register(id("warrior_workbench"), ENTITY);
        });
    }
    @SubscribeEvent public static void creative(net.minecraftforge.event.BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey().equals(com.tnc.tnc.TNMod.TNC_TAB.getKey())) {
            e.accept(HEARTH); e.accept(ANVIL); e.accept(BATH); e.accept(HAMMER);
            e.accept(SWORD_GRIP); e.accept(LONG_GRIP); e.accept(MACE_GRIP);
        }
    }
}
