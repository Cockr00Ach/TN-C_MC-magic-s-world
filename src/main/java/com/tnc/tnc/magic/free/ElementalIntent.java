package com.tnc.tnc.magic.free;

import com.tnc.tnc.magic.Element;
import java.util.*;

/** Element-specific actions: shared modifiers do not force shared attack behavior. */
public final class ElementalIntent {
    public static final int GIANT=1,FAST=2,EMPOWER=4,DURABLE=8,SHOCK=16,REPEAT=32,VOLLEY=64;
    public enum Target { PROJECTILE, GROUND, ENEMY, SELF, CHANNEL, FRIEND, AIR, CHANNEL_SELF, CHANNEL_ENEMY }
    public enum Ability {
        FIREBALL("火球",Target.PROJECTILE,1,12,8,30,100,127,"炎球","塑形火球"),
        FIREBLADE("火刃",Target.PROJECTILE,1,14,7,28,90,127,"炎刃","塑形火刃"),
        FIRE_SERPENT("火蛇",Target.PROJECTILE,2,22,9,34,100,127,"焰蛇"),
        FIRE_COVER("火焰覆盖",Target.GROUND,2,28,4,32,120,127,"铺火","焚地"),
        IGNITE("点燃",Target.ENEMY,1,10,3,12,100,EMPOWER|DURABLE|VOLLEY,"燃烧起来"),
        MAGMA_SPRAY("岩浆喷射",Target.CHANNEL,3,20,6,30,100,GIANT|FAST|EMPOWER|DURABLE|SHOCK,"熔浆喷射"),
        VIOLET_FIRE("紫火",Target.ENEMY,2,22,4,24,160,EMPOWER|DURABLE|VOLLEY,"幽焰"),
        FIRE_BREATH("持续喷火",Target.CHANNEL,2,14,4,24,100,GIANT|FAST|EMPOWER|DURABLE|SHOCK,"喷火","吐息"),
        FIRE_WALL("火墙",Target.GROUND,2,28,4,34,140,GIANT|EMPOWER|DURABLE|SHOCK,"焰墙"),
        FIRE_RING("火环",Target.SELF,2,24,6,26,30,GIANT|FAST|EMPOWER|DURABLE|SHOCK|REPEAT,"炎环"),
        FLASH_FIRE("爆燃",Target.GROUND,2,24,12,30,16,GIANT|EMPOWER|SHOCK|REPEAT|VOLLEY,"火爆"),
        DETONATE_BURN("燃爆",Target.ENEMY,2,18,7,24,16,GIANT|EMPOWER|SHOCK,"引爆灼痕"),
        FIRE_RAIN("火雨",Target.GROUND,3,38,5,40,140,GIANT|FAST|EMPOWER|DURABLE|SHOCK,"流火"),
        FLAME_COLUMN("炎柱",Target.GROUND,2,28,12,34,30,GIANT|EMPOWER|DURABLE|SHOCK|REPEAT|VOLLEY,"地火喷发"),
        LAVA_VENT("火山口",Target.GROUND,3,36,5,40,160,GIANT|FAST|EMPOWER|DURABLE|SHOCK,"熔火泉"),
        SPREAD_FIRE("引火",Target.ENEMY,2,16,3,18,60,GIANT|EMPOWER|DURABLE,"火焰蔓延"),
        FLAME_CLOAK("火焰附身",Target.SELF,2,24,4,24,160,EMPOWER|DURABLE,"焰衣"),
        HEAT_WAVE("热浪",Target.PROJECTILE,2,22,4,30,40,GIANT|FAST|EMPOWER|DURABLE|SHOCK|REPEAT,"灼风"),
        EMBER_MARK("余烬印",Target.ENEMY,2,22,12,24,60,GIANT|EMPOWER|SHOCK|REPEAT|VOLLEY,"烬印"),
        KINDLE("不灭火种",Target.GROUND,3,30,3,34,180,GIANT|EMPOWER|DURABLE,"续燃"),
        WIND_BALL(Element.WIND,"风球",Target.PROJECTILE,1,12,7,30,90,127,"气弹"),
        WIND_BLADE(Element.WIND,"风刃",Target.PROJECTILE,1,14,8,28,90,127,"气刃"),
        TAKEOFF(Element.WIND,"起飞",Target.SELF,1,18,0,18,50,12,"腾空"),
        UPDRAFT(Element.WIND,"升龙",Target.AIR,2,24,5,28,45,29,"升龙风"),
        GATHER(Element.WIND,"聚风",Target.GROUND,2,28,1,32,100,13,"聚怪", "吸过来"),
        WIND_STEP(Element.WIND,"风步",Target.SELF,1,12,0,12,12,6,"位移", "疾风步"),
        LAUNCH(Element.WIND,"击飞",Target.AIR,2,20,4,24,18,53,"吹飞"),
        WIND_PRESSURE(Element.WIND,"风压",Target.PROJECTILE,2,24,6,34,60,119,"空气炮"),
        GLIDE(Element.WIND,"滑翔",Target.CHANNEL_SELF,2,12,0,16,160,12,"御风滑行"),
        AIR_JUMP(Element.WIND,"跃风",Target.SELF,1,10,0,8,12,4,"二段跳"),
        TAILWIND(Element.WIND,"顺风",Target.GROUND,1,16,0,22,120,13,"风行"),
        HEADWIND(Element.WIND,"逆风",Target.GROUND,2,22,1,28,120,29,"阻风"),
        WIND_WALL(Element.WIND,"风墙",Target.GROUND,2,26,0,30,120,13,"偏风屏"),
        TORNADO(Element.WIND,"龙卷",Target.GROUND,3,38,3,40,120,29,"龙卷风"),
        VACUUM(Element.WIND,"真空",Target.AIR,2,28,14,36,45,53,"抽空"),
        WIND_CORRIDOR(Element.WIND,"风切走廊",Target.GROUND,2,28,5,30,100,15,"风切", "切风道"),
        RETURN_WIND(Element.WIND,"回风",Target.PROJECTILE,2,20,7,30,100,127,"折返风"),
        WIND_LIFT(Element.WIND,"风托",Target.FRIEND,1,14,0,18,50,12,"托举"),
        DOWNDRAFT(Element.WIND,"下压",Target.ENEMY,2,20,10,24,60,84,"坠风"),
        AIR_BURST(Element.WIND,"空爆",Target.AIR,2,24,10,30,18,117,"爆风"),
        ROCK_BALL(Element.EARTH,"岩球",Target.PROJECTILE,1,16,12,38,120,127,"石球"),
        ROCK_BLADE(Element.EARTH,"岩刃",Target.PROJECTILE,1,16,10,34,100,127,"石刃"),
        ROCK_RISE(Element.EARTH,"岩突",Target.SELF,2,28,0,32,200,13,"托岩", "升岩柱"),
        ROCK_CRUSH(Element.EARTH,"岩合",Target.ENEMY,2,28,16,40,35,85,"岩石挤压", "挤压"),
        ROCK_ARRAY(Element.EARTH,"岩之阵",Target.GROUND,2,24,2,32,160,13,"岩阵"),
        EARTH_SPIKE(Element.EARTH,"地刺",Target.GROUND,2,24,9,34,60,119,"岩刺"),
        ROCK_WALL(Element.EARTH,"岩墙",Target.GROUND,2,26,0,36,200,13,"石墙"),
        STONE_STEPS(Element.EARTH,"石阶",Target.SELF,1,18,0,26,200,12,"岩阶"),
        ROCK_BRIDGE(Element.EARTH,"岩桥",Target.SELF,2,30,0,40,240,12,"架桥"),
        FISSURE(Element.EARTH,"地裂",Target.GROUND,2,28,10,36,60,119,"裂地"),
        QUAKE(Element.EARTH,"地震",Target.SELF,3,36,10,42,65,61,"震地"),
        STONE_ARMOR(Element.EARTH,"岩甲",Target.SELF,2,22,0,28,160,12,"石肤"),
        ROOTED(Element.EARTH,"磐石",Target.SELF,2,16,0,24,120,12,"扎根"),
        QUICKSAND(Element.EARTH,"流沙",Target.GROUND,2,24,0,32,140,13,"陷沙"),
        ROCK_GRASP(Element.EARTH,"岩握",Target.ENEMY,2,22,5,28,80,12,"地之手"),
        FALLING_ROCK(Element.EARTH,"落岩",Target.GROUND,3,32,20,44,65,85,"陨岩"),
        GRAVEL_FLOW(Element.EARTH,"碎石流",Target.GROUND,2,28,5,34,100,31,"崩石"),
        STONE_ARCH(Element.EARTH,"岩拱",Target.SELF,2,26,0,36,200,12,"石穹"),
        EARTH_SENSE(Element.EARTH,"地脉感知",Target.SELF,1,12,0,20,120,9,"听地"),
        SHATTER_ROCK(Element.EARTH,"崩岩",Target.AIR,2,16,14,24,20,21,"岩构爆破"),
        THUNDER_BALL(Element.LIGHTNING,"雷球",Target.PROJECTILE,1,14,8,26,90,127,"电球"),
        THUNDER_BLADE(Element.LIGHTNING,"雷刃",Target.PROJECTILE,1,14,8,24,90,127,"电刃"),
        THUNDER_SPEED(Element.LIGHTNING,"雷之加速",Target.SELF,1,16,0,16,140,12,"加速", "雷速"),
        PARALYZE(Element.LIGHTNING,"麻痹",Target.ENEMY,2,20,2,22,45,76,"电麻"),
        THUNDER_STRIKE(Element.LIGHTNING,"雷击",Target.ENEMY,1,16,10,28,25,117,"落雷"),
        NINE_THUNDERS(Element.LIGHTNING,"九重雷",Target.ENEMY,3,48,6,45,180,23,"九重雷击"),
        CHAIN_LIGHTNING(Element.LIGHTNING,"连锁雷电",Target.ENEMY,2,28,8,30,24,5,"连锁"),
        CONDUCTIVE(Element.LIGHTNING,"导电印",Target.ENEMY,1,10,1,16,160,76,"导电"),
        THUNDER_DASH(Element.LIGHTNING,"雷闪",Target.SELF,2,18,0,12,12,6,"电闪步"),
        THUNDER_RETURN(Element.LIGHTNING,"雷返",Target.SELF,2,20,0,18,160,12,"回雷"),
        THUNDERCLAP(Element.LIGHTNING,"雷鸣",Target.SELF,2,24,7,26,20,53,"轰雷"),
        OVERLOAD(Element.LIGHTNING,"过载",Target.ENEMY,2,22,16,30,20,21,"电能过载"),
        STORE_CHARGE(Element.LIGHTNING,"蓄雷",Target.SELF,2,18,0,30,200,12,"积电"),
        THUNDER_STORM(Element.LIGHTNING,"雷暴",Target.GROUND,3,40,7,44,180,15,"雷云"),
        ELECTRIC_BIND(Element.LIGHTNING,"电链束缚",Target.ENEMY,2,26,4,30,100,12,"电链"),
        ELECTRIC_NET(Element.LIGHTNING,"雷网地带",Target.GROUND,2,28,2,32,140,13,"雷网", "带电地面"),
        THUNDER_ARMOR(Element.LIGHTNING,"雷甲",Target.SELF,2,24,5,28,160,12,"反击电衣"),
        MAGNETIC_PULL(Element.LIGHTNING,"磁引",Target.GROUND,2,24,1,28,80,13,"电磁牵引"),
        ELECTRIC_PULSE(Element.LIGHTNING,"电能脉冲",Target.SELF,2,26,7,30,40,53,"电涌"),
        THUNDER_PIERCE(Element.LIGHTNING,"雷霆贯通",Target.AIR,3,32,14,38,18,117,"贯雷"),
        LIGHT_BALL(Element.LIGHT,"光球",Target.PROJECTILE,1,12,8,28,100,127,"辉球"),
        LIGHT_BLADE(Element.LIGHT,"光刃",Target.PROJECTILE,1,14,8,28,100,127,"辉刃"),
        LASER(Element.LIGHT,"激光",Target.CHANNEL,2,16,5,34,140,31,"光束"),
        PRISM(Element.LIGHT,"棱镜折射",Target.CHANNEL,3,24,4,42,120,31,"棱镜", "折射"),
        FOCUS_LIGHT(Element.LIGHT,"聚光",Target.AIR,2,24,18,50,18,117,"聚焦"),
        PURIFY(Element.LIGHT,"净化",Target.FRIEND,1,16,0,22,18,4,"驱散污秽"),
        HEAL(Element.LIGHT,"治愈",Target.FRIEND,1,18,6,30,18,4,"治疗"),
        HEAL_FIELD(Element.LIGHT,"回春光域",Target.GROUND,2,30,2,36,160,13,"回春", "疗愈光域"),
        LIGHT_BOON(Element.LIGHT,"赐光",Target.FRIEND,2,22,0,28,160,12,"光之增益"),
        LIGHT_SHIELD(Element.LIGHT,"光盾",Target.FRIEND,2,22,8,28,160,12,"辉盾"),
        GUARD_LINK(Element.LIGHT,"守护链接",Target.FRIEND,2,26,12,32,160,12,"光之链接"),
        REVEAL(Element.LIGHT,"曙光显形",Target.SELF,1,14,0,24,100,9,"显形", "曙光"),
        FLASH(Element.LIGHT,"闪光",Target.AIR,2,20,2,24,40,21,"耀目"),
        SUN_MARK(Element.LIGHT,"日印",Target.ENEMY,2,16,6,24,160,76,"曦印"),
        EXORCISE(Element.LIGHT,"驱邪",Target.AIR,2,24,6,30,24,117,"退灵"),
        LIGHT_CURTAIN(Element.LIGHT,"光幕",Target.GROUND,2,26,0,32,120,13,"护光幕"),
        LIGHT_PATH(Element.LIGHT,"祝福之径",Target.GROUND,2,24,4,30,140,13,"光路"),
        SANCTUARY(Element.LIGHT,"圣域",Target.GROUND,3,34,0,40,180,13,"光之圣域"),
        DAYLIGHT(Element.LIGHT,"极昼",Target.CHANNEL_SELF,2,14,0,26,180,9,"昼光"),
        REFLECT_LIGHT(Element.LIGHT,"反照",Target.SELF,3,28,0,32,120,12,"返光"),
        DARK_BALL(Element.DARK,"暗球",Target.PROJECTILE,1,14,8,30,100,127,"影球"),
        DARK_BLADE(Element.DARK,"暗刃",Target.PROJECTILE,1,14,8,28,90,127,"影刃"),
        SUMMON_WRAITH(Element.DARK,"召唤恶灵",Target.AIR,2,28,5,40,240,14,"恶灵", "唤灵"),
        SPIRIT_SWARM(Element.DARK,"灵群",Target.GROUND,3,40,2,44,180,14,"群灵"),
        COMMAND_SPIRIT(Element.DARK,"役灵",Target.ENEMY,1,0,0,10,12,0,"恶灵听令"),
        SHADOW_BIND(Element.DARK,"缚影",Target.ENEMY,2,20,1,26,60,12,"影缚"),
        BLIND(Element.DARK,"失明",Target.ENEMY,1,16,0,22,70,76,"遮目"),
        WEAKNESS_CURSE(Element.DARK,"虚弱咒",Target.ENEMY,1,18,0,24,140,76,"虚弱", "衰弱"),
        SLOW_CURSE(Element.DARK,"迟滞咒",Target.ENEMY,1,18,0,24,140,76,"迟滞", "迟缓诅咒"),
        EROSION(Element.DARK,"侵蚀",Target.ENEMY,2,18,2,26,160,76,"腐影"),
        DEVOUR_CURSE(Element.DARK,"噬咒",Target.ENEMY,2,22,8,30,20,21,"引爆诅咒"),
        SHADOW_HIDE(Element.DARK,"影遁",Target.SELF,2,22,0,24,100,12,"遁影"),
        SHADOW_SPIKE(Element.DARK,"影刺",Target.ENEMY,2,22,12,34,32,117,"刺影"),
        SHADOW_ASSAULT(Element.DARK,"影袭",Target.ENEMY,2,24,12,28,20,4,"袭影"),
        SHADOW_SWAP(Element.DARK,"换影",Target.SELF,2,20,0,24,160,12,"移形换影"),
        FEAR(Element.DARK,"恐惧",Target.ENEMY,2,22,0,28,60,76,"惊魂"),
        DARK_RITUAL(Element.DARK,"黑暗仪式",Target.GROUND,3,36,0,48,200,13,"仪式", "唤魂阵"),
        SACRIFICE(Element.DARK,"献祭",Target.SELF,2,6,0,28,180,4,"献祭恶灵"),
        BLOOD_SACRIFICE(Element.DARK,"血祭",Target.SELF,2,6,0,30,120,4,"以血为祭"),
        SOUL_DRAIN(Element.DARK,"灵魂汲取",Target.CHANNEL_ENEMY,3,18,4,36,120,14,"汲取"),
        MISFORTUNE(Element.DARK,"厄运",Target.ENEMY,2,18,7,26,160,76,"厄运咒"),
        HEAL_LOCK(Element.DARK,"禁疗",Target.ENEMY,2,20,0,26,100,76,"断愈"),
        SILENCE(Element.DARK,"噤声",Target.ENEMY,3,26,0,32,45,76,"沉默咒"),
        PAIN_LINK(Element.DARK,"痛苦链接",Target.ENEMY,3,30,0,38,120,12,"共痛"),
        DELAYED_CURSE(Element.DARK,"迟发咒印",Target.ENEMY,2,24,16,32,70,85,"咒爆印"),
        ARMOR_EROSION(Element.DARK,"蚀甲",Target.ENEMY,2,22,0,28,140,76,"腐甲"),
        EXPOSURE(Element.DARK,"暴露咒",Target.ENEMY,2,22,0,28,120,76,"弱点咒"),
        WEARINESS(Element.DARK,"疲敝咒",Target.ENEMY,2,20,0,26,180,76,"疲敝", "枯竭咒");

