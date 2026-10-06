package com.tnc.tnc.life.pasture;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.List;

/** Each entry owns a distinct entity registration, food, production clock and behaviour. */
public record PastureSpecies(String id,String name,Role role,Habitat habitat,Item food,
                             int adultDays,int breedDays,int harvestDays,String product,int amount,float width,float height) {
    public enum Role { MEAT,LIVE,ELEMENT,HELPER }
    public enum Habitat { LAND,WATER,PERCH,WARM,OPEN }
    public static final List<PastureSpecies> ALL=List.of(
        s("stonebarrow_boar","石垒豪豕",Role.MEAT,Habitat.LAND,Items.POTATO,2,1,2,"stonebarrow_meat",3,1.0f,0.9f),
        s("emberback_hog","暮炭野豕",Role.MEAT,Habitat.WARM,Items.CARROT,2,2,2,"emberback_meat",3,0.9f,0.9f),
        s("tideback_newt","潮背鳄螈",Role.MEAT,Habitat.WATER,Items.COD,3,2,2,"tideback_meat",2,1.0f,0.5f),
        s("froststride_fowl","银霜步禽",Role.MEAT,Habitat.LAND,Items.WHEAT_SEEDS,2,1,2,"froststride_meat",2,0.7f,1.2f),
        s("apiary_toad","蜂囊腴蛙",Role.MEAT,Habitat.WATER,Items.SWEET_BERRIES,2,2,2,"honeydew",1,0.8f,0.6f),
        s("starfelt_hare","星茸耳兔",Role.MEAT,Habitat.LAND,Items.CARROT,2,1,2,"starfelt_meat",2,0.6f,0.7f),
        s("dusk_lantern_deer","暮灯鹿",Role.LIVE,Habitat.LAND,Items.APPLE,2,2,4,"lantern_antler",1,0.9f,1.6f),
        s("pattern_shell_snail","纹壳蜗",Role.LIVE,Habitat.WATER,Items.SWEET_BERRIES,2,2,1,"shell_glue",1,0.65f,0.7f),
        s("post_heron","邮羽鹭",Role.LIVE,Habitat.PERCH,Items.WHEAT,2,3,3,"flight_feather",1,0.7f,1.6f),
        s("watch_mantis","巡灯螳螂",Role.LIVE,Habitat.PERCH,Items.BROWN_MUSHROOM,2,3,3,"watch_wing",1,0.65f,1.1f),
        s("mirrorwing_moth","镜瓣夜蛾",Role.LIVE,Habitat.PERCH,Items.SUGAR,2,2,2,"mirror_scale",1,1.0f,0.7f),
        s("forgegill_tapir","炉鳃山貘",Role.ELEMENT,Habitat.WARM,Items.BEETROOT,3,3,1,"warm_breath",1,1.1f,1.1f),
        s("mistbelly_otter","雾腹水獭",Role.ELEMENT,Habitat.WATER,Items.COD,2,2,1,"spring_concentrate",1,0.8f,0.6f),
        s("ringstone_tortoise","环砾龟",Role.ELEMENT,Habitat.LAND,Items.POTATO,3,3,2,"loam_pebble",2,1.0f,0.7f),
        s("papersail_ray","纸帆魟",Role.ELEMENT,Habitat.OPEN,Items.WHEAT_SEEDS,3,3,1,"air_plume",1,1.5f,0.6f),
        s("wirecall_lizard","鸣线蜥",Role.ELEMENT,Habitat.LAND,Items.SPIDER_EYE,2,3,1,"storm_crystal",1,0.8f,0.5f),
        s("dewbound_whale","蓄魔鲸",Role.ELEMENT,Habitat.OPEN,Items.MELON_SLICE,4,4,1,"stored_mana",1,1.5f,1.2f),
        s("satchelback_runner","囊背负兽",Role.HELPER,Habitat.LAND,Items.BREAD,3,3,2,"runner_hide",1,1.0f,1.1f),
        s("bowlhorn_rhino","碗角犀",Role.HELPER,Habitat.LAND,Items.BEETROOT,4,4,4,"horn_powder",1,1.25f,1.2f),
        s("pageforage_raccoon","觅页浣兽",Role.HELPER,Habitat.LAND,Items.SWEET_BERRIES,2,2,2,"forage_paper_hide",1,0.7f,0.7f),
        s("patternbuild_beaver","锦纹筑狸",Role.HELPER,Habitat.WATER,Items.SUGAR_CANE,3,3,2,"nest_glue",1,0.8f,0.8f),
        s("springhoof_strider","跃泉蹄兽",Role.HELPER,Habitat.LAND,Items.APPLE,3,3,3,"hoof_glue",1,1.0f,1.4f),
        s("prismatic_antelope","曳彩角羚",Role.ELEMENT,Habitat.LAND,Items.HONEY_BOTTLE,3,3,2,"prism_horn_shard",1,1.0f,1.6f),
        s("drumbelly_otter","鼓腹砂獭",Role.ELEMENT,Habitat.LAND,Items.CARROT,2,2,2,"sand_otter_fiber",1,0.95f,0.8f),
        s("pillowlight_marten","枕光貂",Role.HELPER,Habitat.LAND,Items.MELON_SLICE,2,3,3,"lamp_wax",1,0.65f,0.6f)
    );
    private static PastureSpecies s(String id,String name,Role role,Habitat habitat,Item food,int adult,int breed,int harvest,String product,int amount,float width,float height){return new PastureSpecies(id,name,role,habitat,food,adult,breed,harvest,product,amount,width,height);}
    public static PastureSpecies byId(String id){return ALL.stream().filter(s->s.id.equals(id)).findFirst().orElse(ALL.get(0));}
}

