package com.tnc.tnc.warrior;
/** Linear unbounded damage growth. Visual brightness may saturate; damage does not. */
public final class WeaponChargeRules {
    private WeaponChargeRules(){}
    public static double multiplier(long ticks){return 1+Math.max(0,ticks)/20.0;}
    public record Profile(String name,WarriorAnimation release,double radius,int arc) {}
    /** Weapon identity refines the family default without introducing new packet IDs. */
    public static Profile profile(String id,WarriorWeaponFamily family){
        if(id.equals("doom_scythe"))return new Profile("蓄势·回镰收割",WarriorAnimation.SCYTHE_REAP,3.8,360);
        if(id.equals("qimei_staff"))return new Profile("蓄势·抡棍压顶",WarriorAnimation.SPEAR_RETURN,3.8,200);
        if(id.equals("monk_spade"))return new Profile("蓄势·伏魔推铲",WarriorAnimation.SPEAR_BREAK,4.0,140);
        if(id.equals("crescent_guandao"))return new Profile("蓄势·拖刀断岳",WarriorAnimation.GS_SMASH,3.8,160);
        if(id.equals("fangtian_ji"))return new Profile("蓄势·画戟扫阵",WarriorAnimation.SPEAR_RETURN,4.2,240);
        if(id.equals("willow_leaf_dao")||id.equals("ring_pommel_dao")||id.equals("bronze_dao")||id.equals("frost_katana"))
            return new Profile("蓄势·拔刀横断",WarriorAnimation.SWORD_SPIN,3.6,180);
        if(id.equals("iron_jian_mace"))return new Profile("蓄势·沉锏破甲",WarriorAnimation.HM_CRUSH,3.2,100);
        if(id.equals("bronze_dagger"))return new Profile("蓄势·藏锋突刺",WarriorAnimation.SWORD_BREAKER,3.3,60);
        if(id.equals("bronze_halberd"))return new Profile("蓄势·双刃合斩",WarriorAnimation.SWORD_SPIN,3.6,200);
        return switch(family){
            case LONGSWORD->new Profile("蓄势·立剑劈山",WarriorAnimation.GS_SMASH,3.4,120);
            case GREATSWORD->new Profile("蓄势·巨剑断岳",WarriorAnimation.GS_SMASH,3.8,140);
            case HAMMER->new Profile("蓄势·震地重锤",WarriorAnimation.HM_QUAKE,4.0,360);
            case SPEAR->new Profile("蓄势·拦拿破军",WarriorAnimation.SPEAR_BREAK,4.5,65);
            case BOW->new Profile("蓄势·穿云箭",WarriorAnimation.BOW_AIM,1,20);
        };
    }
    public static boolean chargingAnimation(WarriorAnimation a){return a==WarriorAnimation.SWORD_CHARGE||a==WarriorAnimation.GS_CHARGE||a==WarriorAnimation.HM_CHARGE||a==WarriorAnimation.SPEAR_CHARGE||a==WarriorAnimation.BOW_CHARGE;}
    public static float visualScale(float ticks){return 1+7*Math.min(1,Math.max(0,ticks)/60f);}
    public static WarriorAnimation holdAnimation(WarriorWeaponFamily family){return switch(family){
        case GREATSWORD->WarriorAnimation.GS_CHARGE;case HAMMER->WarriorAnimation.HM_CHARGE;
        case SPEAR->WarriorAnimation.SPEAR_CHARGE;case BOW->WarriorAnimation.BOW_CHARGE;default->WarriorAnimation.SWORD_CHARGE;};}
}
