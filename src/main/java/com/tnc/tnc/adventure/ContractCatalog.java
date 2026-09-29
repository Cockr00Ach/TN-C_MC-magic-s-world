package com.tnc.tnc.adventure;

import java.util.List;

public final class ContractCatalog {
    public record Material(String id, boolean tag, int count) {}
    public record Contract(String id, String title, int grade, List<Material> materials,
                           String enemy, int kills, int xp, int reputation, int coins, boolean teaching) {}
    private static Contract gather(String id, String title, int grade, String item, boolean tag, int count, int xp, int rep, int coins) {
        return new Contract(id,title,grade,List.of(new Material(item,tag,count)),"",0,xp,rep,coins,false);
    }
    private static Contract hunt(String id, String title, int grade, String mob, int count, int xp, int rep, int coins) {
        return new Contract(id,title,grade,List.of(),mob,count,xp,rep,coins,false);
    }
    public static final List<Contract> ALL = List.of(
        new Contract("welcome","教学 · 酒馆备料",0,List.of(new Material("minecraft:logs",true,4),new Material("minecraft:cobblestone",false,8)),"",0,120,10,30,true),
        gather("logs","E · 修缮用原木",0,"minecraft:logs",true,12,100,10,30),
        gather("stone","E · 炉边石料",0,"minecraft:cobblestone",false,24,80,8,20),
        gather("wheat","E · 面包房的小麦",0,"minecraft:wheat",false,12,100,10,30),
        gather("carrot","E · 炖锅里的胡萝卜",0,"minecraft:carrot",false,12,100,10,30),
        gather("bread","E · 旅人的面包",0,"minecraft:bread",false,6,140,12,40),
        gather("potato","E · 热腾腾的烤土豆",0,"minecraft:baked_potato",false,8,140,12,40),
        hunt("zombie","E · 夜路巡猎",0,"minecraft:zombie",4,160,12,40),
        hunt("spider","E · 清理蛛网",0,"minecraft:spider",4,160,12,40),
        gather("iron","D · 铁匠的炉料",1,"minecraft:iron_ingot",false,8,450,20,130),
        gather("copper","D · 铜制修缮件",1,"minecraft:copper_ingot",false,16,350,18,100),
        gather("steak","D · 远征肉食",1,"minecraft:cooked_beef",false,10,500,22,150),
        gather("stew","D · 温暖的蘑菇汤",1,"minecraft:mushroom_stew",false,6,600,25,180),
        hunt("skeleton","D · 弓箭威胁",1,"minecraft:skeleton",8,650,25,180),
        hunt("creeper","D · 道路爆破隐患",1,"minecraft:creeper",6,700,25,180)
    );
    public static Contract find(String id) { return ALL.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null); }
    private ContractCatalog() {}
}
