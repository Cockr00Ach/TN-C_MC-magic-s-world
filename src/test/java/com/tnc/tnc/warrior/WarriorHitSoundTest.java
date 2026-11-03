package com.tnc.tnc.warrior;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WarriorHitSoundTest {
    @Test void weaponsHaveDistinctImpactProfilesAndCrowdHitsAreThrottled(){
        var light=WarriorHitSound.profile(WarriorWeaponFamily.LONGSWORD,false);
        var heavy=WarriorHitSound.profile(WarriorWeaponFamily.GREATSWORD,false);
        var hammer=WarriorHitSound.profile(WarriorWeaponFamily.HAMMER,false);
        assertNotEquals(light.soundId(),heavy.soundId());assertTrue(light.pitch()>heavy.pitch());assertTrue(hammer.pitch()<heavy.pitch());
        assertNotEquals(light.soundId(),WarriorHitSound.profile(WarriorWeaponFamily.SPEAR,false).soundId());
        assertNotEquals(heavy.soundId(),WarriorHitSound.profile(WarriorWeaponFamily.BOW,true).soundId());
        assertTrue(WarriorHitSound.ready(0,Long.MIN_VALUE,3));assertFalse(WarriorHitSound.ready(12,10,3));assertTrue(WarriorHitSound.ready(13,10,3));
    }
}
