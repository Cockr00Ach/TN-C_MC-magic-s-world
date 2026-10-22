package com.tnc.tnc.alchemy;
import java.util.*;
/** Original herb routes; order-independent, exact ingredients, no permanent stat inflation. */
public enum Elixir {
    BLOOM("bloom","回春露",0xe16f90,"恢复8生命",0,160,"minecraft:glistering_melon_slice","tnc:homeward_flower_petal","minecraft:wheat"),
    SPRING("spring","凝泉饮",0x45c8ec,"立即恢复30法力",0,160,"tnc:rainletter_berry","minecraft:sugar","minecraft:kelp"),
    AWAKEN("awaken","醒神星露",0x98dfdb,"立即恢复60法力",0,260,"tnc:star_dew_fruit","tnc:prism_crown_petal","minecraft:honey_bottle"),
    FLOW("flow","续魔灵酊",0x4ee5b0,"60秒内每秒恢复1法力",60,220,"tnc:mana_root_core","tnc:rainletter_berry","minecraft:redstone"),
    EDGE("edge","锐锋琥珀",0xf1ad57,"60秒力量I",60,220,"tnc:stone_pattern_leaf","minecraft:iron_nugget","minecraft:sweet_berries"),
    STRIDE("stride","迅步风饮",0x99dfa6,"90秒速度I",90,200,"tnc:dance_petal","minecraft:sugar","minecraft:feather"),
    SHELL("shell","坚肤石蜜",0xb6aa82,"60秒抗性I",60,260,"tnc:stone_pattern_leaf","minecraft:scute","minecraft:honey_bottle"),
    FOCUS("focus","元素共鸣酊",0xa4beff,"45秒内每秒恢复2法力",45,300,"tnc:prism_crown_petal","tnc:storm_crown_tip","tnc:pure_medicinal_powder"),
    CINDER("cinder","避火绯露",0xf28140,"120秒抗火",120,220,"tnc:hearth_pepper_fruit","minecraft:magma_cream","minecraft:nether_wart"),
    TIDE("tide","潮息珍液",0x62b6c8,"120秒水下呼吸",120,220,"tnc:rainletter_berry","minecraft:pufferfish","minecraft:kelp"),
    FEATHER("feather","轻羽浮沫",0xd9edb6,"90秒缓降",90,220,"tnc:dance_petal","minecraft:feather","minecraft:phantom_membrane"),
    CLEANSE("cleanse","净秽清酿",0xc1e8d8,"清除中毒、凋零和饥饿",0,180,"tnc:homeward_flower_petal","minecraft:milk_bucket","minecraft:glow_berries"),
    NIGHT("night","夜航蓝酊",0x677dce,"180秒夜视",180,200,"tnc:shadow_hour_petal","minecraft:golden_carrot","minecraft:brown_mushroom"),
    ECHO("echo","回声显影剂",0x7beee8,"90秒显影隐匣；打开魔力视看清线索",90,260,"tnc:prism_crown_petal","minecraft:echo_shard","tnc:shadow_hour_petal"),
    HEARTH("hearth","归途暖饮",0xeec179,"恢复8饱食及30秒再生I",30,180,"tnc:homeward_flower_petal","minecraft:honey_bottle","minecraft:bread"),
    REVIVE("revive","复苏晨曦",0xf0e4a0,"恢复12生命与90秒伤害吸收I",90,320,"tnc:star_dew_fruit","minecraft:golden_apple","tnc:homeward_flower_petal"),
    OVERDRAFT("overdraft","狂躁透支药水",0xff405c,"产魔+400%两分钟，之后枯死成灰",0,280,"tnc:hearth_pepper_fruit","minecraft:redstone","minecraft:blaze_powder"),
    GIANT("gigantism","巨人化药水",0x98df47,"魔植结巨果，魔兽体型放大三倍",0,280,"tnc:mana_root_core","minecraft:brown_mushroom","minecraft:bone_meal"),
    MINI("miniature","微缩药水",0x8cd7ff,"魔植与魔兽缩小",0,240,"tnc:rainletter_berry","minecraft:fermented_spider_eye","minecraft:sugar"),
    TWIST("flame_twist","属性扭曲药水",0xff7940,"水生魔植50%变为蒸气魔莲，50%烧毁",0,300,"tnc:hearth_pepper_fruit","minecraft:magma_cream","minecraft:amethyst_shard"),
    FISSION("fission","分裂药水",0xa7f576,"成年产魔昆虫/小史莱姆一天分裂一次",0,300,"tnc:honey_powder","minecraft:slime_ball","minecraft:glow_berries"),
    CAPTURE("capture_shrink","缩水捕捉魔药",0x79dde7,"标记25%以下血量杂兵，10秒内用空拘束笼捕获",0,260,"tnc:shadow_hour_petal","minecraft:fermented_spider_eye","minecraft:string"),
    BATH("bath_salt","舒缓浴盐",0xffdf83,"投入供魔地热核心，周围源水排毒一天",0,220,"tnc:homeward_flower_petal","minecraft:prismarine_crystals","minecraft:honey_bottle");
    public final String id,name,help;public final int color,seconds,ticks;public final List<String> ingredients;
    Elixir(String id,String name,int color,String help,int seconds,int ticks,String...ingredients){this.id=id;this.name=name;this.color=color;this.help=help;this.seconds=seconds;this.ticks=ticks;this.ingredients=List.of(ingredients);}
    public boolean agricultural(){return ordinal()>=OVERDRAFT.ordinal();}
    public String outputId(){return agricultural()?(this==BATH?"soothing_bath_salt":id+"_elixir"):"elixir_"+id;}
    public static Elixir find(String id){return Arrays.stream(values()).filter(e->e.id.equals(id)).findFirst().orElse(null);}
    public boolean matches(List<String> input){var expected=new ArrayList<>(ingredients);var actual=new ArrayList<>(input);Collections.sort(expected);Collections.sort(actual);return expected.equals(actual);}
    /** Visual and aroma families are semantic, independent of catalog order. */
    public int effectFamily(){return switch(this){case BLOOM,CLEANSE,HEARTH,REVIVE->0;case SPRING,AWAKEN,FLOW,FOCUS,NIGHT,ECHO->1;case EDGE,SHELL,CINDER->2;default->3;};}
}
