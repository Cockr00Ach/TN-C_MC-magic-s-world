package com.tnc.tnc.magic.free;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

/** Shared vocabulary and physical/visual budgets for the twenty authored water shapes. */
public final class FreeMagicIntent {
    public enum Behavior { BOLT, PIERCE, EXPLODE, RETURN, SWEEP, WAVE, COLUMN, VORTEX, SEEK, PRISON, NET }
    public enum Shape {
        BALL("水弹",1,8,5,.55,.30,1.70,24,100,Behavior.BOLT),
        BLADE("水刃",1,10,6,.62,.42,1.55,26,100,Behavior.PIERCE),
        SPEAR("水枪",1,12,8,.68,.32,1.65,28,110,Behavior.PIERCE),
        CANNON("水炮",2,24,13,.42,.65,1.80,34,110,Behavior.EXPLODE),
        SWORD("水剑",2,14,9,.55,.42,1.55,28,100,Behavior.PIERCE),
        ARROW("水箭",1,9,6,.80,.24,1.65,24,90,Behavior.BOLT),
        DISC("水轮",2,16,7,.55,.65,1.55,28,100,Behavior.RETURN),
        SHURIKEN("水镖",1,10,5,.70,.40,1.65,24,90,Behavior.BOLT),
        AXE("水斧",2,20,11,.45,.68,1.60,30,100,Behavior.EXPLODE),
        HAMMER("水锤",2,26,14,.36,.80,1.70,34,110,Behavior.EXPLODE),
        WHIP("水鞭",2,18,7,.32,1.10,1.30,30,40,Behavior.SWEEP),
        TENTACLE("水触手",2,22,6,.28,1.00,1.30,32,48,Behavior.SWEEP),
        DRILL("水钻",3,26,9,.50,.58,1.50,34,110,Behavior.PIERCE),
        WAVE("海浪",2,24,7,.30,1.35,1.20,34,90,Behavior.WAVE),
        TSUNAMI("巨浪",3,45,11,.24,2.00,1.20,40,110,Behavior.WAVE),
        GEYSER("水柱",2,24,10,0,1.35,1.20,34,36,Behavior.COLUMN),
        VORTEX("水龙卷",3,40,5,0,1.90,1.20,40,70,Behavior.VORTEX),
        DRAGON("水龙",3,42,12,.40,.85,1.35,42,120,Behavior.SEEK),
        BUBBLE("水泡囚笼",2,22,4,.38,.72,1.50,30,100,Behavior.PRISON),
        NET("水网",2,24,5,.34,1.05,1.40,32,100,Behavior.NET);
        public final String label;
        public final int tier, mana, chargeTicks, lifetime;
        public final float damage;
        public final double speed, radius, scale;
        public final Behavior behavior;
        Shape(String label,int tier,int mana,float damage,double speed,double radius,double scale,
              int chargeTicks,int lifetime,Behavior behavior){
            this.label=label;this.tier=tier;this.mana=mana;this.damage=damage;this.speed=speed;
            this.radius=radius;this.scale=scale;this.chargeTicks=chargeTicks;this.lifetime=lifetime;this.behavior=behavior;
        }
        public boolean large(){return behavior==Behavior.WAVE||behavior==Behavior.COLUMN||behavior==Behavior.VORTEX||behavior==Behavior.SEEK;}
        public boolean stationary(){return behavior==Behavior.COLUMN||behavior==Behavior.VORTEX;}
    }
    public static final int GIANT=1, FAST=2, EMPOWER=4, DURABLE=8, SHOCK=16;
    public enum Mode { SINGLE, REPEAT, VOLLEY, REPEAT_VOLLEY }
    public record Intent(Shape shape, Mode mode,int modifiers,int chantPower) {
        public Intent { java.util.Objects.requireNonNull(shape);java.util.Objects.requireNonNull(mode);modifiers&=31;chantPower=Math.max(0,Math.min(5,chantPower)); }
        public Intent(Shape shape,Mode mode){this(shape,mode,0,0);}
        public boolean has(int flag){return (modifiers&flag)!=0;}
        public int requiredTier(){return Math.max(shape.tier,Math.max(modifiers!=0?2:1,mode==Mode.VOLLEY||mode==Mode.REPEAT_VOLLEY?3:mode==Mode.REPEAT?2:1));}
        public int formationCount(int tier){return mode==Mode.REPEAT_VOLLEY?(shape.large()?2:3):mode==Mode.VOLLEY?Math.min(shape.large()?3:5,2+Math.max(1,tier)):1;}
        public int waves(int tier){return mode==Mode.REPEAT_VOLLEY?(shape.large()?2:3):mode==Mode.REPEAT?3:1;}
        public int count(int tier){return formationCount(tier)*waves(tier);}
        public int waveInterval(){return has(FAST)?10:14;}
        public int chargeTicks(){return (int)Math.ceil(shape.chargeTicks*(has(EMPOWER)?3:1));}
        public int costPerProjectile(){int percent=100+(has(GIANT)?60:0)+(has(FAST)?35:0)+(has(EMPOWER)?65:0)+(has(DURABLE)?30:0)+(has(SHOCK)?30:0);return (shape.mana*percent+99)/100;}
        public int costPerWave(int tier){return costPerProjectile()*formationCount(tier);}
        public double scale(){return shape.scale*(has(GIANT)?3.5:1);}
        public double radius(){return shape.radius*(has(GIANT)?3.5:1)*(has(SHOCK)?1.2:1);}
        public double speed(){return shape.speed*(has(FAST)?3.2:1);}
        public float damage(){return (float)(shape.damage*(has(GIANT)?1.1:1)*(has(EMPOWER)?2.4:1)*(1+chantPower*.08));}
        public int lifetime(){return (int)Math.ceil(shape.lifetime*(has(DURABLE)?3:1));}
        public double knockback(){return has(SHOCK)?4.2:1;}
        public double fieldRange(){return shape.behavior==Behavior.COLUMN?18:24;}
        public int pierceLimit(){return 3;}
        public int restraintTicks(int base){return (int)Math.ceil(base*(has(DURABLE)?3:1));}
        public String description(){
            var s=new StringBuilder();
            if(has(GIANT))s.append("巨大·");if(has(FAST))s.append("急速·");if(has(EMPOWER))s.append("强化·");
            if(has(DURABLE))s.append("持久·");if(has(SHOCK))s.append("震荡·");
            if(mode==Mode.REPEAT||mode==Mode.REPEAT_VOLLEY)s.append("连续·");
            if(mode==Mode.VOLLEY||mode==Mode.REPEAT_VOLLEY)s.append("齐发·");
            s.append(shape.label);if(chantPower>0)s.append("（吟唱 +").append(chantPower*8).append("%）");return s.toString();
        }
    }
    private static final Shape[] LONGEST=Arrays.stream(Shape.values())
            .sorted(Comparator.comparingInt((Shape s)->s.label.length()).reversed()).toArray(Shape[]::new);
    public static String normalize(String spoken){return WaterSpeech.normalize(clean(spoken));}
    /** Acoustic keyword spotting only repairs a missing shape, never overrides a recognized one. */
    public static String resolve(String spoken,String shapeHint){
        String normal=normalize(spoken);
        if(parse(normal).isPresent())return normal;
        if(shapeHint!=null)for(Shape shape:Shape.values())if(shape.label.equals(shapeHint)){
            int room=127-shape.label.length();return clean(normal.length()>room?normal.substring(0,room):normal)+"，"+shape.label;
        }
        return normal;
    }
    public static Optional<Intent> parse(String spoken){
        if(spoken==null||spoken.length()>128)return Optional.empty();
        String text=normalize(spoken).replaceAll("[\\s\\p{P}]", "");
        text=text.replace("换枪齐发","水枪齐发").replace("万枪","水枪").replace("万剑","水剑").replace("万箭","水箭");
        Shape found=null;
        for(int at=0;at<text.length();){
            Shape next=null;
            for(Shape shape:LONGEST)if(text.startsWith(shape.label,at)){next=shape;break;}
            if(next==null){at++;continue;}
            found=next;at+=next.label.length();
        }
        if(found==null)return Optional.empty();
        boolean volley=text.contains("齐发")||text.contains("齐射");
        boolean repeat=text.contains("连发")||text.contains("连续");
        int mods=0;
        if(containsAny(text,"巨大","巨型","巨化","超大"))mods|=GIANT;
        if(containsAny(text,"急速","极速","高速","迅疾"))mods|=FAST;
        if(containsAny(text,"强化","强力","强效","蓄力"))mods|=EMPOWER;
        if(containsAny(text,"持久","延长","持續","持续"))mods|=DURABLE;
        if(containsAny(text,"震荡","冲击","震波"))mods|=SHOCK;
        int chant=ChantLexicon.power(text);
        return Optional.of(new Intent(found,volley&&repeat?Mode.REPEAT_VOLLEY:volley?Mode.VOLLEY:repeat?Mode.REPEAT:Mode.SINGLE,mods,chant));
    }
    private static boolean containsAny(String text,String... words){for(String word:words)if(text.contains(word))return true;return false;}
    public static String clean(String text){
        if(text==null)return "";
        String result=text.codePoints().filter(c->!Character.isISOControl(c)&&c!=0xA7&&Character.getType(c)!=Character.FORMAT)
                .limit(128).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString();
        return result.length()>128?result.substring(0,128):result;
    }
    private FreeMagicIntent(){}
}