        public final String label;
        private final Element element;
        public final Target target;
        public final int tier,mana,charge,duration,supported;
        public final float damage;
        public final List<String> aliases;
        Ability(String label,Target target,int tier,int mana,float damage,int charge,int duration,int supported,String... aliases){
            this(Element.FIRE,label,target,tier,mana,damage,charge,duration,supported,aliases);
        }
        Ability(Element element,String label,Target target,int tier,int mana,float damage,int charge,int duration,int supported,String... aliases){
            this.element=element;this.label=label;this.target=target;this.tier=tier;this.mana=mana;this.damage=damage;
            this.charge=charge;this.duration=duration;this.supported=supported;
            var words=new ArrayList<String>();words.add(label);words.addAll(List.of(aliases));this.aliases=List.copyOf(words);
        }
        public Element element(){return element;}
        public boolean channel(){return target==Target.CHANNEL||target==Target.CHANNEL_SELF||target==Target.CHANNEL_ENEMY;}
    }
    public record Intent(Ability ability,int flags,int ignored,int chantPower){
        public Intent {Objects.requireNonNull(ability);ignored|=flags&~ability.supported;flags&=ability.supported;chantPower=Math.max(0,Math.min(5,chantPower));}
        public boolean has(int flag){return (flags&flag)!=0;}
        public int tier(){return Math.max(ability.tier,has(VOLLEY)?3:flags!=0?2:1);}
        public int charge(){return ability.charge*(has(EMPOWER)?3:1);}
        public int duration(){return ability.duration*(has(DURABLE)?3:1);}
        public int cost(){return (ability.mana*(100+(has(GIANT)?60:0)+(has(FAST)?35:0)+(has(EMPOWER)?65:0)+(has(DURABLE)?30:0)+(has(SHOCK)?30:0))+99)/100;}
        public int count(){return has(VOLLEY)?3:1;}
        public int waves(){return has(REPEAT)?3:1;}
        public int interval(){return has(FAST)?10:18;}
        public double scale(){return has(GIANT)?3.2:1;}
        public double speed(){return has(FAST)?3.2:1;}
        public float power(){return (float)((has(EMPOWER)?2.4:1)*(1+chantPower*.08));}
        public float damage(){return ability.damage*power();}
        public String description(){
            String s=labels(flags)+ability.label+(chantPower>0?"（吟唱 +"+chantPower*8+"%）":"");
            return ignored==0?s:s+"；不适用且不加价："+labels(ignored);
        }
    }
    private record Word(Ability ability,String text){}
    private static final List<Word> WORDS=Arrays.stream(Ability.values()).flatMap(a->a.aliases.stream().map(w->new Word(a,w)))
            .sorted(Comparator.comparingInt((Word w)->w.text.length()).reversed()).toList();
    public static Optional<Intent> parse(String raw,Element context){
        if(raw==null||raw.length()>128)return Optional.empty();
        String text=FreeMagicIntent.clean(raw).replaceAll("[\\s\\p{P}]","");
        for(Word word:WORDS)if(explicit(word.text)&&text.contains(word.text)){context=word.ability.element();break;}
        text=ElementalSpeech.normalize(text,context);
        Word found=null;int ending=-1;
        for(Word word:WORDS){
            // Unprefixed aliases are interpreted only in this element's context.
            if(context!=word.ability.element()&&!explicit(word.text))continue;
            int at=text.lastIndexOf(word.text),end=at+word.text.length();
            if(at>=0&&(end>ending||end==ending&&(found==null||word.text.length()>found.text.length()))){found=word;ending=end;}
        }
        if(found==null)return Optional.empty();
        // Remove the matched main phrase before parsing modifiers (持续喷火 is a name).
        String modifiers=text.substring(0,ending-found.text.length())+text.substring(ending);
        int flags=0;
        if(any(modifiers,"巨大","巨型","巨化","超大"))flags|=GIANT;
        if(any(modifiers,"急速","极速","高速","迅疾"))flags|=FAST;
        if(any(modifiers,"强化","强力","强效","蓄力"))flags|=EMPOWER;
        if(any(modifiers,"持久","延长","持续"))flags|=DURABLE;
        if(any(modifiers,"震荡","冲击","震波"))flags|=SHOCK;
        if(any(modifiers,"连续","连发"))flags|=REPEAT;
        if(any(modifiers,"齐发","齐射"))flags|=VOLLEY;
        return Optional.of(new Intent(found.ability,flags,0,ElementalChants.power(modifiers,found.ability.element())));
    }
    static boolean explicit(String word){return any(word,"火","焰","炎","熔","岩浆","灼","余烬","雷","电","风","岩","石","地脉","光","辉","暗","影","灵","咒","诅","血祭","圣域");}
    private static boolean any(String text,String... words){for(String w:words)if(text.contains(w))return true;return false;}
    static String labels(int flags){StringBuilder b=new StringBuilder();int[] bits={GIANT,FAST,EMPOWER,DURABLE,SHOCK,REPEAT,VOLLEY};String[] words={"巨大","急速","强化","持久","震荡","连续","齐发"};for(int i=0;i<bits.length;i++)if((flags&bits[i])!=0)b.append(words[i]).append('·');return b.toString();}
    private ElementalIntent(){}
}
