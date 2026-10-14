package com.tnc.tnc.boss;

import com.tnc.tnc.npc.TNNpcs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tncleviathan")
@PrefixGameTestTemplate(false)
public final class LeviathanMovementGameTests {
    @GameTest(template="leviathan_empty", timeoutTicks=120)
    public static void groundSpawnTakesOffInsteadOfWalking(GameTestHelper h) {
        for (int x=8; x<=30; x++) for (int z=8; z<=30; z++) h.setBlock(x, 2, z, Blocks.STONE);
        var boss = h.spawn(TNNpcs.WATER_LEVIATHAN.get(), new BlockPos(16, 3, 16));
        var target = h.spawn(EntityType.VILLAGER, new BlockPos(16, 3, 27));
        target.setNoAi(true);
        target.setInvulnerable(true);
        double start = boss.getY();
        boss.setTarget(target);
        h.runAtTickTime(100, () -> {
            h.assertTrue(boss.getNavigation() instanceof FlyingPathNavigation,
                    "Dry boss must use flying navigation");
            h.assertTrue(boss.isNoGravity() && boss.getY()>start+1.0D && !boss.onGround(),
                    "Ground-spawned Leviathan must physically take off and hover");
            boss.discard(); target.discard(); h.succeed();
        });
    }

    @GameTest(template="leviathan_empty", timeoutTicks=140)
    public static void swimmingCanClimbAndSwitchBackToFlight(GameTestHelper h) {
        for (int x=10; x<=30; x++) for (int z=10; z<=30; z++)
            for (int y=2; y<=17; y++) h.setBlock(x, y, z, Blocks.WATER);
        var boss = h.spawn(TNNpcs.WATER_LEVIATHAN.get(), new BlockPos(20, 7, 20));
        var target = h.spawn(EntityType.VILLAGER, new BlockPos(20, 12, 25));
        target.setNoAi(true); target.setNoGravity(true); target.setInvulnerable(true);
        double start = boss.getY();
        boss.setTarget(target);
        h.runAtTickTime(90, () -> {
            h.assertTrue(boss.isInWater() && boss.getNavigation() instanceof WaterBoundPathNavigation,
                    "Submerged boss must use water navigation");
            h.assertTrue(boss.getY()>start+0.3D,
                    "Swimming must move vertically toward an underwater target; start="+start
                            +" actual="+boss.getY()+" target="+target.getY());
            target.discard(); boss.setTarget(null);
            boss.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(20, 25, 20)));
            boss.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        });
        h.runAtTickTime(120, () -> {
            h.assertTrue(!boss.isInWater() && boss.getNavigation() instanceof FlyingPathNavigation
                            && boss.isNoGravity(), "Leaving water must restore flight without gravity");
            boss.discard(); h.succeed();
        });
    }
}
