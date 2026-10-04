package com.tnc.tnc.life.wonders;

import com.tnc.tnc.TNMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid=TNMod.MODID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class WonderContent {
    public static LightNodeBlock LIGHT_NODE;
    public static SkyVineRootBlock SKY_VINE;
    public static Block VINE_STEM,VINE_LEAF;
    public static RopeNodeBlock ROPE;
    public static SoundRelayBlock RELAY;
    public static Item SKY_VINE_SEED,SKY_FIBER,SKY_ROPE,SURVEY;
    public static BlockEntityType<LightNodeEntity> LIGHT_ENTITY;
    public static BlockEntityType<SkyVineRootEntity> VINE_ENTITY;
    public static BlockEntityType<RopeNodeEntity> ROPE_ENTITY;
    public static BlockEntityType<SoundRelayEntity> RELAY_ENTITY;
    public static Item RELAY_ITEM;
    public static EntityType<FarLightProjectile> FARLIGHT;
    public static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("tnc",path);}
    @SubscribeEvent public static void register(RegisterEvent event){
        event.register(Registries.BLOCK,h->{if(LIGHT_NODE!=null)return;
            LIGHT_NODE=new LightNodeBlock();SKY_VINE=new SkyVineRootBlock();ROPE=new RopeNodeBlock();h.register(id("canopy_rope_segment"),ROPE);
            RELAY=new SoundRelayBlock();h.register(id("field_sound_relay"),RELAY);
            VINE_STEM=new Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).mapColor(net.minecraft.world.level.material.MapColor.COLOR_GREEN).strength(2).noOcclusion());
            VINE_LEAF=new Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.OAK_LEAVES).strength(.2f).noOcclusion());
            h.register(id("farlight_node"),LIGHT_NODE);h.register(id("sky_vine"),SKY_VINE);h.register(id("sky_vine_stem"),VINE_STEM);h.register(id("sky_vine_leaf"),VINE_LEAF);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE,h->{if(LIGHT_ENTITY!=null)return;
            LIGHT_ENTITY=BlockEntityType.Builder.of(LightNodeEntity::new,LIGHT_NODE).build(null);
            VINE_ENTITY=BlockEntityType.Builder.of(SkyVineRootEntity::new,SKY_VINE).build(null);
            ROPE_ENTITY=BlockEntityType.Builder.of(RopeNodeEntity::new,ROPE).build(null);
            RELAY_ENTITY=BlockEntityType.Builder.of(SoundRelayEntity::new,RELAY).build(null);h.register(id("field_sound_relay"),RELAY_ENTITY);
            h.register(id("farlight_node"),LIGHT_ENTITY);h.register(id("sky_vine"),VINE_ENTITY);h.register(id("canopy_rope_segment"),ROPE_ENTITY);
        });
        event.register(Registries.ENTITY_TYPE,h->{if(FARLIGHT!=null)return;FARLIGHT=EntityType.Builder.<FarLightProjectile>of(FarLightProjectile::new,MobCategory.MISC).sized(.25f,.25f).clientTrackingRange(4).updateInterval(10).build("tnc:farlight_fruit");h.register(id("farlight_fruit"),FARLIGHT);});
        event.register(Registries.ITEM,h->{if(SKY_VINE_SEED!=null)return;SKY_VINE_SEED=new ItemNameBlockItem(SKY_VINE,new Item.Properties());SKY_FIBER=new Item(new Item.Properties());SKY_ROPE=new SkyRopeItem();SURVEY=new SkySurveyItem();RELAY_ITEM=new net.minecraft.world.item.BlockItem(RELAY,new Item.Properties());h.register(id("field_sound_relay"),RELAY_ITEM);h.register(id("sky_vine_seed"),SKY_VINE_SEED);h.register(id("sky_canopy_fiber"),SKY_FIBER);h.register(id("sky_canopy_rope"),SKY_ROPE);h.register(id("sky_vine_survey"),SURVEY);});
    }
    @SubscribeEvent public static void creative(net.minecraftforge.event.BuildCreativeModeTabContentsEvent e){if(e.getTabKey().equals(TNMod.TNC_TAB.getKey())){e.accept(SKY_VINE_SEED);e.accept(SKY_FIBER);e.accept(SKY_ROPE);e.accept(SURVEY);e.accept(RELAY_ITEM);}}
}
