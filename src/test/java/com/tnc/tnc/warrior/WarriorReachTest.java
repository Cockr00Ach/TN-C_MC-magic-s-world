package com.tnc.tnc.warrior;

import com.tnc.tnc.warrior.client.WarriorHeavyGrowth;
import net.minecraft.world.entity.ai.attributes.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarriorReachTest {
    private AttributeInstance attribute(double base){return new AttributeInstance(new RangedAttribute("test.reach",base,0,1024),a->{});}
    @Test void serverReachAndRenderedPeakShareExactlyTheSameProgression(){
        for(int points:new int[]{-1,0,200,1000,5000,10000,Integer.MAX_VALUE}){
            assertEquals((float)WarriorReach.peak(points),WarriorHeavyGrowth.peak(points));
            assertEquals(WarriorReach.peak(points),WarriorReach.scale(WarriorWeaponFamily.HAMMER,points,10000));
            assertEquals(WarriorReach.peak(points),WarriorReach.scale(WarriorWeaponFamily.GREATSWORD,points,10000));
        }
        assertEquals(8,WarriorReach.scale(WarriorWeaponFamily.HAMMER,10000,10000));
        assertEquals(8,WarriorReach.scale(WarriorWeaponFamily.LONGSWORD,10000,10000),1e-8);
        assertEquals(8,WarriorReach.scale(WarriorWeaponFamily.SPEAR,10000,10000),1e-8);
        assertEquals(1,WarriorReach.scale(WarriorWeaponFamily.BOW,10000,10000),1e-8);
    }
    @Test void nativeMouseReachScalesOnceAndIsRestoredWhenSwitchingWeapons(){
        var a=attribute(3);
        WarriorReach.apply(a,true,false,0);assertEquals(9,a.getValue(),1e-8);
        WarriorReach.apply(a,true,false,5000);assertEquals(16.5,a.getValue(),1e-8);
        for(int i=0;i<100;i++)WarriorReach.apply(a,true,false,10000);
        assertEquals(24,a.getValue(),1e-8);assertEquals(1,a.getModifiers().size());
        WarriorReach.apply(a,false,false,10000);assertEquals(3,a.getValue(),1e-8);assertTrue(a.getModifiers().isEmpty());
    }
    @Test void creativeBonusOtherModifiersAndDisabledReachAreHandledWithoutStacking(){
        var a=attribute(3);
        WarriorReach.apply(a,true,true,10000);assertEquals(48,a.getValue()+3,1e-8);
        WarriorReach.apply(a,true,false,10000);assertEquals(24,a.getValue(),1e-8);
        var other=new AttributeModifier(UUID.randomUUID(),"Other mod",2,AttributeModifier.Operation.ADDITION);
        a.addTransientModifier(other);WarriorReach.apply(a,true,false,10000);assertEquals(40,a.getValue(),1e-8);
        WarriorReach.apply(a,false,false,0);assertEquals(5,a.getValue(),1e-8);assertNotNull(a.getModifier(other.getId()));
        var disabled=attribute(0);WarriorReach.apply(disabled,true,true,10000);assertEquals(0,disabled.getValue());
    }
}
