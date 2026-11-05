package com.tnc.tnc.warrior;

/** Movement and animation identity; input chains, damage, unlocks and ammunition stay in the catalog. */
public final class WarriorPerformance {
    private WarriorPerformance() {}
    public static WarriorSkill catalog(WarriorSkill s) {
        WarriorAnimation animation = switch(s.id()) {
            case "sword_combo_5" -> WarriorAnimation.SWORD_RETREAT;
            case "sword_combo_6" -> WarriorAnimation.SWORD_SWALLOW;
            case "greatsword_combo_5" -> WarriorAnimation.GS_RIFT;
            case "greatsword_combo_6" -> WarriorAnimation.GS_ADVANCE;
            case "hammer_combo_5" -> WarriorAnimation.HM_RUIN;
            case "hammer_combo_6" -> WarriorAnimation.HM_DRIVE;
            case "bow_combo_4" -> WarriorAnimation.BOW_PIERCE;
            case "bow_combo_5" -> WarriorAnimation.BOW_STRAFE;
            case "bow_combo_6" -> WarriorAnimation.BOW_VOLLEY;
            case "spear_combo_1" -> WarriorAnimation.SPEAR_BREAK;
            case "spear_combo_2" -> WarriorAnimation.SPEAR_DRAGON;
            case "spear_combo_3" -> WarriorAnimation.SPEAR_RETURN;
            case "spear_combo_4" -> WarriorAnimation.SPEAR_GUARD;
            case "spear_combo_5" -> WarriorAnimation.SPEAR_FLOW;
            default -> s.animation();
        };
        double dash=s.dash(), back=s.backstep();
        switch(s.family()) {
            case GREATSWORD -> dash=s.chain()==WarriorChain.ASSAULT?.60:s.chain()==WarriorChain.DEFENSE?.25:.40;
            case HAMMER -> dash=s.chain()==WarriorChain.ASSAULT?.55:s.chain()==WarriorChain.DEFENSE?.25:.35;
            case SPEAR -> { dash=s.backsteps()?0:s.chain()==WarriorChain.ASSAULT?2.8:s.chain()==WarriorChain.DEFENSE?.60:1.25; if(back>0)back=3.8; }
            case BOW -> { dash=back>0?0:s.slot()==4?3.8:s.slot()==1?4.8:3.2; if(back>0)back=6.0; }
            default -> {}
        }
        return copy(s,animation,dash,back);
    }
    public static WarriorSkill forWeapon(WarriorSkill skill,net.minecraft.world.item.ItemStack stack) {
        return forId(skill,net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
    }
    public static WarriorSkill forId(WarriorSkill s,String id) {
        if(!id.equals("tnc:doom_scythe"))return s;
        WarriorAnimation a=switch(s.slot()) {
            case 0->WarriorAnimation.SCYTHE_EXECUTE;case 1->WarriorAnimation.SCYTHE_GUARD;
            case 2->WarriorAnimation.SCYTHE_HOOK;case 3->WarriorAnimation.SCYTHE_POUNCE;
            case 4->WarriorAnimation.SCYTHE_REAP;default->WarriorAnimation.SCYTHE_WHIRL;
        };
        return copy(s,a,s.slot()<2?0:s.slot()==2?.25:s.slot()==3?1.7:s.slot()==5?1.3:.75,0);
    }
    private static WarriorSkill copy(WarriorSkill s,WarriorAnimation a,double dash,double back) {
        return new WarriorSkill(s.id(),s.codexId(),s.name(),s.sequence(),s.slot(),s.cooldownTicks(),s.damageMultiplier(),
                s.radius(),s.arcDegrees(),s.knockback(),s.durabilityCost(),s.finisherText(),s.family(),s.chain(),dash,
                s.ranged(),s.interrupt(),s.staggerTicks(),a,s.holds(),s.guardTicks(),s.evadeTicks(),back);
    }
}
