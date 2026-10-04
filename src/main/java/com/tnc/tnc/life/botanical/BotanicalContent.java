package com.tnc.tnc.life.botanical;

import com.tnc.tnc.TNMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/** Automatic MOD-bus registration; no central registry or authored quest changes. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class BotanicalContent {
    public static final Map<String,Item> PRODUCTS=new LinkedHashMap<>(),SEEDS=new LinkedHashMap<>(),TOOLS=new LinkedHashMap<>();
    public static final Map<String,BotanicalBlock> BLOCKS=new LinkedHashMap<>();
    public static BlockEntityType<BotanicalPlantEntity> PLANT_ENTITY;
    public static EntityType<BotanicalSprite> SPRITE;
    public static net.minecraft.world.inventory.MenuType<PortableFieldMenu> FIELD_MENU;
    public static net.minecraft.world.item.crafting.RecipeSerializer<BotanicalKitchenRecipe> KITCHEN_SERIALIZER;
    public static Block FIELD_FRAME,SALT_BASIN,VINE_SEGMENT,PAPER_SEGMENT;
    private BotanicalContent(){}
    public static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(TNMod.MODID,path);}
    @SubscribeEvent public static void register(RegisterEvent event){
        event.register(Registries.BLOCK,h->{if(!BLOCKS.isEmpty())return;for(var s:BotanicalSpecies.values()){var b=new BotanicalBlock(s);BLOCKS.put(s.id,b);h.register(id(s.id),b);}FIELD_FRAME=new Block(BlockBehaviour.Properties.copy(Blocks.OAK_FENCE).noOcclusion());SALT_BASIN=new Block(BlockBehaviour.Properties.copy(Blocks.FLOWER_POT).noOcclusion());VINE_SEGMENT=new BotanicalSegment(true);PAPER_SEGMENT=new BotanicalSegment(false);h.register(id("field_frame"),FIELD_FRAME);h.register(id("salt_basin"),SALT_BASIN);h.register(id("botanical_vine_segment"),VINE_SEGMENT);h.register(id("botanical_paper_segment"),PAPER_SEGMENT);});
        event.register(Registries.BLOCK_ENTITY_TYPE,h->{if(PLANT_ENTITY==null){PLANT_ENTITY=BlockEntityType.Builder.of(BotanicalPlantEntity::new,BLOCKS.values().toArray(Block[]::new)).build(null);h.register(id("botanical_plant"),PLANT_ENTITY);}});
        event.register(Registries.ENTITY_TYPE,h->{if(SPRITE==null){SPRITE=EntityType.Builder.<BotanicalSprite>of(BotanicalSprite::new,MobCategory.MISC).sized(.2F,.3F).clientTrackingRange(8).updateInterval(3).build("tnc:botanical_sprite");h.register(id("botanical_sprite"),SPRITE);}});
        event.register(Registries.MENU,h->{if(FIELD_MENU==null){FIELD_MENU=net.minecraftforge.common.extensions.IForgeMenuType.create((window,inventory,buffer)->new PortableFieldMenu(window,inventory,buffer.readBoolean(),buffer.readVarInt()));h.register(id("portable_field_container"),FIELD_MENU);}});
        event.register(Registries.RECIPE_SERIALIZER,h->{if(KITCHEN_SERIALIZER==null){KITCHEN_SERIALIZER=new BotanicalKitchenRecipe.Serializer();h.register(id("field_kitchen"),KITCHEN_SERIALIZER);}});
        event.register(Registries.ITEM,h->{if(!SEEDS.isEmpty())return;for(var s:BotanicalSpecies.values()){
            Item seed=s==BotanicalSpecies.MIRROR_LOTUS?new LotusSeedItem(BLOCKS.get(s.id)):new ItemNameBlockItem(BLOCKS.get(s.id),new Item.Properties());SEEDS.put(s.id,seed);h.register(id(s.id+"_seed"),seed);
            Item product=s==BotanicalSpecies.WISH_PUFF?new FarLightFruitItem():new BotanicalProductItem(s.product,s==BotanicalSpecies.STAR_DEW?BotanicalProductItem.Mode.MANA30:BotanicalProductItem.Mode.RAW);PRODUCTS.put(s.product,product);h.register(id(s.product),product);
        }
        for(var entry:BotanicalProductItem.PROCESSED.entrySet()){Item item=new BotanicalProductItem(entry.getKey(),entry.getValue());PRODUCTS.put(entry.getKey(),item);h.register(id(entry.getKey()),item);}
        for(String tool:new String[]{"plant_sample_clip","rain_watering_flask","pollination_brush","field_tuning_bell"}){Item item=new BotanicalToolItem(tool);TOOLS.put(tool,item);h.register(id(tool),item);}
        PRODUCTS.put("rainproof_seed_box",new PortableFieldItem(false));PRODUCTS.put("survey_archive_folder",new PortableFieldItem(true));PRODUCTS.put("climbing_rope",new ClimbingRopeItem());for(String key:new String[]{"rainproof_seed_box","survey_archive_folder","climbing_rope"})h.register(id(key),PRODUCTS.get(key));
        h.register(id("field_frame"),new net.minecraft.world.item.BlockItem(FIELD_FRAME,new Item.Properties()));h.register(id("salt_basin"),new net.minecraft.world.item.BlockItem(SALT_BASIN,new Item.Properties()));});
    }
    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent e){if(e.getTabKey().equals(TNMod.TNC_TAB.getKey())){SEEDS.values().forEach(e::accept);PRODUCTS.values().forEach(e::accept);TOOLS.values().forEach(e::accept);e.accept(FIELD_FRAME);e.accept(SALT_BASIN);}}
}
