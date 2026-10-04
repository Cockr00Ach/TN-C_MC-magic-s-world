package com.tnc.tnc.life.botanical;

import java.util.List;

/** The approved field collection. Time is active world ticks, never dayTime. */
public enum BotanicalSpecies {
    DAWN_DISK("dawn_disk","晨晖盘葵","dawn_honey","晨蜜",12,12,1,"露天土壤；经历真实黎明后，用空瓶采晨蜜。"),
    HEARTH_PEPPER("hearth_pepper","炉息椒","hearth_pepper_fruit","暖椒",16,8,2,"根旁两格有正在燃烧的熔炉或点燃营火。"),
    MIST_COTTON("mist_cotton","雾织棉","mist_cotton_fiber","雾棉",16,8,2,"湿润、半阴；真实清晨或雨停后结绒。"),
    STONE_FERN("stone_fern","岩函蕨","stone_pattern_leaf","岩纹叶",16,8,1,"石质表面、阴或半阴；岩纹记录根下表面。"),
    MIRROR_LOTUS("mirror_lotus","镜潭莲","mirror_dew","镜露",16,8,1,"浅静水面，下方泥土；空瓶采露。"),
    WISH_PUFF("wish_puff","灯愿蒲","farlight_fruit","远照果",20,10,2,"湿土，黄昏或夜间成长；果实可投掷照明。"),
    ECHO_BEAN("echo_bean","回声豆","echo_bean_pod","回声豆",12,6,2,"耕地日照；附近音符盒连续敲三音认调。"),
    LADDER_VINE("ladder_vine","梯脊藤","vine_sinew","藤筋",24,6,2,"土上木支架；每六分钟增一段，最多四段。"),
    FROST_CHIME("frost_chime","霜鸣蒿","frost_dew","霜露",16,8,2,"寒冷群系或相邻冰块冷箱；空瓶收露。"),
    SALT_INK("salt_ink","盐墨菌","salt_ink","盐墨",16,8,1,"阴处石或泥，邻近海洋水体或装水盐盆。"),
    WIND_SAIL("wind_sail","风铃帆草","sail_fiber","帆纤维",16,8,2,"露天高坡、相邻支架；雨雷暂停。"),
    SLEEP_CLOCK("sleep_clock","钟眠草","sleep_leaf","静叶",12,12,1,"床旁陰处；成熟后本人真实睡眠返回结叶。"),
    SHADOW_CUT("shadow_cut","影裁花","shadow_silk","影绢",16,8,1,"半阴，上方两格的遮棚决定八种影纹。"),
    PAPER_TREE("paper_tree","墨信树","paper_bark","纸皮",30,15,2,"三格高空间；两格内讲台或持书照料。"),
    FLIGHT_POD("flight_pod","换羽荚","rainproof_pod","防雨荚壳",16,8,2,"湿土；先正常采壳，鹭鸟访株可另寄种。"),
    HONEY_CLUSTER("honey_cluster","共生蜜簇","honey_powder","蜜粉",12,6,2,"土上花簇；三次真实蜂访或授粉刷照料。"),
    STAR_DEW("star_dew","星露藤","star_dew_fruit","星露果",16,8,2,"露天夜间成长；果实16刻吃下回复30真实魔力。"),
    DANCE_BELL("dance_bell","脉舞铃花","dance_petal","舞瓣",12,6,2,"土壤日照；首次浇水认主，主人近八格会舞动。"),
    STAR_REST("star_rest","星憩蕊","star_rest_dew","星憩露",20,20,1,"露天，成熟夜间放三只真实花灵，安全归来结露。" );
    public final String id,name,product,productName,help;
    public final int firstTicks,regrowTicks,count;
    BotanicalSpecies(String id,String name,String product,String productName,int first,int regrow,int count,String help){this.id=id;this.name=name;this.product=product;this.productName=productName;firstTicks=first*1200;regrowTicks=regrow*1200;this.count=count;this.help=help;}
    public boolean eventCrop(){return this==DAWN_DISK||this==MIST_COTTON||this==SLEEP_CLOCK||this==STAR_REST;}
    public boolean bottled(){return this==DAWN_DISK||this==MIRROR_LOTUS||this==FROST_CHIME;}
    public static BotanicalSpecies byId(String id){for(var s:values())if(s.id.equals(id))return s;throw new IllegalArgumentException(id);}
}
