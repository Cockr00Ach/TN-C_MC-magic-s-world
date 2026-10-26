package com.tnc.tnc.warrior;
import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncwarrior") @PrefixGameTestTemplate(false)
public final class WarriorTalentGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void zeroTalentAndLegacyPointsCannotBypassQualification(GameTestHelper h) {
        var p=AdventureGameTests.player(h);var data=TemperingData.get(p.server);var id=p.getUUID();
        data.set(id,10000);
        h.assertTrue(!data.enroll(id)&&!data.enrolled(id)&&data.points(id)==0,"Legacy points never grant missing talent");
        h.assertTrue(TemperingEvents.award(p,120)==0&&!TemperingTraining.start(p),"Zero talent cannot gain or train");
        var hit=new LivingDamageEvent(p,h.getLevel().damageSources().mobAttack(EntityType.ZOMBIE.create(h.getLevel())),8);
        TemperingEvents.damage(hit);h.assertTrue(hit.getAmount()==8,"Legacy points cannot grant reduction without talent");
        var loaded=TemperingData.load(data.save(new CompoundTag()));
        h.assertTrue(!loaded.enrolled(id)&&loaded.points(id)==0,"Reload cannot fabricate aptitude");
        data.setTalent(id,1);h.assertTrue(data.points(id)==200,"Low aptitude caps effective inherited points");
        data.set(id,0);h.assertTrue(TemperingEvents.award(p,120)==60,"Low talent learns at half base rate");
        data.setTalent(id,6);h.assertTrue(TemperingEvents.award(p,120)==360,"High talent learns three times base rate");
        data.setTalent(id,0);h.assertTrue(data.points(id)==0&&!data.enrolled(id),"Removing talent disables growth and perks immediately");
        h.succeed();
    }
}
