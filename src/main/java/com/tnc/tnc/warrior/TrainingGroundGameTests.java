package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.AdventureGameTests;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncwarrior")
@PrefixGameTestTemplate(false)
public final class TrainingGroundGameTests {
    @GameTest(template = "building_test_empty", timeoutTicks = 40)
    public static void elementalEffectsPreserveCombatStatsAndCosmeticChoiceSurvivesReload(GameTestHelper h) {
        var p = AdventureGameTests.player(h);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        TemperingData.get(p.server).setTalent(p.getUUID(), 2);
        var data = WarriorEffectsData.get(p.server);
        float health = p.getHealth();
        int damage = p.getMainHandItem().getDamageValue();
        for (var style : WarriorEffectStyle.values()) {
            data.set(p.getUUID(), style);
            h.assertTrue(WarriorCombatEffects.style(p) == style, "Selected palette resolves");
            WarriorCombatEffects.slash(p, false);
            WarriorCombatEffects.slash(p, true);
            WarriorCombatEffects.guard(p, false, true);
            WarriorCombatEffects.guard(p, true, false);
        }
        var restored = WarriorEffectsData.load(data.save(new CompoundTag()));
        h.assertTrue(restored.style(p.getUUID()) == WarriorEffectStyle.DARK, "Cosmetic choice persists independently of training plots");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, WindInfusion.infuse(p.getMainHandItem()));
        h.assertTrue(WarriorCombatEffects.style(p) == WarriorEffectStyle.WIND, "Weapon element takes priority");
        p.setXRot(-90);
        WarriorCombatEffects.slash(p, false);
        WarriorCombatEffects.windCut(p, p.getEyePosition(), p.getEyePosition().add(0, 6, 0));
        h.assertTrue(p.getHealth() == health && p.getMainHandItem().getDamageValue() == damage
            && TemperingData.get(p.server).points(p.getUUID()) == 0, "Cosmetic emissions cannot alter life, durability or growth");
        p.discard();
        h.succeed();
    }

    /**
     * 在测试世界远处砌一座演武场。
     *
     * <p>GameTest 会把整批测试同时跑起来，所以每一条测试都用自己的一座台，
     * 台心和演武位都从返回的这条记录里取，不依赖「最后建的那座」。
     */
    static TrainingGroundData.Arena arena(GameTestHelper h, int offset) {
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(0, 0, 0));
        int cx = origin.getX() + offset, cz = origin.getZ() + offset;
        PracticeArena.build(level, cx, cz);
        String dimension = level.dimension().location().toString();
        for (var built : TrainingGroundData.get(level.getServer()).arenas()) {
            if (built.x() == cx && built.z() == cz) return built;
        }
        throw new IllegalStateException("Arena was not recorded at " + cx + "," + cz + " in " + dimension);
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 600)
    public static void arenaBuildsDeckPadsTerminalAndProtection(GameTestHelper h) {
        var level = h.getLevel();
        var arena = arena(h, 240);
        int cx = arena.x(), cz = arena.z(), deck = arena.y();
        h.assertTrue(level.getBlockState(new BlockPos(cx, deck + 1, cz)).is(TNMod.PRACTICE_TERMINAL.get()),
            "Terminal stands in the middle of the deck");
        h.assertTrue(level.getBlockState(new BlockPos(cx + 1, deck, cz)).is(Blocks.CHISELED_STONE_BRICKS),
            "Terminal has a chiselled pedestal");
        for (int pad = 0; pad < TrainingGroundRules.PAD_COUNT; pad++) {
            int px = TrainingGroundRules.padX(pad, cx), pz = TrainingGroundRules.padZ(pad, cz);
            h.assertTrue(!level.getBlockState(new BlockPos(px, deck, pz)).isAir(), "Pad " + pad + " has a floor");
            h.assertTrue(level.getBlockState(new BlockPos(px, deck + 1, pz)).isAir(), "Pad " + pad + " is walkable");
        }
        h.assertTrue(level.getBlockState(new BlockPos(cx + TrainingGroundRules.HALF, deck + 1, cz))
            .is(Blocks.POLISHED_BLACKSTONE_BRICK_WALL), "Deck has a rim wall");
        var p = AdventureGameTests.player(h);
        p.teleportTo(level, cx + 0.5, deck + 1, cz + 0.5, 0f, 0f);
        h.assertTrue(TrainingGround.inside(p), "Standing on the deck counts as inside the arena");
        var breakEvent = new BlockEvent.BreakEvent(level, new BlockPos(cx, deck, cz),
            level.getBlockState(new BlockPos(cx, deck, cz)), p);
        MinecraftForge.EVENT_BUS.post(breakEvent);
        h.assertTrue(breakEvent.isCanceled(), "Arena floor is protected");
        var placePos = new BlockPos(cx, deck + 1, cz + 2);
        var placeEvent = new BlockEvent.EntityPlaceEvent(
            net.minecraftforge.common.util.BlockSnapshot.create(level.dimension(), level, placePos),
            level.getBlockState(placePos.below()), p);
        MinecraftForge.EVENT_BUS.post(placeEvent);
        h.assertTrue(placeEvent.isCanceled(), "Arena floor rejects building");
        p.discard();
        h.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 600)
    public static void terminalSummonsOnlyTheChosenInstructorAndDismissClears(GameTestHelper h) {
        var level = h.getLevel();
        var arena = arena(h, 340);
        int cx = arena.x(), cz = arena.z(), deck = arena.y();
        var p = AdventureGameTests.player(h);
        TemperingData.get(p.server).setTalent(p.getUUID(), 2);
        p.teleportTo(level, cx + 0.5, deck + 1, cz + 0.5, 0f, 0f);
        h.assertTrue(PracticeArena.summon(p, "guard"), "Terminal can summon the shield instructor");
        var box = new AABB(cx - 40, deck - 4, cz - 40, cx + 40, deck + 12, cz + 40);
        var tutors = level.getEntitiesOfClass(InstructorEntity.class, box);
        h.assertTrue(tutors.size() == 1, "Exactly one instructor appears, found " + tutors.size());
        h.assertTrue(tutors.get(0).variant() == InstructorEntity.VARIANT_GUARD, "The chosen instructor is the shield one");
        h.assertTrue("guard".equals(TrainingGround.activeRole(p)), "The panel is told which instructor is out");
        // 四位教官手里真拿东西：陪练木剑、洛克铁剑 + 盾、莉娅铁剑、岚风刃。
        for (String role : InstructorEntity.VARIANTS) {
            h.assertTrue(PracticeArena.summon(p, role), "Terminal can summon " + role);
            var out = level.getEntitiesOfClass(InstructorEntity.class, box);
            h.assertTrue(out.size() == 1, "Summoning " + role + " leaves exactly one instructor");
            var tutor = out.get(0);
            h.assertTrue(tutor.variant() == InstructorEntity.variantOf(role), role + " is the one that came out");
            var main = tutor.getMainHandItem();
            h.assertTrue(!main.isEmpty(), role + " holds a weapon");
            h.assertTrue(switch (role) {
                    case "counter" -> main.is(Items.WOODEN_SWORD);
                    case "wind" -> WindInfusion.isWind(main);
                    default -> main.is(Items.IRON_SWORD);
                }, role + " holds the weapon its lesson uses");
            h.assertTrue(tutor.getOffhandItem().is(Items.SHIELD) == "guard".equals(role),
                role + " carries a shield only when the lesson is about blocking");
        }
        h.assertTrue(PracticeArena.dismiss(p), "Dismiss ends the session");
        h.assertTrue(level.getEntitiesOfClass(InstructorEntity.class, box).isEmpty(), "Dismiss removes the instructor");
        h.assertTrue(TrainingGroundData.get(p.server).slot(p.getUUID(), arena.key()) < 0, "Dismiss releases the arena pad");
        p.discard();
        h.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 800)
    public static void realTeacherAttacksCanBeBlockedAndCounteredForFirstReward(GameTestHelper h) {
        realPractice(h, 0, false);
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 800)
    public static void expertTeacherRecordsRealCounterWithoutRepeatingGrowthReward(GameTestHelper h) {
        realPractice(h, 2, true);
    }

    private static void realPractice(GameTestHelper h, int mode, boolean graduated) {
        var level = h.getLevel();
        var arena = arena(h, 440 + mode * 60);
        int cx = arena.x(), cz = arena.z(), deck = arena.y();
        var p = AdventureGameTests.player(h);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        var shield = new ItemStack(Items.SHIELD);
        p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, shield);
        var data = TemperingData.get(p.server);
        data.setTalent(p.getUUID(), 2);
        if (graduated) data.completeGuardDrill(p.getUUID());
        p.teleportTo(level, cx + 0.5, deck + 1, cz + 0.5, 0f, 0f);
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(
            net.minecraft.server.level.ServerPlayer.class, p, 0, "f_8921_");
        // 难度要在开练前选：终端「开始练习」会当场开课，训练中难度是锁死的。
        for (int m = 0; m < mode; m++) {
            h.assertTrue(TrainingGround.cyclePractice(p), "Idle player can select difficulty before summoning");
        }
        h.assertTrue(PracticeArena.summon(p, "counter"), "Sparring dummy summoned from the terminal");
        boolean[] started = {false}, done = {false};
        int[] ticks = {0};
        float health = p.getHealth();
        h.onEachTick(() -> {
            if (done[0]) return;
            var teacher = TrainingGround.teacher(p);
            if (teacher == null) return;
            var tag = teacher.getPersistentData();
            p.setPos(teacher.getX(), teacher.getY(), teacher.getZ() - 2.0);
            p.setDeltaMovement(Vec3.ZERO);
            if (!started[0]) {
                TrainingGround.resetRhythm(p);
                started[0] = true;
                p.setYRot(0);
                p.setXRot(0);
                p.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
                h.assertTrue(TemperingTraining.start(p), "Drill starts on the pad");
                h.assertTrue(!TrainingGround.cyclePractice(p) && TemperingTraining.mode(p) == mode, "Active drill locks difficulty");
                if (graduated) {
                    p.stopUsingItem();
                    p.invulnerableTime = 0;
                    p.hurt(p.damageSources().mobAttack(teacher), 4);
                    h.assertTrue(p.getHealth() == health, "A missed strike is safe");
                    p.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
                }
            }
            p.tick();
            p.doTick();
            TrainingGround.practiceTick(p);
            if (++ticks[0] == 300 && TemperingTraining.blockCount(p) < 3) {
                var ledger = TrainingGroundData.get(p.server);
                h.fail("Real blocking stalled: using=" + p.isUsingItem() + ", blocking=" + p.isBlocking()
                    + ", blocks=" + TemperingTraining.blockCount(p) + ", swings="
                    + teacher.getPersistentData().getInt("Swings") + ", position=" + p.position()
                    + ", inside=" + TrainingGround.inside(p) + ", active=" + TemperingTraining.active(p)
                    + ", enrolled=" + TemperingData.get(p.server).enrolled(p.getUUID()) + ", arena=" + arena
                    + ", home=" + teacher.getPersistentData().getDouble("HomeX") + ","
                    + teacher.getPersistentData().getDouble("HomeY") + ","
                    + teacher.getPersistentData().getDouble("HomeZ")
                    + ", next=" + teacher.getPersistentData().getLong("NextStrike")
                    + ", windup=" + teacher.getPersistentData().getBoolean("Windup")
                    + ", now=" + p.server.overworld().getGameTime() + ", mode=" + TemperingTraining.mode(p));
                return;
            }
            if (TemperingTraining.blockCount(p) < 3) return;
            done[0] = true;
            try {
                h.assertTrue(teacher.getPersistentData().getInt("Swings") >= 3, "Instructor performed real timed strikes");
                h.assertTrue(p.getHealth() == health && shield.getDamageValue() == 0, "Three actual blocks preserve life and shield durability");
                p.stopUsingItem();
                p.attack(teacher);
                h.assertTrue(data.points(p.getUUID()) == (graduated ? 0 : 120) && data.guardGraduate(p.getUUID()),
                    "Only first graduation yields growth, even on expert tier");
                var record = TrainingGroundData.get(p.server).record(p.getUUID());
                h.assertTrue(record.mode() == mode && record.completions() == 1
                    && record.score() == (graduated ? 85 : 100) && record.reaction() == 0,
                    "Real misses and counter update selected tier's personal record: " + record);
                h.assertTrue(PracticeArena.dismiss(p), "Completed drill can dismiss the dummy");
                h.succeed();
            } finally {
                p.discard();
            }
        });
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 40)
    public static void tierRecordsPersistSeparatelyAndDisplayPacketRoundTrips(GameTestHelper h) {
        var ledger = new TrainingGroundData();
        var id = UUID.randomUUID();
        ledger.claim(id);
        ledger.complete(id, 0, 90, 12);
        ledger.complete(id, 0, 80, 6);
        ledger.cycle(id);
        h.assertTrue(ledger.record(id).completions() == 0, "A new tier has its own records");
        ledger.complete(id, 1, 75, 20);
        ledger = TrainingGroundData.load(ledger.save(new CompoundTag()));
        h.assertTrue(ledger.record(id).mode() == 1 && ledger.record(id).score() == 75, "Selected tier survives save/load");
        ledger.cycle(id);
        ledger.cycle(id);
        var record = ledger.record(id);
        h.assertTrue(record.completions() == 2 && record.score() == 90 && record.reaction() == 6,
            "Best score and fastest counter remain separate personal records");
        var packet = new TemperingPacket(true, 200, 2, true, false, 0, 0, 0, true, true, 2, 5, 95, 10, "blade");
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            packet.encode(buffer);
            h.assertTrue(packet.equals(TemperingPacket.decode(buffer)), "Practice record and instructor role round-trip through the network");
        } finally {
            buffer.release();
        }
        h.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 40)
    public static void padLedgerSurvivesReloadAndCapsAtFour(GameTestHelper h) {
        var data = new TrainingGroundData();
        var ids = new UUID[TrainingGroundRules.PAD_COUNT + 1];
        for (int i = 0; i < ids.length; i++) ids[i] = UUID.randomUUID();
        for (int i = 0; i < TrainingGroundRules.PAD_COUNT; i++) {
            h.assertTrue(data.claim(ids[i]) == i, "Pads are handed out in order");
        }
        h.assertTrue(data.claim(ids[0]) == 0, "Claiming twice keeps the same pad");
        h.assertTrue(data.claim(ids[TrainingGroundRules.PAD_COUNT]) < 0, "The fifth player is refused, not stacked on a pad");
        var reloaded = TrainingGroundData.load(data.save(new CompoundTag()));
        h.assertTrue(reloaded.slot(ids[0]) == 0 && reloaded.slot(ids[3]) == 3, "Pad ownership survives reload");
        reloaded.release(ids[1]);
        h.assertTrue(reloaded.claim(ids[TrainingGroundRules.PAD_COUNT]) == 1, "A released pad is reusable");
        h.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 200)
    public static void legacyTrainingDimensionIsStillRegisteredForRescue(GameTestHelper h) {
        var level = h.getLevel().getServer().getLevel(
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "training_ground")));
        h.assertTrue(level != null, "Legacy training dimension stays registered until every save is swept");
        h.succeed();
    }
}
