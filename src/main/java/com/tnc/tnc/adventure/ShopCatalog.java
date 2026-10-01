package com.tnc.tnc.adventure;

import java.util.List;

/** Prices use copper. Produce is bought, never resold here: no buy/sell arbitrage. */
public final class ShopCatalog {
    public record Goods(String id,String name,String item,int count,int price,String detail){}
    public static final List<Goods> FURNITURE=List.of(
        new Goods("oak_chair","橡木靠背椅","model:chair",1,25,"木框与软垫，可坐。"),
        new Goods("blue_chair","海蓝扶手椅","model:armchair",1,45,"蓝色坐垫、双扶手，可坐。"),
        new Goods("dining_table","朝夕餐桌","model:table",1,60,"单格餐桌，可围坐用餐。"),
        new Goods("sofa","灯下双人沙发","model:sofa",1,120,"两格宽，两个座位。摆放前留出空间。"),
        new Goods("cabinet","归航储物柜","model:cabinet",1,90,"九格收纳，可右键使用。"),
        new Goods("bookcase","书风陈列架","model:bookcase",1,75,"木质分层展示架。"),
        new Goods("lamp","茶灯落地灯","model:lamp",1,55,"暖色灯罩，点亮房间。"),
        new Goods("bed","靛蓝床","minecraft:blue_bed",1,60,"凡家物语可识别的床；两人生活请备两张。"),
        new Goods("bookshelf","藏书柜","minecraft:bookshelf",1,50,"阅读与附魔空间。"),
        new Goods("carpet","海蓝地毯 ×8","minecraft:blue_carpet",8,16,"柔软地毯。"),
        new Goods("lantern","归航提灯","minecraft:lantern",1,20,"可悬挂。"),
        new Goods("flowerpot","窗边花盆","minecraft:flower_pot",1,10,"可种植花草。"),
        new Goods("oak_cabinet","橡木壁柜","farmersdelight:oak_cabinet",1,80,"农夫乐事原生壁柜。"),
        new Goods("spruce_cabinet","云杉壁柜","farmersdelight:spruce_cabinet",1,80,"农夫乐事原生壁柜。"),
        new Goods("candle","晚餐蜡烛 ×4","minecraft:candle",4,12,"摆好后用打火石点亮。"),
        new Goods("feast_table","团聚长桌","model:feast_table",1,150,"酒馆晚餐归档后开放；双格长桌，红色桌旗。"),
        new Goods("travel_lamp","远方航灯","model:travel_lamp",1,100,"远行见闻归档后开放；铜框蓝光，高架提灯。"),
        new Goods("memory_shelf","归处纪念柜","model:memory_shelf",1,130,"第一盏灯归档后开放；不对称陈列，六格储物。"));
    public static final List<Goods> PRODUCE=List.of(
        new Goods("wheat","小麦 ×16","minecraft:wheat",16,16,"农场主的基础收入。"),
        new Goods("carrot","胡萝卜 ×16","minecraft:carrot",16,12,"新鲜蔬菜。"),
        new Goods("potato","马铃薯 ×16","minecraft:potato",16,12,"新鲜蔬菜。"),
        new Goods("beetroot","甜菜根 ×16","minecraft:beetroot",16,16,"新鲜蔬菜。"),
        new Goods("pumpkin","南瓜 ×8","minecraft:pumpkin",8,24,"整颗收购。"),
        new Goods("melon","西瓜 ×8","minecraft:melon",8,24,"整颗收购。"),
        new Goods("berry","甜浆果 ×16","minecraft:sweet_berries",16,10,"野外采集也能出售。"),
        new Goods("egg","鸡蛋 ×8","minecraft:egg",8,16,"养鸡收入。"),
        new Goods("milk","牛奶桶 ×1","minecraft:milk_bucket",1,8,"收购牛奶，返还空桶。"),
        new Goods("wool","白色羊毛 ×8","minecraft:white_wool",8,24,"剪毛后羊仍能继续产毛。"),
        new Goods("beef","生牛肉 ×8","minecraft:beef",8,24,"牧场收获。"),
        new Goods("pork","生猪肉 ×8","minecraft:porkchop",8,24,"牧场收获。"),
        new Goods("chicken","生鸡肉 ×8","minecraft:chicken",8,16,"牧场收获。"),
        new Goods("mutton","生羊肉 ×8","minecraft:mutton",8,24,"牧场收获。"),
        new Goods("honey","蜂蜜瓶 ×4","minecraft:honey_bottle",4,16,"返还四个玻璃瓶。"),
        new Goods("tomato","番茄 ×16","farmersdelight:tomato",16,20,"农夫乐事作物。"),
        new Goods("cabbage","卷心菜 ×16","farmersdelight:cabbage",16,20,"农夫乐事作物。"),
        new Goods("onion","洋葱 ×16","farmersdelight:onion",16,20,"农夫乐事作物。"),
        new Goods("rice","稻米 ×16","farmersdelight:rice",16,20,"农夫乐事作物。"));
    public static Goods find(List<Goods> list,String id){return list.stream().filter(g->g.id().equals(id)).findFirst().orElse(null);}
    public static int quota(int level){return level>=45?2000:level>=20?1000:500;}
}
