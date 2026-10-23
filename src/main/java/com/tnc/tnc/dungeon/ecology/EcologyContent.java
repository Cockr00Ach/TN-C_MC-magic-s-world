package com.tnc.tnc.dungeon.ecology;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.dungeon.MobCatalog;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Four separate entity factories and render rigs; no replacement of existing minions. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class EcologyContent {
    public static final List<String> KINDS=List.of("crab","siphon","flash","worm");
    public static final Map<String,EntityType<EcologyMob>> TYPES=new LinkedHashMap<>();
    public static final Map<String,Item> EGGS=new LinkedHashMap<>();
    public static EntityType<EcologyFlask> FLASK;
    private static final Map<String,MobCatalog.Species> SPECIES=Map.of(
        "crab",new MobCatalog.Species("mimic_crystal_crab","拟态魔晶蟹",MobCatalog.Family.MIMIC,MobCatalog.Role.WARRIOR,0x95622b,0xff384e,1.05f,.95f,44,7,.28),
        "siphon",new MobCatalog.Species("mana_siphon_jelly","噬魔幽浮",MobCatalog.Family.TENTACLE,MobCatalog.Role.WARRIOR,0x4c829b,0x8affef,.72f,.8f,24,1,.28),
        "flash",new MobCatalog.Species("glare_gargoyle","强光闪灵",MobCatalog.Family.DEMON,MobCatalog.Role.WARRIOR,0x423456,0xfff5c0,.85f,1.65f,38,5,.24),
        "worm",new MobCatalog.Species("source_crystal_grub","源晶吞噬虫",MobCatalog.Family.LIZARD,MobCatalog.Role.WARRIOR,0x5a526f,0x76f4dd,1.25f,.8f,48,1,.13));
    private EcologyContent(){}
    public static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("tnc",path);}
    public static MobCatalog.Species species(String kind){return SPECIES.getOrDefault(kind,SPECIES.get("crab"));}
    public static EcologyMob create(String kind,ServerLevel level){var type=TYPES.get(kind);return type==null?null:type.create(level);}
    @SubscribeEvent public static void register(RegisterEvent event){
        event.register(Registries.ENTITY_TYPE,helper->{
            for(String kind:KINDS){var s=species(kind);EntityType<EcologyMob> type=EntityType.Builder.<EcologyMob>of((t,l)->switch(kind){
                case "siphon"->new ManaSiphonJelly(t,l);case "flash"->new GlareGargoyle(t,l);case "worm"->new SourceCrystalGrub(t,l);default->new MimicCrystalCrab(t,l);
            },MobCategory.MONSTER).sized(s.width(),s.height()).clientTrackingRange(10).updateInterval(2).build("tnc:"+s.id());TYPES.put(kind,type);helper.register(id(s.id()),type);}
            FLASK=EntityType.Builder.<EcologyFlask>of(EcologyFlask::new,MobCategory.MISC).sized(.25f,.25f).clientTrackingRange(6).updateInterval(2).build("tnc:ecology_flask");helper.register(id("ecology_flask"),FLASK);
        });
        event.register(Registries.ITEM,helper->{for(String kind:KINDS){var s=species(kind);var egg=new ForgeSpawnEggItem(()->TYPES.get(kind),s.skin(),s.accent(),new Item.Properties());EGGS.put(kind,egg);helper.register(id(s.id()+"_spawn_egg"),egg);}});
    }
    @SubscribeEvent public static void attributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent event){TYPES.values().forEach(t->event.put(t,EcologyMob.attributes().build()));}
    @SubscribeEvent public static void creative(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event){if(event.getTabKey().equals(TNMod.TNC_TAB.getKey()))EGGS.values().forEach(event::accept);}
}
