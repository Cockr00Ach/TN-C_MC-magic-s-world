package com.tnc.tnc.production;

import com.tnc.tnc.TNMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Registers the workshop without changing the town, island, or the original quest book. */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MagicForgeContent {
    public static final ResourceLocation FORGE_ID = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "magic_forge");
    public static final ResourceLocation COIL_ID = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "mana_copper_coil");
    public static final ResourceLocation LAMP_ID = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "homeward_lamp");
    public static final ResourceLocation BRICK_ID = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "forge_firebrick");
    public static final ResourceLocation FRAME_ID = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "forge_copper_frame");
    public static final ResourceLocation INPUT_ID = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "forge_input_port");
    public static final ResourceLocation OUTPUT_ID = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "forge_output_port");
    public static final ResourceLocation INJECTOR_ID = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "forge_mana_injector");
    public static final ResourceLocation EXHAUST_ID = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "forge_exhaust");
    public static final ResourceLocation GUIDE_ID = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "forge_guide");
    public static final ResourceLocation FORGING_ID = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "mana_forging");

    // Forge's automatic subscriber scan loads this class while registries are frozen.
    // Construct registry entries only inside their own RegisterEvent callbacks.
    public static MagicForgeBlock FORGE;
    public static HomewardLampBlock LAMP;
    public static ForgeBrickBlock FIREBRICK;
    public static ForgeFrameBlock COPPER_FRAME;
    public static ForgePortBlock INPUT_PORT;
    public static ForgePortBlock OUTPUT_PORT;
    public static ForgeInjectorBlock INJECTOR;
    public static ForgeExhaustBlock EXHAUST;
    public static ForgePartBlock HEART;
    public static Item HEART_ITEM;
    public static Item FORGE_ITEM;
    public static Item MANA_COPPER_COIL;
    public static Item LAMP_ITEM;
    public static Item BRICK_ITEM;
    public static Item FRAME_ITEM;
    public static Item INPUT_ITEM;
    public static Item OUTPUT_ITEM;
    public static Item INJECTOR_ITEM;
    public static Item EXHAUST_ITEM;
    public static Item GUIDE_ITEM;
    public static BlockEntityType<MagicForgeBlockEntity> FORGE_ENTITY;
    public static BlockEntityType<ForgePortBlockEntity> PORT_ENTITY;
    public static MenuType<MagicForgeMenu> FORGE_MENU;
    public static RecipeType<ManaForgeRecipe> FORGE_RECIPE_TYPE;
    public static RecipeSerializer<ManaForgeRecipe> FORGE_SERIALIZER;

    private MagicForgeContent() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            FORGE = new MagicForgeBlock();
            LAMP = new HomewardLampBlock();
            FIREBRICK = new ForgeBrickBlock();
            COPPER_FRAME = new ForgeFrameBlock();
            INPUT_PORT = new ForgePortBlock(false);
            OUTPUT_PORT = new ForgePortBlock(true);
            INJECTOR = new ForgeInjectorBlock();
            EXHAUST = new ForgeExhaustBlock();
            HEART = new ForgePartBlock(false);
            helper.register(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "forge_core"), HEART);
            helper.register(FORGE_ID, FORGE);
            helper.register(LAMP_ID, LAMP);
            helper.register(BRICK_ID, FIREBRICK);
            helper.register(FRAME_ID, COPPER_FRAME);
            helper.register(INPUT_ID, INPUT_PORT);
            helper.register(OUTPUT_ID, OUTPUT_PORT);
            helper.register(INJECTOR_ID, INJECTOR);
            helper.register(EXHAUST_ID, EXHAUST);
        });
        event.register(Registries.ITEM, helper -> {
            FORGE_ITEM = new MagicForgeItem(FORGE);
            MANA_COPPER_COIL = new Item(new Item.Properties());
            LAMP_ITEM = new BlockItem(LAMP, new Item.Properties());
            BRICK_ITEM = new BlockItem(FIREBRICK, new Item.Properties());
            FRAME_ITEM = new BlockItem(COPPER_FRAME, new Item.Properties());
            INPUT_ITEM = new BlockItem(INPUT_PORT, new Item.Properties());
            OUTPUT_ITEM = new BlockItem(OUTPUT_PORT, new Item.Properties());
            INJECTOR_ITEM = new BlockItem(INJECTOR, new Item.Properties());
            EXHAUST_ITEM = new BlockItem(EXHAUST, new Item.Properties());
            HEART_ITEM = new BlockItem(HEART, new Item.Properties());
            helper.register(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "forge_core"), HEART_ITEM);
            GUIDE_ITEM = new ForgeGuideItem();
            helper.register(FORGE_ID, FORGE_ITEM);
            helper.register(COIL_ID, MANA_COPPER_COIL);
            helper.register(LAMP_ID, LAMP_ITEM);
            helper.register(BRICK_ID, BRICK_ITEM);
            helper.register(FRAME_ID, FRAME_ITEM);
            helper.register(INPUT_ID, INPUT_ITEM);
            helper.register(OUTPUT_ID, OUTPUT_ITEM);
            helper.register(INJECTOR_ID, INJECTOR_ITEM);
            helper.register(EXHAUST_ID, EXHAUST_ITEM);
            helper.register(GUIDE_ID, GUIDE_ITEM);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            FORGE_ENTITY = BlockEntityType.Builder.of(MagicForgeBlockEntity::new, FORGE).build(null);
            helper.register(FORGE_ID, FORGE_ENTITY);
            PORT_ENTITY = BlockEntityType.Builder.of(ForgePortBlockEntity::new, INPUT_PORT, OUTPUT_PORT, FIREBRICK).build(null);
            helper.register(INPUT_ID, PORT_ENTITY);
        });
        event.register(Registries.MENU, helper -> {
            FORGE_MENU = IForgeMenuType.create(MagicForgeMenu::new);
            helper.register(FORGE_ID, FORGE_MENU);
        });
        event.register(Registries.RECIPE_TYPE, helper -> {
            FORGE_RECIPE_TYPE = new RecipeType<>() {
                @Override public String toString() { return FORGING_ID.toString(); }
            };
            helper.register(FORGING_ID, FORGE_RECIPE_TYPE);
        });
        event.register(Registries.RECIPE_SERIALIZER, helper -> {
            FORGE_SERIALIZER = new ManaForgeRecipe.Serializer();
            helper.register(FORGING_ID, FORGE_SERIALIZER);
        });
    }

    @SubscribeEvent
    public static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(TNMod.TNC_TAB.getKey())) {
            event.accept(FORGE_ITEM);
        event.accept(HEART_ITEM);
            event.accept(MANA_COPPER_COIL);
            event.accept(LAMP_ITEM);
            event.accept(BRICK_ITEM);
            event.accept(FRAME_ITEM);
            event.accept(INPUT_ITEM);
            event.accept(OUTPUT_ITEM);
            event.accept(INJECTOR_ITEM);
            event.accept(EXHAUST_ITEM);
            event.accept(GUIDE_ITEM);
        }
    }
}
