package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.journey.JourneyData;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real instructor entry and server lesson progression, in isolated disposable test arenas. */
@GameTestHolder("tncwarriorpractical")
@PrefixGameTestTemplate(false)
public final class WarriorTutorialGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private record Fixture(ServerPlayer player, InstructorEntity mentor, TrainingGroundData.Arena arena,
                           List<ChunkPos> forcedChunks)
            implements AutoCloseable {
        @Override public void close() {
            WarriorTutorial.cancel(player);
            mentor.discard();
            TrainingGroundData.get(player.server).release(player.getUUID());
            TrainingGroundData.get(player.server).retireArena(arena.dimension(), arena.x(), arena.z());
            for (var chunk : forcedChunks) player.serverLevel().setChunkForced(chunk.x, chunk.z, false);
            player.discard();
        }
    }

    private static Fixture fixture(GameTestHelper h, int lane) {
        var level = h.getLevel();
        // Template origins differ by only a few blocks. Put every fixture in its own high cell.
        var center = h.absolutePos(new BlockPos(2, 4, 2)).offset(lane * 1024, 80, 6144);
        int px = TrainingGroundRules.padX(0, center.getX());
        int pz = TrainingGroundRules.padZ(0, center.getZ());
        var forcedChunks = new ArrayList<ChunkPos>();
        // There is no logged-in player at these remote fixtures. Explicit tickets keep actors loaded
        // across scheduled input ticks, just as a real learner's presence would in a running game.
        for (int cx = (px - 7) >> 4; cx <= (center.getX() + 2) >> 4; cx++) {
            for (int cz = (pz - 7) >> 4; cz <= (center.getZ() + 2) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
                level.getChunk(cx, cz);
                forcedChunks.add(new ChunkPos(cx, cz));
            }
        }
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
            var floor = new BlockPos(px + x, center.getY(), pz + z);
            level.getChunk(floor);
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            for (int y = 1; y <= 4; y++) level.setBlock(floor.above(y), Blocks.AIR.defaultBlockState(), 3);
        }
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            level.setBlock(center.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
        }
        var ledger = TrainingGroundData.get(level.getServer());
        ledger.markArena(level.dimension().location().toString(), center.getX(), center.getY(), center.getZ());
        var arena = ledger.arenaAt(level.dimension().location().toString(), px + .5, center.getY() + 1, pz + .5);
        h.assertTrue(arena != null && arena.center().equals(center), "Fixture must resolve its own registered arena");
        var player = AdventureGameTests.player(h);
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.setPos(px + .5, center.getY() + 1, pz + 5.5);
        // AdventureGameTests creates an unlogged ServerPlayer. Its normal 60-tick spawn shield
        // otherwise never ages, so hurt() returns before Forge can observe a teaching strike.
        for (int initialTick = 0; initialTick < 60; initialTick++) player.tick();
        var tempering = TemperingData.get(player.server);
        tempering.setTalent(player.getUUID(), 6);
        tempering.enroll(player.getUUID());
        tempering.set(player.getUUID(), 6000);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TNMod.ASHWOOD_JIAN.get()));
        var mentor = WarriorEntities.INSTRUCTOR.get().create(level);
        if (mentor == null) throw new IllegalStateException("Registered instructor type could not create mentor");
        mentor.setVariant(InstructorEntity.VARIANT_BLADE);
        mentor.equipForRole();
        mentor.setInvulnerable(true);
        mentor.getPersistentData().putBoolean(WarriorTutorial.MENTOR, true);
        mentor.moveTo(px + .5, center.getY() + 1, pz + 3.5, 180, 0);
        h.assertTrue(level.addFreshEntity(mentor), "Permanent mentor must join the actual test world");
        return new Fixture(player, mentor, arena, List.copyOf(forcedChunks));
    }

    private static void open(GameTestHelper h, Fixture f) {
        var event = new PlayerInteractEvent.EntityInteract(f.player(), InteractionHand.MAIN_HAND, f.mentor());
        MinecraftForge.EVENT_BUS.post(event);
        h.assertTrue(event.isCanceled() && event.getCancellationResult() == InteractionResult.SUCCESS,
                "Normal Forge right-click entry must consume the mentor interaction");
        h.assertTrue(WarriorTutorial.active(f.player()) && stage(f.player()) == 0,
                "Right-click opens the invitation without starting or completing lessons");
    }

    private static Object session(ServerPlayer player) {
        try {
            Field field = WarriorTutorial.class.getDeclaredField("SESSIONS");
            field.setAccessible(true);
            @SuppressWarnings("unchecked") var sessions = (Map<UUID, ?>) field.get(null);
            Object session = sessions.get(player.getUUID());
            if (session == null) throw new IllegalStateException("Expected active tutorial session");
            return session;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot inspect tutorial test session", error);
        }
    }

    private static Field field(Object session, String name) throws ReflectiveOperationException {
        Field field = session.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object get(ServerPlayer player, String name) {
        Object session = session(player);
        try { return field(session, name).get(session); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private static void set(ServerPlayer player, String name, Object value) {
        Object session = session(player);
        try { field(session, name).set(session, value); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private static long token(ServerPlayer player) { return (long) get(player, "token"); }
    private static int stage(ServerPlayer player) { return (int) get(player, "stage"); }
    private static void action(ServerPlayer player, String action) {
        // Exercise normal actions while avoiding wall-clock waits for the four-tick packet limiter.
        set(player, "lastAction", Long.MIN_VALUE);
        WarriorTutorial.action(player, token(player), action);
    }

    private static void age(ServerPlayer player, int age) {
        set(player, "started", player.level().getGameTime() - age);
    }

    private static void tick(ServerPlayer player) {
        try {
            Field field = ObfuscationReflectionHelper.findField(PlayerList.class, "f_11196_");
            @SuppressWarnings("unchecked") var players = (List<ServerPlayer>) field.get(player.server.getPlayerList());
            // AdventureGameTests players deliberately have no login; expose this one only for this callback.
            boolean added = !players.contains(player);
            if (added) players.add(player);
            try {
                WarriorTutorial.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, player.server));
            } finally {
                if (added) players.remove(player);
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot temporarily expose tutorial test player to server tick", error);
        }
    }

    private static CompoundTag savedLesson(ServerPlayer player) {
        return JourneyData.get(player.server).save(new CompoundTag()).copy().getCompound("Players")
                .getCompound(player.getUUID().toString()).getCompound("WarriorTutorial");
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 40)
    public static void mentorEntryRejectsStaleTokenAndCancelCleansOnlyTemporaryActors(GameTestHelper h) {
        try (var f = fixture(h, 1)) {
            var player = f.player();
            open(h, f);
            long inviteToken = token(player);
            WarriorTutorial.action(player, inviteToken + 1, "begin");
            h.assertTrue(stage(player) == 0 && TrainingGroundData.get(player.server).slot(player.getUUID(), f.arena().key()) == -1,
                    "Wrong token cannot begin a lesson or claim a pad");
            action(player, "begin");
            var actor = (InstructorEntity) get(player, "tutor");
            var dummy = (TestDummyEntity) get(player, "dummy");
            h.assertTrue(stage(player) == 1 && token(player) != inviteToken && actor != f.mentor()
                            && actor.tutorialActor() && dummy != null && actor.isAlive() && dummy.isAlive(),
                    "Accepted invitation creates an independent equipped actor and practice dummy");
            h.assertTrue(TrainingGroundData.get(player.server).slot(player.getUUID(), f.arena().key()) == 0,
                    "Starting through the mentor claims a real pad");
            h.assertTrue(savedLesson(player).getInt("Stage") == 1 && savedLesson(player).getBoolean("PracticeSword")
                            && !savedLesson(player).getBoolean("Completed"), "JourneyData persists the started checkpoint");
            WarriorTutorial.action(player, inviteToken, "cancel");
            h.assertTrue(WarriorTutorial.active(player) && actor.isAlive() && dummy.isAlive(),
                    "An old invitation token cannot cancel the new lesson session");
            action(player, "cancel");
            h.assertTrue(!WarriorTutorial.active(player) && actor.isRemoved() && dummy.isRemoved()
                            && TrainingGroundData.get(player.server).slot(player.getUUID(), f.arena().key()) == -1,
                    "Normal cancel removes both temporary entities and releases the occupied pad");
            h.assertTrue(f.mentor().isAlive(), "The permanent entry mentor survives lesson cancellation");
        }
        h.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 40)
    public static void cinematicInputsCannotPrimeCombosOrSpendTheirCooldowns(GameTestHelper h) {
        try (var f = fixture(h, 2)) {
            var player = f.player();
            open(h, f);action(player, "begin");
            h.assertTrue(WarriorTutorial.cinematic(player) && TrainingGround.qualified(player),
                    "Fixture is an otherwise eligible warrior inside the real cinematic gate");
            var state = WarriorComboService.state(player.getUUID());
            state.cooldowns.put("tutorial_sentinel", player.level().getGameTime() + 400);
            var before = Map.copyOf(state.cooldowns);
            for (int i = 0; i < 3; i++) WarriorComboService.input(player, WarriorMove.HORIZONTAL);
            WarriorComboService.input(player, WarriorMove.RIGHT);
            WarriorComboService.input(player, WarriorMove.LEFT);
            h.assertTrue(state.cooldowns.equals(before) && state.lastInput == Long.MIN_VALUE
                            && state.comboWeapon.isEmpty() && state.heldSkill == null,
                    "Cinematic taps cannot touch cooldowns, input timing, combo weapon or guard stance");
            age(player, WarriorTutorialScript.duration(1));
            tick(player);
            h.assertTrue(stage(player) == 2 && !WarriorTutorial.cinematic(player), "Real server tick ends the demo stage");
            WarriorComboService.input(player, WarriorMove.HORIZONTAL);
            h.assertTrue(state.lastInput == WarriorComboService.gameTime(player),
                    "The same equipped learner's input is accepted after the cinematic ends");
        }
        h.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 40)
    public static void skipAndTerminalCannotCompleteUnperformedPracticeAndCheckpointPersists(GameTestHelper h) {
        try (var f = fixture(h, 3)) {
            var player = f.player();
            open(h, f);action(player, "begin");
            action(player, "skip");
            h.assertTrue(stage(player) == 1 && !savedLesson(player).getBoolean("Completed"),
                    "Skipping the camera cannot complete or bypass the bounded demonstration");
            age(player, 185);tick(player);
            h.assertTrue(stage(player) == 2, "Skipped demonstration advances only to the terminal lesson");
            var terminal = (BlockPos) get(player, "terminal");
            player.setPos(terminal.getX() + .5, terminal.getY(), terminal.getZ() + .5);
            h.assertTrue(WarriorTutorial.terminal(player, terminal) && stage(player) == 3,
                    "Using the real session terminal starts primary attack instruction");
            action(player, "skip");tick(player);
            var dummy = (TestDummyEntity) get(player, "dummy");
            h.assertTrue(stage(player) == 3 && !WarriorTutorial.cinematic(player) && dummy.hitCount() == 0,
                    "Dialogue skip reaches hands-on practice without fabricating a successful hit");
            for (int i = 0; i < 3; i++) {
                action(player, "next");action(player, "skip");tick(player);
            }
            var saved = savedLesson(player);
            h.assertTrue(stage(player) == 3 && saved.getInt("Stage") == 3 && !saved.getBoolean("Completed"),
                    "Repeated dialogue actions cannot complete real practice; JourneyData retains only stage three");
            int swords = player.getInventory().countItem(TNMod.ASHWOOD_JIAN.get());
            action(player, "cancel");
            player.setPos(f.mentor().getX(), f.mentor().getY(), f.mentor().getZ() + 2);
            open(h, f);action(player, "begin");
            h.assertTrue(stage(player) == 3 && player.getInventory().countItem(TNMod.ASHWOOD_JIAN.get()) == swords,
                    "Reopening through the mentor resumes the saved lesson without duplicating its practice sword");
        }
        h.succeed();
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 40)
    public static void realNpcDemonstrationMovesWithoutDamagingOrRewardingTheLearner(GameTestHelper h) {
        try (var f = fixture(h, 4)) {
            var player = f.player();
            open(h, f);action(player, "begin");
            var actor = (InstructorEntity) get(player, "tutor");
            var dummy = (TestDummyEntity) get(player, "dummy");
            var home = (Vec3) get(player, "home");
            var start = actor.position();
            float health = player.getHealth();
            float dummyHealth = dummy.getHealth();
            int points = TemperingData.get(player.server).points(player.getUUID());
            var weapon = player.getMainHandItem().copy();
            WarriorTutorialDemonstration.tick(player, actor, home, 0);
            WarriorTutorialDemonstration.tick(player, actor, home, 18);
            h.assertTrue(actor.position().distanceToSqr(start) > .25,
                    "Real tutorial actor must physically perform a clear first thrust on the fixture floor");
            h.assertTrue(player.getHealth() == health && dummy.getHealth() == dummyHealth && dummy.hitCount() == 0
                            && TemperingData.get(player.server).points(player.getUUID()) == points
                            && ItemStack.matches(weapon, player.getMainHandItem()),
                    "NPC choreography emits effects without damage, growth, durability or item mutation");
            h.assertTrue(!WarriorUltimateService.active(player) && !savedLesson(player).getBoolean("Completed"),
                    "An NPC demonstration cannot borrow the learner's combat ultimate or grant completion");
        }
        h.succeed();
    }

    private static void lessonStep(GameTestHelper h, Fixture fixture, int after, Runnable action) {
        h.runAfterDelay(after, () -> {
            if (fixture.player().isRemoved()) return;
            try {
                action.run();
            } catch (RuntimeException | Error failure) {
                String context = lessonContext(fixture);
                LOGGER.error("Warrior tutorial integration step after {} ticks failed: {}", after, context, failure);
                fixture.close();
                throw new IllegalStateException("Tutorial step after " + after + " ticks failed: "
                        + failure.getMessage() + "; " + context, failure);
            }
        });
    }

    private static String lessonContext(Fixture fixture) {
        var player = fixture.player();
        String context = "time=" + player.level().getGameTime() + ", player=" + player.position()
                + ", alive=" + player.isAlive() + ", inside=" + TrainingGround.inside(player)
                + ", active=" + WarriorTutorial.active(player) + ", savedStage=" + savedLesson(player).getInt("Stage");
        var state = WarriorComboService.state(player.getUUID());
        context += ", eligible=" + WarriorComboService.eligible(player) + ", stanceUntil=" + state.stanceUntil
                + ", counterReady=" + state.counterReady + ", heldSkill=" + state.heldSkill;
        if (WarriorTutorial.active(player)) {
            var tutor = (InstructorEntity) get(player, "tutor");
            var dummy = (TestDummyEntity) get(player, "dummy");
            context += ", stage=" + stage(player) + ", tutor=" + tutor.position() + "/" + tutor.isAlive()
                    + "/" + tutor.getRemovalReason() + ", dummy=" + (dummy == null ? "null"
                    : dummy.position() + "/" + dummy.isAlive() + "/" + dummy.getRemovalReason());
        }
        return context;
    }

    @GameTest(template = "building_test_empty", timeoutTicks = 100)
    public static void realBeginnerInputsCompleteAllThreeLessonsAndOnlyThenFinish(GameTestHelper h) {
        var f = fixture(h, 5);
        var player = f.player();
        var tempering = TemperingData.get(player.server);
        tempering.setTalent(player.getUUID(), 1);
        tempering.set(player.getUUID(), 0);
        try {
            open(h, f);action(player, "begin");action(player, "skip");
            age(player, 185);tick(player);
            var terminal = (BlockPos) get(player, "terminal");
            player.setPos(terminal.getX() + .5, terminal.getY(), terminal.getZ() + .5);
            h.assertTrue(WarriorTutorial.terminal(player, terminal), "Beginner reaches the real terminal lesson");
            action(player, "skip");tick(player);
            h.assertTrue(stage(player) == 3 && !WarriorTutorial.cinematic(player),
                    "Rank-one practice sword and talent one may start primary-attack practice at zero tempering");
        } catch (RuntimeException | Error failure) {
            f.close();
            throw failure;
        }
        // These are real spaced input packets, not edits to cooldowns, counters or dummy statistics.
        for (int at : new int[]{2, 6, 10}) lessonStep(h, f, at,
                () -> WarriorComboService.input(player, WarriorMove.HORIZONTAL));
        lessonStep(h, f, 11, () -> {
            tick(player);
            h.assertTrue(stage(player) == 4 && ((TestDummyEntity) get(player, "dummy")).hitCount() > 0,
                    "ZZZ must actually damage the tutorial dummy before the parry lesson starts");
            action(player, "skip");tick(player);
        });
        lessonStep(h, f, 14, () -> WarriorComboService.input(player, WarriorMove.RIGHT));
        lessonStep(h, f, 18, () -> WarriorComboService.input(player, WarriorMove.LEFT));
        lessonStep(h, f, 22, () -> WarriorComboService.input(player, WarriorMove.RIGHT));
        lessonStep(h, f, 32, () -> {
            WarriorComboService.input(player, WarriorMove.RIGHT, WarriorComboInputPacket.HOLD);
            h.assertTrue(WarriorComboService.state(player.getUUID()).heldSkill != null,
                    "EQ and a genuinely held final E must create the real guard stance");
            // This only advances dialogue time to the next authored teaching strike.
            age(player, 210);
            h.assertTrue(WarriorComboService.state(player.getUUID()).stanceUntil > player.level().getGameTime()
                            && WarriorComboService.smallCounterTarget(player, (InstructorEntity) get(player, "tutor")),
                    "The active guard and live nearby teaching instructor must meet the real mob-parry conditions");
            tick(player);
            h.assertTrue(stage(player) == 5 && WarriorComboService.state(player.getUUID()).counterReady
                            > player.level().getGameTime(),
                    "The instructor's real mobAttack must be parried through Forge before breaker practice starts");
            h.assertTrue(player.getHealth() == player.getMaxHealth(), "A tutorial parry must preserve the learner's health");
        });
        lessonStep(h, f, 35, () -> MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player)));
        lessonStep(h, f, 36, () -> {
            action(player, "skip");tick(player);
            h.assertTrue(stage(player) == 5, "The delayed parry sweep cannot also complete the breaker lesson");
        });
        lessonStep(h, f, 40, () -> WarriorComboService.input(player, WarriorMove.RIGHT));
        lessonStep(h, f, 44, () -> WarriorComboService.input(player, WarriorMove.LEFT));
        lessonStep(h, f, 48, () -> {
            WarriorComboService.input(player, WarriorMove.HORIZONTAL);tick(player);
            h.assertTrue(stage(player) == 6 && !savedLesson(player).getBoolean("Completed"),
                    "A real EQZ hit reaches the summary, but completion still waits for its dialogue");
        });
        lessonStep(h, f, 52, () -> {
            action(player, "skip");tick(player);
            var saved = savedLesson(player);
            h.assertTrue(!WarriorTutorial.active(player) && saved.getInt("Stage") == 7 && saved.getBoolean("Completed"),
                    "Only the completed practical sequence and summary may finish the lesson");
            h.assertTrue(TrainingGroundData.get(player.server).slot(player.getUUID(), f.arena().key()) == -1,
                    "Finishing the full lesson releases the real occupied practice pad");
            f.close();h.succeed();
        });
    }

    private WarriorTutorialGameTests() {}
}
