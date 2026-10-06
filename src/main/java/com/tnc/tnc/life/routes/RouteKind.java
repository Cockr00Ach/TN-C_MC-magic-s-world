package com.tnc.tnc.life.routes;
/** Mana and bandwidth are expressed in M/second; fractional consumption is retained. */
public enum RouteKind {
    INFUSER("mana_infuser","注能台",500,8),COLLECTOR("dew_collector","集露盏",100,4),ANIMAL_COLLECTOR("breath_collector","集息器",200,32),COLOR_TARGET("color_target","色靶",200,32),
    WIRE("mana_thread","魔导丝",0,8),GOLD_WIRE("radiant_thread","曜纹导线",0,32),CRYSTAL_WIRE("star_thread","星髓导线",0,128),
    POOL("mana_basin","浅魔池",1000,8),RESERVOIR("radiant_basin","曜纹储池",8000,32),VAULT("star_basin","星髓储池",32000,128),
    MIRROR("mana_mirror","折光镜",200,4),FAR_MIRROR("star_mirror","远折镜",400,16),VALVE("mana_valve","分流阀",200,32),
    LAMP("mana_lantern","魔导灯",20,4),BRIGHT_LAMP("bright_mana_lantern","庭院明灯",40,4),PAPER_PRESS("mana_pulp_press","纸皮压机",200,8),FEED_PLATE("mana_feed_plate","饲料分盘",100,4),
    IRRIGATOR("gentle_irrigator","轻灌器",100,4),GREENHOUSE("climate_lantern","护候灯",200,4),SAWMILL("mana_sawmill","魔导锯台",200,8),LOOM("mist_loom","雾纱织机",300,8),
    DRYER("meal_dryer","远行烘食器",300,8),INCUBATOR("mana_incubator","暖卵台",100,4),HARVESTER("harvest_arm","收获臂",300,8),SEED_SORTER("seed_sorter","种源分拣器",100,4),
    CART_TRACK("mana_cart_track","魔导运货轨",200,8),ALARM("ward_bell","守望铃",100,4),CHARGER("accessory_charger","饰品充能台",800,16);
    public final String id,name;public final int capacity,rate;
    RouteKind(String id,String name,int capacity,int rate){this.id=id;this.name=name;this.capacity=capacity;this.rate=rate;}
    public boolean wire(){return this==WIRE||this==GOLD_WIRE||this==CRYSTAL_WIRE;}
    public boolean storage(){return this==POOL||this==RESERVOIR||this==VAULT;}
    public boolean supply(){return storage()||this==COLLECTOR||this==ANIMAL_COLLECTOR||this==COLOR_TARGET||this==INFUSER||this==MIRROR||this==FAR_MIRROR||this==VALVE;}
}
