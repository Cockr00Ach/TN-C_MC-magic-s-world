package com.tnc.tnc.life.routes;
public enum NewPlantKind {
    COMPOST("dream_compost","沤梦菌","dream_humus","梦腐质",300,3.34,"三类有机料发酵90秒；同类只产生180魔力"),
    THERMAL("thermal_foldleaf","温差折叶","foldleaf_film","折叶膜",120,2,"真实燃烧炉热面＋冷面；魔导炉不能回供"),
    SHADOW("shadow_hour_orchid","影漏兰","shadow_hour_petal","影时瓣",480,0,"自然日影移动时蓄魔；每日最多八次"),
    PAGE("turnpage_fern","旋页蕨","page_fern_fiber","旋页丝",240,0,"讲台真实翻新页并消耗纸；30/页、每日240"),
    CYCLE("daynight_sandgrain","昼夜砂穗","cycle_sandgrain","昼夜砂",360,6,"白昼和夜晚各感受60秒，再释放360魔力"),
    DEW("hanging_dewgrass","垂露丝草","dewgrass_thread","露丝",600,2,"真实雨水逐秒蓄能，晴天慢慢放出"),
    PRISM("prism_crown","虹折冠","prism_crown_petal","虹折片",180,3,"天然日光通过三色玻璃折射；自身灯不生效"),
    ORE("ore_sleep_moss","矿眠苔","ore_dream_flake","矿眠屑",1200,2,"天然矿脉有限共鸣；同区块每日共享1200"),
    STORM("storm_crown","雷纹冠","storm_crown_tip","雷冠尖",2400,6,"自然雷击蓄2400；付费造雷只返实际能量的80%");
    public final String id,name,product,productName,help;public final int capacity;public final double rate;
    NewPlantKind(String id,String name,String product,String productName,int capacity,double rate,String help){this.id=id;this.name=name;this.product=product;this.productName=productName;this.capacity=capacity;this.rate=rate;this.help=help;}
}
