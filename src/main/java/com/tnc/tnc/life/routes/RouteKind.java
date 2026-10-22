package com.tnc.tnc.life.routes;
/** Mana and bandwidth are expressed in M/second; fractional consumption is retained. */
public enum RouteKind {
    INFUSER("mana_infuser","注能台",500,8),COLLECTOR("dew_collector","集露盏",100,4),ANIMAL_COLLECTOR("breath_collector","集息器",200,32),COLOR_TARGET("color_target","色靶",200,32),
    WIRE("mana_thread","青辉导魔晶纤",0,8),GOLD_WIRE("radiant_thread","霞金导魔晶纤",0,32),CRYSTAL_WIRE("star_thread","虹星导魔晶纤",0,128),
    POOL("mana_basin","浅魔池",1000,8),RESERVOIR("radiant_basin","曜纹储池",8000,32),VAULT("star_basin","星髓储池",32000,128),
    MIRROR("mana_mirror","折光镜",200,4),FAR_MIRROR("star_mirror","远折镜",400,16),VALVE("mana_valve","分流阀",200,32),
    LAMP("mana_lantern","魔导灯",20,4),BRIGHT_LAMP("bright_mana_lantern","庭院明灯",40,4),PAPER_PRESS("mana_pulp_press","纸皮压机",200,8),FEED_PLATE("mana_feed_plate","自动投料器",100,4),
    IRRIGATOR("gentle_irrigator","轻灌器",100,4),GREENHOUSE("climate_lantern","护候灯",200,4),SAWMILL("mana_sawmill","魔导锯台",200,8),LOOM("mist_loom","雾纱织机",300,8),
    DRYER("meal_dryer","远行烘食器",300,8),INCUBATOR("mana_incubator","暖卵台",100,4),HARVESTER("harvest_arm","收获臂",300,8),SEED_SORTER("seed_sorter","种源分拣器",100,4),
    CART_TRACK("mana_cart_track","魔导运货轨",200,8),ALARM("ward_bell","守望铃",100,4),CHARGER("accessory_charger","饰品充能台",800,128),
    ABYSS_TOWER("abyss_mana_tower","深渊魔力聚能塔",1000000,4096),RELIEF("mana_relief_valve","魔力泄压阀",16000,512),
    GEOTHERMAL("geothermal_mana_core","地热魔力核心",4000,64),SPRAYER("arcane_sprinkler","魔力农田喷灌塔",2000,64),
    SOLAR("corona_focusing_mirror","日冕聚焦巨镜",16000,512),LUNAR("moon_tide_basin","月华潮汐水盘",16000,512),OBSERVATORY("arcane_observatory","观星台",100,8),
    NURTURE("spirit_nurture_altar","魔力温养祭坛",16000,256),MEAL_PLATE("carved_mana_platter","雕花魔力餐盘",1000,64),
    RESTRAINT("ecology_restraint_cage","魔力生态拘束笼",2000,128),PRESSURE_PLATE("mana_pressure_tread","魔力压电踏板",1000,128),PROVOKER("mana_spray_provoker","喷水挑衅器",1000,64),HOOD("living_mana_hood","活体吸魔罩",4000,256),
    SKY_LAUNCHER("sky_mana_launch_ring","定向魔力喷射环",8000,512),SKY_RELAY("sky_mana_relay_ring","魔力导流中继环",1000,256),
    DIMENSION_PRISM("dimensional_mana_prism","高维魔力棱镜",64000,4096),SINGULARITY("singularity_mana_core","奇点魔力核心",2000000000,8192),
    EXTRACTOR("mana_extractor","魔力剥离台",200,8),IGNITER("elemental_igniter","元素引火桩",200,8),LEYLINE_COMPASS("leyline_compass","地脉寻迹仪",400,8),
    RESONANCE("beast_resonance_pedestal","灵兽共鸣台",400,16),FUMIGATOR("elixir_fumigator","药雾熏蒸釜",1000,32),PHASE_SHIFTER("elemental_phase_shifter","元素折射转相仪",4000,256);
    public final String id,name;public final int capacity,rate;
    RouteKind(String id,String name,int capacity,int rate){this.id=id;this.name=name;this.capacity=capacity;this.rate=rate;}
    public boolean wire(){return this==WIRE||this==GOLD_WIRE||this==CRYSTAL_WIRE;}
    public boolean storage(){return this==POOL||this==RESERVOIR||this==VAULT||this==ABYSS_TOWER||this==SINGULARITY;}
    public boolean supply(){return storage()||this==COLLECTOR||this==ANIMAL_COLLECTOR||this==COLOR_TARGET||this==INFUSER||this==MIRROR||this==FAR_MIRROR||this==VALVE||this==SOLAR||this==LUNAR||this==DIMENSION_PRISM||this==PRESSURE_PLATE||this==HOOD||this==PHASE_SHIFTER;}
}
