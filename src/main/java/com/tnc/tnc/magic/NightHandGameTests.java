package com.tnc.tnc.magic;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class NightHandGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="night_hand_presentation")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void handsReleaseWithoutGatesAndSeparateTheirStarts(GameTestHelper h) {
        var caster = h.spawn(EntityType.ZOMBIE, new BlockPos(1,2,1));
        caster.setNoAi(true);
        var victim = h.spawn(EntityType.VILLAGER, new BlockPos(3,2,3));
        victim.setNoAi(true);
        for (int tier = 1; tier <= 5; tier++) {
            // Headless fixture omits Spell Engine. Reuse a registered metadata type for
            // this manually ticked, unspawned production hand; the runtime registry is frozen.
            var hand = new TNNightGripEntity((EntityType) EntityType.MARKER, h.getLevel());
            if (tier == 5) hand.launchSkyfall(caster, victim.getId(), tier, 0, 7, victim.position());
            else hand.launch(caster, victim.getId(), tier, 0, 1);
            Vec3 start = hand.position();
            float health = victim.getHealth();
            hand.tickCount=1;hand.tick();
            var gates = h.getLevel().getEntitiesOfClass(TNMagicCircleEntity.class, caster.getBoundingBox().inflate(80),
                    e -> e.style() == TNMagicCircleEntity.STYLE_DARK_HAND);
            h.assertTrue(gates.isEmpty(), "Night hands never spawn a summoning gate");
            h.assertTrue(hand.launched() && hand.position().distanceToSqr(start) > 0.001, "Tier " + tier + " releases immediately");
            hand.discard();
            gates.forEach(TNMagicCircleEntity::discard);
        }
        caster.discard();victim.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="night_hand_presentation")
    @SuppressWarnings({"unchecked","rawtypes"})
    public static void allHandsHaveSeparateOriginsAndReleaseTogether(GameTestHelper h) {
        var caster=h.spawn(EntityType.ZOMBIE,new BlockPos(1,2,1));caster.setNoAi(true);
        for(int tier=2;tier<=4;tier++) {
            var first=new TNNightGripEntity((EntityType)EntityType.MARKER,h.getLevel());
            var next=new TNNightGripEntity((EntityType)EntityType.MARKER,h.getLevel());
            first.launch(caster,-1,tier,0,2);next.launch(caster,-1,tier,1,2);
            h.assertTrue(first.position().distanceTo(next.position())>first.handHalfExtent()*2,"Model-sized launch separation");
            first.tickCount=1;next.tickCount=1;first.tick();next.tick();
            h.assertTrue(first.launched()&&next.launched(),"All hands release together");
            first.discard();next.discard();
        }
        caster.discard();h.succeed();
    }
}
