package com.tnc.tnc.warrior;
import java.util.List;

/** Stable skill IDs, shared first three slots; later slots retain weapon roles. */
public final class WarriorUnifiedSlots {
    private WarriorUnifiedSlots(){}
    public static int slot(String id){
        int n=Integer.parseInt(id.substring(id.lastIndexOf('_')+1));
        if(id.startsWith("bow_"))return switch(n){case 1->0;case 4->1;case 5->2;case 2->3;case 3->4;default->5;};
        if(n==4)return 1;if(n==3)return 2;
        if(id.startsWith("greatsword_")||id.startsWith("hammer_"))return n==2?0:n==1?3:n-1;
        return n==1?0:n==2?3:n-1;
    }
    public static String keys(int slot){return switch(slot){case 0->"qze";case 1->"eqe";case 2->"eqz";case 3->"qez";case 4->"zqe";default->"zzq";};}
    public static String name(String id,String original){return id.equals("bow_combo_4")?"架势·挽弓":id.equals("bow_combo_5")?"弓身破架":original;}
    public static String purpose(int slot){return switch(slot){case 0->"攻击";case 1->"振刀";case 2->"破振刀";default->"武器特色";};}
    public static WarriorSkill apply(WarriorSkill s){
        int slot=slot(s.id());boolean stance=slot==1,breaker=slot==2,core=slot<3;
        var chain=core?(slot==0?WarriorChain.ATTACK:WarriorChain.DEFENSE):s.chain();
        var holds=List.of(false,false,stance);
        double dash=core?(breaker?.25:0):s.dash();
        boolean ranged=s.ranged()&&!stance&&!breaker;
        String effect=core?purpose(slot)+(stance?"：按住末段 E 架住攻击，成功振刀触发横扫击飞":breaker?"：向前压进破开振刀，打断并造成僵直":"：正面主攻，明确的一次发力收势"):s.finisherText();
        return new WarriorSkill(s.id(),s.codexId(),name(s.id(),s.name()),WarriorMove.parse(keys(slot)),slot,s.cooldownTicks(),
            stance?.6f:breaker?.8f:s.damageMultiplier(),stance||breaker?3.0:s.radius(),core?(stance?120:breaker?100:s.arcDegrees()):s.arcDegrees(),
            breaker?.65:s.knockback(),s.durabilityCost(),effect,s.family(),chain,dash,ranged,stance||breaker||(!core&&s.interrupt()),
            stance?20:breaker?50:s.staggerTicks(),s.animation(),holds,stance?40:0,core?0:s.evadeTicks(),core?0:s.backstep());
    }
}
