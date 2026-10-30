package com.tnc.tnc.warrior;

import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncwarrior")
@PrefixGameTestTemplate(false)
public final class SpecialistGameTests {
    @GameTest(template = "building_test_empty", timeoutTicks = 900)
    public static void shieldInstructorCountsFiveActualBlocks(GameTestHelper h) {
        exercise(h, "guard", 680);
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 900)
    public static void bladeInstructorRequiresChargedMeleeHits(GameTestHelper h) {
        exercise(h, "blade", 800);
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 1200)
    public static void windInstructorAcceptsThreeActualRangedCuts(GameTestHelper h) {
        exercise(h, "wind", 920);
    }

    private static void exercise(GameTestHelper h, String role, int offset) {
        var arena = TrainingGroundGameTests.arena(h, offset);
        int cx = arena.x(), cz = arena.z(), deck = arena.y();
        var p = AdventureGameTests.player(h);
        var data = TemperingData.get(p.server);
        data.setTalent(p.getUUID(), 2);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        if (role.equals("wind")) {
            data.set(p.getUUID(), 200);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                WindInfusion.infuse(p.getMainHandItem()));
        }
        p.teleportTo(h.getLevel(), cx + 0.5, deck + 1, cz + 0.5, 0f, 0f);
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(
            net.minecraft.server.level.ServerPlayer.class, p, 0, "f_8921_");
        h.assertTrue(PracticeArena.summon(p, role), "Terminal summons the " + role + " instructor");
        boolean[] started = {false}, done = {false}, cast = {false};
        int[] ticks = {0};
        int original = data.points(p.getUUID());
        float health = p.getHealth();
        h.onEachTick(() -> {
            if (done[0]) return;
            var tutors = SpecialistTrainers.trainers(p);
            if (tutors.isEmpty()) return;
            var t = tutors.get(0);
            var tag = t.getPersistentData();
            // 站在教官正前方：守势课两格，风刃课四格（三格外命中才计数）。
            double away = role.equals("wind") ? 4.0 : 2.0;
            p.setPos(t.getX(), t.getY(), t.getZ() - away);
            p.setYRot(0);
            p.setXRot(0);
            p.setDeltaMovement(Vec3.ZERO);
            if (!started[0]) {
                h.assertTrue(SpecialistTrainers.begin(p, t), "Owner starts the specific lesson");
                started[0] = true;
                if (role.equals("guard")) p.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
            }
            // Fake players do not keep their private pad entity ticking. Advance the
            // instructor too, including vanilla hurt immunity, as a live player's chunk does.
            p.tick();
            p.doTick();
            t.tick();
            ticks[0]++;
            if (role.equals("blade") && ticks[0] % 30 == 0) p.attack(t);
            if (role.equals("wind") && (ticks[0] == 2 || ticks[0] % 170 == 0)) cast[0] = WindInfusion.cast(p);
            if (ticks[0] == 700 && SpecialistTrainingData.get(p.server).count(p.getUUID(), role) == 0) {
                h.fail("Stalled " + SpecialistTrainers.diagnostic(p) + " attack=" + p.getAttackStrengthScale(.5f)
                    + " cast=" + cast[0] + " clock=" + p.server.overworld().getGameTime() + " position=" + p.position()
                    + " target=" + t.position() + " targetHealth=" + t.getHealth() + " look=" + p.getLookAngle());
            }
            if (SpecialistTrainingData.get(p.server).count(p.getUUID(), role) == 0) return;
            done[0] = true;
            try {
                h.assertTrue(data.points(p.getUUID()) == original && p.getHealth() == health,
                    "Specialist completion adds no growth and preserves life");
                h.assertTrue(!data.guardGraduate(p.getUUID()), "Specialist cannot consume the main drill's first reward");
                var saved = SpecialistTrainingData.load(SpecialistTrainingData.get(p.server).save(new net.minecraft.nbt.CompoundTag()));
                h.assertTrue(saved.count(p.getUUID(), role) == 1, "Specialist record survives reload");
                h.assertTrue(PracticeArena.dismiss(p), "Can dismiss after the lesson");
                h.assertTrue(tutors.stream().allMatch(Entity::isRemoved), "Owned instructors are removed on dismissal");
                h.succeed();
            } finally {
                p.discard();
            }
        });
    }
}
