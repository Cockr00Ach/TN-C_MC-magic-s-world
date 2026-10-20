package com.tnc.tnc.dungeon;

import java.util.*;

public final class MobCatalog {
    public enum Family { GOBLIN,SKELETON,SLIME,TENTACLE,ORC,TOAD,LIZARD,LIZARDFOLK,DEMON,MIMIC }
    public enum Role { WARRIOR,CASTER,TANK,ARCHER,LEAPER,AMBUSH }
    public record Species(String id,String name,Family family,Role role,int skin,int accent,float width,float height,double health,double damage,double speed) {}
    private static Species s(String id,String name,Family f,Role r,int skin,int accent,float w,float h,double hp,double dmg,double speed){return new Species(id,name,f,r,skin,accent,w,h,hp,dmg,speed);}
    public static final List<Species> ALL=List.of(
      s("goblin_warrior","哥布林战士",Family.GOBLIN,Role.WARRIOR,0x648c35,0xb97948,.65f,1.4f,28,4,.28),
      s("goblin_mage","哥布林法师",Family.GOBLIN,Role.CASTER,0x719a43,0x3e80c4,.65f,1.45f,23,5,.25),
      s("goblin_bulwark","哥布林重盾手",Family.GOBLIN,Role.TANK,0x557932,0x9f6a3e,.85f,1.6f,48,5,.21),
      s("goblin_archer","哥布林弓手",Family.GOBLIN,Role.ARCHER,0x7b9344,0x8d5143,.65f,1.4f,25,4,.28),
      s("skeleton_sentinel","骷髅卫士",Family.SKELETON,Role.WARRIOR,0xd2c9af,0x758293,.6f,1.99f,30,5,.26),
      s("skeleton_frost","霜骨术士",Family.SKELETON,Role.CASTER,0xaccbd0,0x53a9c8,.6f,1.99f,27,5,.24),
      s("skeleton_warden","古骸盾卫",Family.SKELETON,Role.TANK,0xb5aa8d,0xa18548,.75f,2.15f,52,6,.2),
      s("slime_azure","湛蓝史莱姆",Family.SLIME,Role.LEAPER,0x389bc1,0xa2e9ed,1.1f,1.05f,28,4,.23),
      s("slime_ember","琥珀史莱姆",Family.SLIME,Role.LEAPER,0xcb7737,0xffce6b,1.1f,1.05f,32,5,.24),
      s("slime_venom","苔毒史莱姆",Family.SLIME,Role.LEAPER,0x87aa3b,0xd6e58c,1.1f,1.05f,28,4,.24),
      s("slime_prism","幻彩史莱姆",Family.SLIME,Role.LEAPER,0x7185ce,0xf2badb,1.2f,1.1f,34,5,.25),
      s("tentacle_crawler","裂口触手怪",Family.TENTACLE,Role.WARRIOR,0x9b6581,0xe8c5ac,1.5f,1.4f,40,6,.19),
      s("tentacle_tide","潮痕触手怪",Family.TENTACLE,Role.CASTER,0x387b87,0xebb77e,1.6f,1.5f,38,6,.18),
      s("orc_raider","兽人劫掠者",Family.ORC,Role.WARRIOR,0x7d8b54,0xbb6c3f,1.05f,2.3f,54,7,.27),
      s("orc_ironhide","铁背兽人",Family.ORC,Role.TANK,0x69784c,0x707e89,1.15f,2.45f,80,8,.2),
      s("orc_shaman","兽人萨满",Family.ORC,Role.CASTER,0x8b9566,0x54b5bd,1.0f,2.25f,44,7,.24),
      s("giant_toad","巨沼蛤蟆",Family.TOAD,Role.LEAPER,0x65884f,0xd6ba6b,1.8f,1.3f,50,6,.24),
      s("toad_coral","珊瑚蛤蟆",Family.TOAD,Role.CASTER,0xc88159,0x73c3b2,1.8f,1.3f,46,6,.22),
      s("giant_lizard","岩背巨蜥",Family.LIZARD,Role.LEAPER,0x927845,0xc6ad6b,1.6f,1.2f,58,7,.29),
      s("lizard_ember","赤脊巨蜥",Family.LIZARD,Role.WARRIOR,0xa6553b,0xe3b169,1.7f,1.25f,62,7,.27),
      s("lizardfolk_spear","蜥蜴人矛卫",Family.LIZARDFOLK,Role.WARRIOR,0x477d72,0xc2ad6c,.8f,2.1f,38,6,.28),
      s("lizardfolk_scout","蜥蜴人猎手",Family.LIZARDFOLK,Role.ARCHER,0x658868,0xcc8664,.8f,2.05f,32,5,.3),
      s("lizardfolk_oracle","蜥蜴人潮祭",Family.LIZARDFOLK,Role.CASTER,0x4b819b,0xe2c078,.8f,2.15f,34,6,.25),
      s("demon_imp","烬翼小恶魔",Family.DEMON,Role.CASTER,0x9b534b,0xf2a65d,.7f,1.6f,28,5,.29),
      s("demon_brute","角刃恶魔",Family.DEMON,Role.WARRIOR,0x6c414b,0xc68563,1.05f,2.45f,68,8,.25),
      s("demon_seer","星蚀恶魔",Family.DEMON,Role.CASTER,0x52547c,0x84d8d0,.85f,2.3f,42,7,.24),
      s("mimic_wood","木匣宝箱怪",Family.MIMIC,Role.AMBUSH,0x93602f,0xbab191,1.1f,1.15f,44,7,.25),
      s("mimic_iron","铁匣宝箱怪",Family.MIMIC,Role.AMBUSH,0x667780,0xc5aa68,1.2f,1.25f,62,8,.22));
    public static Species find(String id){return ALL.stream().filter(s->s.id.equals(id)).findFirst().orElse(ALL.get(0));}
    public static List<Species> pool(DungeonTheme theme){return ALL.stream().filter(s->switch(theme){
        case DESERT->Set.of(Family.GOBLIN,Family.ORC,Family.LIZARD,Family.LIZARDFOLK,Family.SLIME,Family.MIMIC).contains(s.family)&&!s.id.equals("slime_azure");
        case CRYPT->Set.of(Family.SKELETON,Family.TENTACLE,Family.DEMON,Family.MIMIC,Family.ORC).contains(s.family);
        case TIDE->Set.of(Family.TENTACLE,Family.TOAD,Family.LIZARDFOLK,Family.SLIME,Family.MIMIC).contains(s.family)&&!s.id.equals("slime_ember");
        case ASTRAL->Set.of(Family.GOBLIN,Family.DEMON,Family.SLIME,Family.MIMIC,Family.TOAD).contains(s.family);
    }).toList();}
}
