package com.tnc.tnc.magic;

import com.tnc.tnc.npc.TNNpcs;
import com.tnc.tnc.world.yan.YanSanctuary;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tncyan")
@PrefixGameTestTemplate(false)
public final class YanLightningGameTests {
    @GameTest(template = "empty", timeoutTicks = 140, batch = "yan_lightning")
    public static void strikeAndBallHaveDistinctImpactVisuals(GameTestHelper test) {
        var level = test.getLevel().getServer().getLevel(YanSanctuary.KEY);
        var at = Vec3.atBottomCenterOf(new BlockPos(68, 65, 68));
        var chunk = new ChunkPos(BlockPos.containing(at));
        level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 2, chunk);
        test.startSequence().thenWaitUntil(() -> test.assertTrue(
                level.isPositionEntityTicking(BlockPos.containing(at)) && level.areEntitiesLoaded(chunk.toLong()),
                "The impact chunk must be tracking its entities")).thenExecute(() -> {
            try {
                assertDistinctImpacts(test, level, at);
            } finally {
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 2, chunk);
            }
        }).thenSucceed();
    }

    private static void assertDistinctImpacts(GameTestHelper test, ServerLevel level, Vec3 at) {
        var nearby = new AABB(at, at).inflate(8);
        var yan = TNNpcs.YAN_DARK.get().create(level);
        yan.setPos(at.add(5, 0, 0));
        test.assertTrue(level.addFreshEntity(yan), "Yan must be present for his impacts");
        test.assertTrue(level.getEntity(yan.getUUID()) == yan, "The impact owner resolves through the live entity table");

        var bolt = TNOrbEntities.LIGHTNING_STRIKE.get().create(level);
        bolt.asDark();
        bolt.configure(12, 6, at.y, 8);
        bolt.setLandImpact(yan, 10, 4);
        bolt.setPos(at);
        var ball = TNOrbEntities.LIGHTNING_STRIKE.get().create(level);
        ball.asDark();
        ball.asBall();
        ball.configure(20, 6, at.y, 12);
        ball.setLandImpact(yan, 24, 7);
        ball.setPos(at);
        var normal = TNOrbEntities.LIGHTNING_STRIKE.get().create(level);
        var outside = TNOrbEntities.LIGHTNING_STRIKE.get().create(test.getLevel());
        outside.asDark();
        try {
            test.assertTrue(bolt.usesYanArenaEffects() && ball.usesYanArenaEffects(), "The arena uses the optimized Yan effect budget");
            test.assertTrue(!normal.usesYanArenaEffects() && !outside.usesYanArenaEffects(), "Player lightning and other dimensions keep their normal effects");
            // Server realm chunks in this test need not tick on their own. Run the actual impact tick explicitly.
            bolt.tickCount = 1;
            bolt.tick();
            test.assertTrue(bolt.impactAt() == 1, "A bolt resolves on its first tick");
            test.assertTrue(level.getEntitiesOfClass(TNShockwaveEntity.class, nearby).size() == 1,
                    "The bolt keeps its original push wave");
            test.assertTrue(level.getEntitiesOfClass(TNLightningDragonEntity.class, nearby).isEmpty(),
                    "Ordinary bolts do not spawn a large dragon on impact");

            ball.tickCount = 2;
            ball.tick();
            test.assertTrue(ball.impactAt() == 2, "The ball resolves when it reaches the ground");
            test.assertTrue(level.getEntitiesOfClass(TNShockwaveEntity.class, nearby).size() == 2,
                    "The ball keeps its original push wave");
            test.assertTrue(level.getEntitiesOfClass(TNLightningDragonEntity.class, nearby).size() == 1,
                    "The ball still launches one lightning dragon");
        } finally {
            level.getEntitiesOfClass(TNShockwaveEntity.class, nearby).forEach(Entity::discard);
            level.getEntitiesOfClass(TNLightningDragonEntity.class, nearby).forEach(Entity::discard);
            yan.discard();
        }
    }
}
