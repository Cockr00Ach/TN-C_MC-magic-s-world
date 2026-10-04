package com.tnc.tnc.life.botanical;

import java.util.List;

/** The approved field collection. Time is active world ticks, never dayTime. */
public enum BotanicalSpecies {
    DAWN_DISK("dawn_disk","晨晖盘葵","dawn_honey","晨蜜",12,12,1,"栽培不挑环境；成株后经历真实黎明，用空瓶采晨蜜。"),
    HEARTH_PEPPER("hearth_pepper","炉息椒","hearth_pepper_fruit","暖椒",16,8,2,"栽培不再需要热源；结出的暖椒用于餐食和暖息制作。"),
    MIST_COTTON("mist_cotton","雾织棉","mist_cotton_fiber","雾棉",16,8,2,"栽培不挑环境；成株后真实清晨或雨停结绒。"),
    STONE_FERN("stone_fern","岩函蕨","stone_pattern_leaf","岩纹叶",16,8,1,"土上也能栽培；根下岩面会改变岩纹记录。"),
    MIRROR_LOTUS("mirror_lotus","镜潭莲","mirror_dew","镜露",16,8,1,"土上或静水面都可栽培；空瓶采镜露。"),
    WISH_PUFF("wish_puff","灯愿蒲","farlight_fruit","远照果",20,10,2,"全天、干土也可栽培；远照果投掷产生临时照明。"),
    ECHO_BEAN("echo_bean","回声豆","echo_bean_pod","回声豆",12,6,2,"不需要先听音才能长成；音符盒三音会记录豆荚音调。"),
    LADDER_VINE("ladder_vine","梯脊藤","vine_sinew","藤筋",24,6,2,"不需要支架；留四格高空间，每六分钟增一段。"),
    FROST_CHIME("frost_chime","霜鸣蒿","frost_dew","霜露",16,8,2,"栽培不需要寒地或冰块；空瓶收两份霜露。"),
    SALT_INK("salt_ink","盐墨菌","salt_ink","盐墨",16,8,1,"栽培不需要海水或盐盆；成熟采盐墨。"),
    WIND_SAIL("wind_sail","风铃帆草","sail_fiber","帆纤维",16,8,2,"低地、室内与雨天都可栽培；收帆纤维。"),
    SLEEP_CLOCK("sleep_clock","钟眠草","sleep_leaf","静叶",12,12,1,"栽培不挑环境；成株后主人在两格内的床真实睡醒结叶。"),
    SHADOW_CUT("shadow_cut","影裁花","shadow_silk","影绢",16,8,1,"栽培不挑环境；上方遮棚仍可改变八种影纹。"),
    PAPER_TREE("paper_tree","墨信树","paper_bark","纸皮",30,15,2,"不需要书或讲台；只要留三格高空间即可生长。"),
    FLIGHT_POD("flight_pod","换羽荚","rainproof_pod","防雨荚壳",16,8,2,"干土也可栽培；采壳后邮羽鹭访株可另寄种。"),
    HONEY_CLUSTER("honey_cluster","共生蜜簇","honey_powder","蜜粉",12,6,2,"栽培不挑环境；长成后三次蜂访或授粉刷结蜜粉。"),
    STAR_DEW("star_dew","星露藤","star_dew_fruit","星露果",16,8,2,"全天与室内都可栽培；果实吃下回复30真实魔力。"),
    DANCE_BELL("dance_bell","脉舞铃花","dance_petal","舞瓣",12,6,2,"全天可栽培；首次浇水认主，主人近八格会舞动。"),
    STAR_REST("star_rest","星憩蕊","star_rest_dew","星憩露",20,20,1,"栽培不挑环境；成熟夜间放三只花灵，归来结露。" );
    public final String id,name,product,productName,help;
    public final int firstTicks,regrowTicks,count;
    BotanicalSpecies(String id,String name,String product,String productName,int first,int regrow,int count,String help){this.id=id;this.name=name;this.product=product;this.productName=productName;firstTicks=first*1200;regrowTicks=regrow*1200;this.count=count;this.help=help;}
    public boolean eventCrop(){return this==DAWN_DISK||this==MIST_COTTON||this==SLEEP_CLOCK||this==STAR_REST;}
    public boolean bottled(){return this==DAWN_DISK||this==MIRROR_LOTUS||this==FROST_CHIME;}
    public static BotanicalSpecies byId(String id){for(var s:values())if(s.id.equals(id))return s;throw new IllegalArgumentException(id);}
}
