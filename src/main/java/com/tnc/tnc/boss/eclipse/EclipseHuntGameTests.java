package com.tnc.tnc.boss.eclipse;

import com.mojang.authlib.GameProfile;
import com.tnc.tnc.world.skyhaven.SkyhavenContent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.PacketSendListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("tncacademy")
@PrefixGameTestTemplate(false)
public final class EclipseHuntGameTests {
    @GameTest(template = "building_test_empty", timeoutTicks = 450, batch = "eclipse_hunt")
    public static void twoSwimmersShareOneHuntAndLeavingRetiresIt(GameTestHelper helper) {
        ServerLevel sea = helper.getLevel().getServer().getLevel(SkyhavenContent.KEY);
        helper.assertTrue(sea != null, "Skyhaven dimension is absent");
        // The headless server's mock clients do not provide simulation tickets.
        var testTickets = new ArrayList<ChunkPos>();
        for (int cx = 30; cx <= 34; cx++) {
            for (int cz = 30; cz <= 34; cz++) {
                if (sea.setChunkForced(cx, cz, true)) testTickets.add(new ChunkPos(cx, cz));
            }
        }
        var first = new Swimmer(sea, "EclipseSwimmerA", new Vec3(512.5, 48.1, 512.5));
        var second = new Swimmer(sea, "EclipseSwimmerB", new Vec3(513.5, 48.1, 512.5));
        sea.addNewPlayer(first);
        sea.addNewPlayer(second);
        var failures = new ArrayList<String>();
        var timeline = new ArrayList<String>();
        for (int delay : new int[]{65, 100, 130, 160, 195, 235}) {
            helper.runAfterDelay(delay, () -> bosses(sea).forEach(boss -> timeline.add(delay + ": "
                    + boss.phase() + " age=" + boss.phaseAge(0) + " ticks=" + boss.tickCount + " at=" + boss.position())));
        }

        helper.runAfterDelay(12, () -> {
            if (!EclipseHunt.huntable(first) || !EclipseHunt.huntable(second)) failures.add("Black sea swimmers are not eligible");
            if (!bosses(sea).isEmpty()) failures.add("Boss spawned before the water grace period");
            second.setGameMode(GameType.CREATIVE);
            if (EclipseHunt.huntable(second)) failures.add("Creative players can trigger the hunt");
            second.setGameMode(GameType.SURVIVAL);
        });
        helper.runAfterDelay(65, () -> {
            var bosses = bosses(sea);
            if (bosses.size() != 1) failures.add("Two swimmers did not share a single pursuer");
            else if (bosses.get(0).phase() != EclipseLeviathanEntity.Phase.EMERGE) failures.add("Boss skipped the emergence tell");
        });
        helper.runAfterDelay(235, () -> {
            if (bosses(sea).size() != 1) failures.add("Pursuit duplicated or ended while swimmers remained");
            if (first.hits + second.hits == 0) failures.add("Pursuit never reached a swimmer with its lunge: " + timeline);
            first.setPos(512.5, 56, 512.5);
            second.setPos(513.5, 56, 512.5);
            if (EclipseHunt.huntable(first) || EclipseHunt.huntable(second)) failures.add("Dry players are still huntable");
        });
        helper.runAfterDelay(315, () -> {
            if (!bosses(sea).isEmpty()) failures.add("Boss did not retreat after everyone left the sea");
            sea.removePlayerImmediately(first, Entity.RemovalReason.DISCARDED);
            sea.removePlayerImmediately(second, Entity.RemovalReason.DISCARDED);
            testTickets.forEach(chunk -> sea.setChunkForced(chunk.x, chunk.z, false));
            helper.assertTrue(failures.isEmpty(), String.join("; ", failures));
            helper.succeed();
        });
    }

    @GameTest(template = "building_test_empty", batch = "eclipse_sweep")
    public static void sweepHitsBetweenTicksButLeavesASideDodge(GameTestHelper helper) {
        var from = new Vec3(0, 48, 0);
        var to = new Vec3(12, 48, 0);
        var inside = new AABB(5.7, 47.1, -.3, 6.3, 48.9, .3);
        helper.assertTrue(EclipseLeviathanEntity.sweepHits(from, to, inside), "Lunge tunneled through a swimmer");
        helper.assertTrue(EclipseLeviathanEntity.sweepHits(from,to,inside.move(0,0,7)), "Enlarged mouth does not match its damaging sweep");
        helper.assertTrue(!EclipseLeviathanEntity.sweepHits(from, to, inside.move(0, 0, 11)), "Sideways dodge still took damage");
        helper.assertTrue(!EclipseLeviathanEntity.sweepHits(from, to, inside.move(18, 0, 0)), "Sweep extended beyond the end of the lunge");
        helper.succeed();
    }

    private static List<? extends EclipseLeviathanEntity> bosses(ServerLevel sea) {
        return sea.getEntities(EclipseRegistration.ECLIPSE_LEVIATHAN, Entity::isAlive);
    }

    private static final class Swimmer extends ServerPlayer {
        private int hits;

        private Swimmer(ServerLevel sea, String name, Vec3 position) {
            super(sea.getServer(), sea, new GameProfile(UUID.randomUUID(), name));
            connection = new ServerGamePacketListenerImpl(sea.getServer(), new Connection(PacketFlow.SERVERBOUND), this) {
                @Override public void send(Packet<?> packet) {}
                @Override public void send(Packet<?> packet, PacketSendListener listener) {}
            };
            setPos(position);
        }

        @Override public void tick() { updateInWaterStateAndDoFluidPushing(); }

        @Override public boolean hurt(DamageSource source, float amount) {
            if (source.getEntity() instanceof EclipseLeviathanEntity) hits++;
            return false;
        }
    }
}
