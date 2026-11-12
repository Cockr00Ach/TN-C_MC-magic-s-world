package com.tnc.tnc.warrior.client;

/** View-space travel of the weapon only. The player's camera and aim remain stable. */
public final class WarriorMartialViewMotion {
    public record Frame(double x,double y,double z,float pitch,float yaw,float roll) {}
    private WarriorMartialViewMotion() {}
    public static boolean supports(String clip) {
        if(clip!=null&&(clip.endsWith("_ultimate")||clip.contains("_signature_")))return true;
        return clip!=null && !clip.startsWith("red_tassel_spear_") &&
                (clip.startsWith("yue_bronze_jian_") || clip.startsWith("han_iron_jian_") ||
                 clip.startsWith("willow_leaf_dao_") || clip.startsWith("ring_pommel_dao_") ||
                 clip.startsWith("crescent_guandao_") || clip.startsWith("fangtian_ji_") ||
                 clip.startsWith("iron_jian_mace_") || clip.startsWith("monk_spade_") || clip.startsWith("qimei_staff_"));
    }
    public static Frame sample(WarriorComboAnimations.Clip clip,float t,boolean left) {
        var arm=clip.rotation("armRight",t*clip.lengthSeconds());
        var rest=clip.rotation("armRight",0);
        var root=clip.rotation("bone",t*clip.lengthSeconds());
        float dx=arm[0]-rest[0],dy=arm[1]-rest[1],dz=arm[2]-rest[2];
        float side=(float)Math.tanh((dy+root[1]*.30)/100),sign=left?-1:1;
        return new Frame(sign*side*.38,Math.tanh(-dx/100)*.11,-Math.abs(side)*.12,
                (float)(32*Math.tanh(dx/110)),sign*side*52,(float)(sign*48*Math.tanh(dz/95)));
    }
}
