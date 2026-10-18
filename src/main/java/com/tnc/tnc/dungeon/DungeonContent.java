package com.tnc.tnc.dungeon;

import com.tnc.tnc.TNMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;
import java.util.*;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class DungeonContent {
    public static final Map<String,EntityType<DungeonMob>> MOBS=new LinkedHashMap<>();
    public static final Map<String,Item> ITEMS=new LinkedHashMap<>();
    public static final Map<DungeonTheme,Block> GATES=new EnumMap<>(DungeonTheme.class);
    public static EntityType<DungeonBolt> BOLT;
    public static Block CONSOLE,CACHE,WAYSTONE;
    public static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("tnc",path);}
    @SubscribeEvent public static void register(RegisterEvent e){
      e.register(Registries.ENTITY_TYPE,h->{for(var s:MobCatalog.ALL){var type=EntityType.Builder.<DungeonMob>of((t,l)->new DungeonMob(t,l,s.id()),MobCategory.MONSTER).sized(s.width(),s.height()).clientTrackingRange(10).build("tnc:"+s.id());MOBS.put(s.id(),type);h.register(id(s.id()),type);}BOLT=EntityType.Builder.<DungeonBolt>of(DungeonBolt::new,MobCategory.MISC).sized(.3f,.3f).clientTrackingRange(8).updateInterval(1).build("tnc:dungeon_bolt");h.register(id("dungeon_bolt"),BOLT);});
      e.register(Registries.BLOCK,h->{for(var t:DungeonTheme.values()){var b=new DungeonGateBlock(t);GATES.put(t,b);h.register(id("dungeon_gate_"+t.id),b);}CONSOLE=new DungeonInteractBlock("console");CACHE=new DungeonInteractBlock("cache");WAYSTONE=new DungeonInteractBlock("waystone");h.register(id("dungeon_console"),CONSOLE);h.register(id("dungeon_cache"),CACHE);h.register(id("dungeon_waystone"),WAYSTONE);});
      e.register(Registries.ITEM,h->{for(var s:MobCatalog.ALL)ITEMS.put(s.id()+"_spawn_egg",new net.minecraftforge.common.ForgeSpawnEggItem(()->MOBS.get(s.id()),s.skin(),s.accent(),new Item.Properties()));for(var t:DungeonTheme.values())ITEMS.put("dungeon_gate_"+t.id,new BlockItem(GATES.get(t),new Item.Properties()));ITEMS.put("dungeon_handbook",new DungeonBookItem());for(String n:List.of("sunwheel_shard","old_bell_bronze","tidal_shell_crystal","star_dream_shard","dungeon_memento"))ITEMS.put(n,new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));ITEMS.forEach((n,i)->h.register(id(n),i));});
    }
    @SubscribeEvent public static void attributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent e){MOBS.values().forEach(t->e.put(t,DungeonMob.attributes().build()));}
    @SubscribeEvent public static void creative(net.minecraftforge.event.BuildCreativeModeTabContentsEvent e){if(e.getTabKey().equals(TNMod.TNC_TAB.getKey()))ITEMS.values().forEach(e::accept);}
}
