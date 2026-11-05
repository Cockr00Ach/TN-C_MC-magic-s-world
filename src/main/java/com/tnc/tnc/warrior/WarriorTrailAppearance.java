package com.tnc.tnc.warrior;

/** Weapon identity stays visible in the coloured shell; infusion accents the core. */
public final class WarriorTrailAppearance {
    public record Style(int shell,int core,double thickness,int base) {
        public Style(int shell,int core,double thickness){this(shell,core,thickness,0xF5F7FF);}
    }
    private WarriorTrailAppearance() {}
    public static Style of(String weapon,WarriorWeaponFamily family,String element) {
        int shell=switch(weapon){
            case "yue_bronze_jian"->0xC9AF66;case "willow_leaf_dao"->0xAEC7BC;
            case "crescent_guandao"->0x8AB59C;case "fangtian_ji"->0xE1BF78;case "red_tassel_spear"->0xD66551;
            case "dragon_sword"->0xD45C67;case "frost_katana"->0x62CFFF;case "star_rapier"->0xF1CE74;
            case "crimson_warblade"->0xE86B39;case "doom_scythe"->0xAF6CF0;case "earthshaker_hammer"->0xDCA349;
            case "sky_halberd"->0x43CAD5;case "greatsword"->0x7899D5;case "hammer"->0xC98A50;
            case "spear","wind_relic_spear"->0x4BD5A4;default->0x70B7ED;
        };
        int core=switch(element){
            case "wind"->0xB6FFE3;case "fire"->0xFFD29A;case "water","ice"->0xD3F5FF;
            case "lightning"->0xE2C9FF;case "earth"->0xFFE1AB;case "light"->0xFFF2C2;case "dark"->0xD3AFF4;
            default->mix(shell,0xFFFFFF,.62);
        };
        double thickness=family==WarriorWeaponFamily.HAMMER?.15:family==WarriorWeaponFamily.GREATSWORD?.11:
                family==WarriorWeaponFamily.SPEAR?.06:.075;
        return new Style(shell,core,thickness,weapon.equals("doom_scythe") || element.equals("dark")?0x10101B:0xF5F7FF);
    }
    public static int mix(int a,int b,double t){
        int out=0;for(int shift:new int[]{16,8,0})out|=(int)Math.round((a>>shift&255)*(1-t)+(b>>shift&255)*t)<<shift;return out;
    }
    public static boolean attacking(WarriorAnimation animation,boolean hold,boolean vanillaSwing) {
        if(hold)return false;
        if(animation==null)return vanillaSwing;
        return animation.finisher() || animation==WarriorAnimation.SLASH_LEFT || animation==WarriorAnimation.SLASH_RIGHT
                || animation==WarriorAnimation.SLASH_HORIZONTAL || animation==WarriorAnimation.GUARD_BASH;
    }
    public static float visibility(double attackAge,double sinceAttack,boolean attacking){
        double rise=Math.max(0,Math.min(1,attackAge/35));
        double fall=attacking?1:Math.max(0,Math.min(1,1-sinceAttack/90));
        return (float)(rise*fall*fall);
    }
}
