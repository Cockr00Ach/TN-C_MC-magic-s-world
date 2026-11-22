package com.tnc.tnc.magic.free;

import com.tnc.tnc.magic.Element;
import java.util.*;

/** Each family contributes once. Names never select an attack or change its element. */
public final class ElementalChants {
    private static final List<String> COMMON=List.of(
        "神圣,神明,神灵,诸神,神祇,神啊,神之,至高,天启,圣洁,圣名,神谕",
        "祝福,赐福,恩赐,庇佑,护佑,眷顾,加护,恩典,降福,蒙福,赐予,垂怜",
        "祈祷,祈求,祈愿,祈请,恳求,祷告,礼赞,颂歌,赞颂,敬献,虔诚,朝拜",
        "回应,聆听,听从,听见,呼唤,召请,应我,听我,降临,显现,觉醒,苏醒",
        "契约,誓约,盟约,誓言,承诺,见证,以我之名,以吾之名,遵从吾命,愿力,意志,灵魂",
        "永恒,无尽,原初,太初,远古,亘古,不朽,星辰,苍穹,天地,命运,奇迹");
    private static final Map<Element,List<String>> ELEMENTS=Map.of(
        Element.FIRE,List.of("火元素,烈焰之主,赤炎君王,火神,炎神,燃烧之心,不熄之焰,赫菲斯托斯,赫斯提亚,普罗米修斯,阿耆尼,苏尔特尔,玛薇卡","熔火意志,红莲,炽日,太阳之火,凤凰,焚尽,燎原,薪火,灼热,烈火,余烬之王,火之灵"),
        Element.WIND,List.of("风元素,风神,风之主,苍风之王,自由之风,巴巴托斯,温迪,埃俄洛斯,玻瑞阿斯,泽菲罗斯,伐由,风伯,飞廉","苍空,天风,云海,长空,四方之风,天空之翼,扶摇,流岚,翱翔,清风,岚之灵,自由意志"),
        Element.EARTH,List.of("土元素,岩元素,大地之母,大地之神,岩神,盖亚,得墨忒耳,摩拉克斯,钟离,普里提维,后土,山岳之主","群山,大地之心,岩之契约,山河,厚土,磐岩之灵,玄岩,地脉,不动之山,万钧,群峰,万岩之王"),
        Element.LIGHTNING,List.of("雷元素,雷神,雷霆之主,九天应元,宙斯,托尔,因陀罗,雷公,建御雷,雷电影,巴尔泽布,雷之灵","万钧雷霆,天罚,苍雷,天威,紫电,霹雳,震天,雷鸣之心,雷之意志,九霄,电光之王,裁决"),
        Element.LIGHT,List.of("光元素,光明之主,光神,阿波罗,赫利俄斯,巴德尔,拉神,苏利耶,晨曦之主,曙光女神,厄俄斯,光之灵","圣光,晨曦,黎明,光明,希望,救赎,慈悲,仁爱,圣洁之心,光辉之翼,明日,太阳之冠"),
        Element.DARK,List.of("暗元素,黑暗之主,暗神,夜之主,倪克斯,厄瑞玻斯,哈迪斯,赫卡忒,阿努比斯,冥王,夜母,暗之灵","幽冥,永夜,深渊,冥河,忘川,幽魂,亡者,夜幕,冥界,无月之夜,深黑之心,影界之王"));
    public static int power(String text,Element element){
        int power=0;for(String family:COMMON)if(matches(text,family))power++;
        for(String family:ELEMENTS.getOrDefault(element,List.of()))if(matches(text,family))power++;
        return Math.min(5,power);
    }
    private static boolean matches(String text,String words){return Arrays.stream(words.split(",")).anyMatch(text::contains);}
    private ElementalChants(){}
}
