package com.tnc.tnc.alchemy;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class AlchemyContent {
    public static Block CAULDRON,HIDDEN_CACHE,BOARD;public static Item FUEL,STIRRER;public static BlockEntityType<AlchemyEntity> ENTITY;public static BlockEntityType<HiddenCacheEntity> CACHE_ENTITY;
    public static MenuType<AlchemyMenu> MENU;public static MenuType<com.tnc.tnc.journey.PreparationMenu> PREP_MENU;
    public static net.minecraft.world.effect.MobEffect ECHO;
    public static final Map<Elixir,Item> POTIONS=new EnumMap<>(Elixir.class);
    public static ResourceLocation id(String n){return ResourceLocation.parse("tnc:"+n);}
    @SubscribeEvent public static void register(RegisterEvent e){
        e.register(Registries.MOB_EFFECT,h->{ECHO=new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.BENEFICIAL,0x7beee8){};h.register(id("echo_sight"),ECHO);});
        e.register(Registries.BLOCK,h->{BOARD=new com.tnc.tnc.journey.CommissionBoardBlock();h.register(id("commission_board"),BOARD);CAULDRON=new AlchemyBlock();HIDDEN_CACHE=new HiddenCacheBlock();h.register(id("spellflame_cauldron"),CAULDRON);h.register(id("echo_hidden_cache"),HIDDEN_CACHE);});
        e.register(Registries.ITEM,h->{h.register(id("commission_board"),new BlockItem(BOARD,new Item.Properties()));h.register(id("spellflame_cauldron"),new BlockItem(CAULDRON,new Item.Properties()));h.register(id("echo_hidden_cache"),new BlockItem(HIDDEN_CACHE,new Item.Properties()));STIRRER=new Item(new Item.Properties().stacksTo(1));h.register(id("ritual_stirrer"),STIRRER);FUEL=new Item(new Item.Properties());h.register(id("ember_blossom_salt"),FUEL);for(var p:Elixir.values()){Item i=p.agricultural()?new com.tnc.tnc.arcana.ArcanaItem(p.outputId()):new ElixirItem(p);POTIONS.put(p,i);h.register(id(p.outputId()),i);}});
        e.register(Registries.BLOCK_ENTITY_TYPE,h->{ENTITY=BlockEntityType.Builder.of(AlchemyEntity::new,CAULDRON).build(null);CACHE_ENTITY=BlockEntityType.Builder.of(HiddenCacheEntity::new,HIDDEN_CACHE).build(null);h.register(id("spellflame_cauldron"),ENTITY);h.register(id("echo_hidden_cache"),CACHE_ENTITY);});
        e.register(Registries.MENU,h->{MENU=net.minecraftforge.common.extensions.IForgeMenuType.create(AlchemyMenu::new);PREP_MENU=net.minecraftforge.common.extensions.IForgeMenuType.create(com.tnc.tnc.journey.PreparationMenu::new);h.register(id("spellflame_cauldron"),MENU);h.register(id("expedition_preparation"),PREP_MENU);});
    }
    @SubscribeEvent public static void creative(net.minecraftforge.event.BuildCreativeModeTabContentsEvent e){if(e.getTabKey().equals(com.tnc.tnc.TNMod.TNC_TAB.getKey())){e.accept(BOARD);e.accept(CAULDRON);e.accept(FUEL);e.accept(STIRRER);POTIONS.values().forEach(e::accept);}}
}
