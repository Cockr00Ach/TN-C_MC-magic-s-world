package com.tnc.tnc.warrior;

import com.tnc.tnc.warrior.client.WarriorComboAnimations;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarriorMartialArtsTest {
    @Test void allTenWeaponsHaveCompleteClipsAndStableProgression() {
        assertEquals(10,WarriorMartialArts.ids().size());
        int count=0;
        for(String path:WarriorMartialArts.ids()) {
            String id="tnc:"+path;
            for(var base:WarriorCombos.forFamily(WarriorMartialArts.family(id))) {
                var move=WarriorPerformance.forId(base,id);
                assertNotEquals(base.name(),move.name(),id);
                assertEquals(base.id(),move.id());assertEquals(base.codexId(),move.codexId());
                assertEquals(base.sequence(),move.sequence());assertEquals(base.holds(),move.holds());
                assertEquals(base.cooldownTicks(),move.cooldownTicks());assertEquals(base.damageMultiplier(),move.damageMultiplier());
                assertEquals(base.role(),move.role());assertEquals(base.guardTicks(),move.guardTicks());
                assertEquals(base.evadeTicks(),move.evadeTicks());assertEquals(base.durabilityCost(),move.durabilityCost());
                assertEquals(WarriorCombos.indexOf(base),WarriorCombos.indexOf(move));
                String key=WarriorMartialArts.clipKey(base.animation(),id);
                assertNotNull(key,id+" slot "+base.slot());
                var clip=WarriorComboAnimations.clip(base.animation(),id);
                assertNotNull(clip,key);assertEquals(key,clip.name());
                for(String bone:new String[]{"bone","head","armRight","armLeft","legRight","legLeft"}) {
                    for(int i=0;i<=20;i++)for(float v:clip.rotation(bone,i/20f))assertTrue(Float.isFinite(v));
                }
                count++;
            }
            for(var basic:new WarriorAnimation[]{WarriorAnimation.SLASH_LEFT,WarriorAnimation.SLASH_RIGHT,WarriorAnimation.SLASH_HORIZONTAL})
                assertTrue(WarriorComboAnimations.clip(basic,id).name().startsWith(path+"_basic_"));
        }
        assertEquals(57,count);
    }
    @Test void spearThrustAndStaffSweepHaveDifferentSectorsAndChoreography() {
        var base=WarriorCombos.bySlot(WarriorWeaponFamily.SPEAR,0);
        var spear=WarriorMartialArts.adapt(base,"tnc:red_tassel_spear");
        var staff=WarriorMartialArts.adapt(base,"tnc:qimei_staff");
        assertEquals(35,spear.arcDegrees());assertEquals(110,staff.arcDegrees());
        assertNotEquals(spear.radius(),staff.radius());
        var a=WarriorComboAnimations.clip(base.animation(),"tnc:red_tassel_spear");
        var b=WarriorComboAnimations.clip(base.animation(),"tnc:qimei_staff");
        assertNotEquals(a.rotation("armRight",.4f)[0],b.rotation("armRight",.4f)[0]);
    }
    @Test void thirdPartyWeaponsAndWrongFamiliesRetainTheirCatalog() {
        var base=WarriorCombos.bySlot(WarriorWeaponFamily.LONGSWORD,0);
        assertSame(base,WarriorMartialArts.adapt(base,"other:yue_bronze_jian"));
        assertSame(base,WarriorMartialArts.adapt(base,"tnc:red_tassel_spear"));
        assertNull(WarriorMartialArts.clipKey(WarriorAnimation.BOW_AIM,"tnc:red_tassel_spear"));
    }
}
