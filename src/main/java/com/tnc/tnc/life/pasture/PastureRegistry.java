package com.tnc.tnc.life.pasture;

import com.tnc.tnc.TNMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import java.util.LinkedHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid=TNMod.MODID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class PastureRegistry {
    public static final Map<String,EntityType<PastureAnimal>> TYPES=new LinkedHashMap<>();
    public static final Map<String,Item> ITEMS=new LinkedHashMap<>();
    public static final Map<String,Block> BLOCKS=new LinkedHashMap<>();
    public static BlockEntityType<PastureFacilityEntity> FACILITY_ENTITY;
    public static net.minecraft.world.inventory.MenuType<PastureMenu> MENU_TYPE;
    public static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath(TNMod.MODID,name);}
    public static Item item(String name){Item item=ITEMS.get(name);if(item==null)throw new IllegalArgumentException("Unknown pasture item: "+name);return item;}
    public static Block block(String name){Block block=BLOCKS.get(name);if(block==null)throw new IllegalArgumentException("Unknown pasture block: "+name);return block;}
    @SubscribeEvent public static void register(RegisterEvent event){
        event.register(Registries.ENTITY_TYPE,h->{if(!TYPES.isEmpty())return;for(var s:PastureSpecies.ALL){var type=EntityType.Builder.<PastureAnimal>of((t,l)->new PastureAnimal(t,l,s),MobCategory.CREATURE).sized(s.width()*PastureFlight.scale(s.id()),s.height()*PastureFlight.scale(s.id())).clientTrackingRange(10).build("tnc:"+s.id());TYPES.put(s.id(),type);h.register(id(s.id()),type);}});
        event.register(Registries.BLOCK,h->{if(!BLOCKS.isEmpty())return;facility("pasture_trough",PastureFacilityBlock.Kind.TROUGH);facility("habitat_marker",PastureFacilityBlock.Kind.MARKER);facility("pasture_tray",PastureFacilityBlock.Kind.TRAY);facility("egg_rack",PastureFacilityBlock.Kind.EGGS);facility("dew_rack",PastureFacilityBlock.Kind.DEW);facility("charging_perch",PastureFacilityBlock.Kind.CHARGING);facility("mana_bottle_base",PastureFacilityBlock.Kind.BOTTLE);facility("pasture_glow",PastureFacilityBlock.Kind.LIGHT);BLOCKS.forEach((key,block)->h.register(id(key),block));});
        event.register(Registries.BLOCK_ENTITY_TYPE,h->{if(FACILITY_ENTITY!=null)return;FACILITY_ENTITY=BlockEntityType.Builder.of(PastureFacilityEntity::new,BLOCKS.values().toArray(Block[]::new)).build(null);h.register(id("pasture_facility"),FACILITY_ENTITY);});
        event.register(Registries.ITEM,h->{if(!ITEMS.isEmpty())return;registerItems();ITEMS.forEach((key,item)->h.register(id(key),item));});
        event.register(Registries.MENU,h->{if(MENU_TYPE==null){MENU_TYPE=net.minecraftforge.common.extensions.IForgeMenuType.create(PastureMenu::new);h.register(id("pasture_facility"),MENU_TYPE);}});
    }
    private static void facility(String id,PastureFacilityBlock.Kind kind){BLOCKS.put(id,new PastureFacilityBlock(kind));}
    private static void registerItems(){
        String[] products={"prism_horn_shard","sand_otter_fiber","stonebarrow_meat","stone_bone","emberback_meat","warm_fat","tideback_meat","water_membrane","froststride_meat","frost_bone","froststride_egg","apiary_meat","sweet_fat","honeydew","starfelt_meat","soft_down","lantern_antler","shell_glue","flight_feather","watch_wing","mirror_scale","warm_breath","spring_concentrate","loam_pebble","air_plume","storm_crystal","runner_hide","horn_powder","forage_paper_hide","nest_glue","hoof_glue","lamp_wax","stonebarrow_hotpot","emberback_stew","tideback_chowder","frostwarm_skewer","honeydew_casserole","starfelt_travel_roll","soft_lantern","waterproof_seed_wrap","quiet_felt","calming_bell","warning_lens","focus_lens","wind_bottle","warm_feed","climbing_cord","beast_saddle","empty_breath_jar","pasture_scraper"};
        for(String id:products)ITEMS.put(id,id.equals("waterproof_seed_wrap")?new com.tnc.tnc.life.botanical.PortableFieldItem(false):new PastureProductItem(id));
        ITEMS.put("pasture_book",new PastureBookItem());ITEMS.put("pasture_staff",new PastureStaffItem());ITEMS.put("pasture_cage",new PastureCageItem(1));ITEMS.put("radiant_cage",new PastureCageItem(2));ITEMS.put("star_cage",new PastureCageItem(3));ITEMS.put("fertile_pasture_egg",new Item(new Item.Properties().stacksTo(1)));ITEMS.put("mana_bottle",new ManaBottleItem(100));ITEMS.put("refined_mana_bottle",new ManaBottleItem(400));
        BLOCKS.forEach((id,block)->{if(!id.equals("pasture_glow"))ITEMS.put(id,new BlockItem(block,new Item.Properties()));});
        for(var species:PastureSpecies.ALL){int base=switch(species.role()){case MEAT->0x846845;case LIVE->0x4C8B81;case ELEMENT->0x60869A;case HELPER->0x93704D;};ITEMS.put(species.id()+"_spawn_egg",new net.minecraftforge.common.ForgeSpawnEggItem(()->TYPES.get(species.id()),base,0xDACFA1,new Item.Properties()));}
    }
    @SubscribeEvent public static void spawnPlacement(net.minecraftforge.event.entity.SpawnPlacementRegisterEvent event){TYPES.values().forEach(type->event.register(type,net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND,net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,PastureEcology::canSpawn,net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.Operation.REPLACE));}
    @SubscribeEvent public static void attributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent event){TYPES.values().forEach(type->event.put(type,PastureAnimal.attributes().build()));}
    @SubscribeEvent public static void creative(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event){if(event.getTabKey().equals(TNMod.TNC_TAB.getKey()))ITEMS.values().forEach(event::accept);}
}
