package com.tnc.tnc.warrior;
/** Linear unbounded damage growth. Visual brightness may saturate; damage does not. */
public final class WeaponChargeRules {
    private WeaponChargeRules(){}
    public static double multiplier(long ticks){return 1+Math.max(0,ticks)/20.0;}
    public static WarriorAnimation holdAnimation(WarriorWeaponFamily family){return switch(family){
        case GREATSWORD->WarriorAnimation.GS_CHARGE;case HAMMER->WarriorAnimation.HM_CHARGE;
        case SPEAR->WarriorAnimation.SPEAR_CHARGE;case BOW->WarriorAnimation.BOW_CHARGE;default->WarriorAnimation.SWORD_CHARGE;};}
}
