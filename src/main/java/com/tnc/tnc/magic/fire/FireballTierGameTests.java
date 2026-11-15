package com.tnc.tnc.magic.fire;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("tncfirerefine")
@PrefixGameTestTemplate(false)
public final class FireballTierGameTests {
    private static final String TEMPLATE = "building_test_empty";

    private static ServerPlayer caster(GameTestHelper h) {
        var at = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 3, 3)));
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "FireballTest"));
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(player.server,
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) {}
        };
        player.setGameMode(GameType.SURVIVAL);
        player.setInvulnerable(false);
        player.moveTo(at.x, at.y, at.z, 0, 0);
        h.getLevel().addNewPlayer(player);
        return player;
    }

    private static Zombie enemy(GameTestHelper h, Vec3 at) {
        var target = EntityType.ZOMBIE.create(h.getLevel());
        target.setNoAi(true);
        target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300);
        target.setHealth(300);
        target.setPos(at);
        h.getLevel().addFreshEntity(target);
        return target;
    }

    private static void cast(ServerPlayer player, String name) {
        TNFireRework.cast(player, ResourceLocation.fromNamespaceAndPath("tnc", name));
    }

    private static List<TNFireBoltEntity> bolts(ServerPlayer owner) {
        return owner.serverLevel().getEntitiesOfClass(TNFireBoltEntity.class, owner.getBoundingBox().inflate(80),
                bolt -> bolt.getOwner() == owner && !bolt.isRemoved());
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "fireball_tiers")
    public static void earlyTiersLaunchOneOrbAndGrowInSize(GameTestHelper h) {
        var player = caster(h);
        var spells = List.of("sun_ray", "blast_ray", "fire_dragon");
        var sizes = new ArrayList<Float>();
        try {
            for (var spell : spells) {
                cast(player, spell);
                var launched = bolts(player);
                h.assertTrue(launched.size() == 1, "Each early tier launches one orb");
                sizes.add(launched.get(0).radius());
                launched.get(0).discard();
            }
            h.assertTrue(sizes.get(1) > sizes.get(0) * 2 && sizes.get(2) > sizes.get(1) * 2,
                    "Small, larger and giant tiers have visibly distinct sizes");
            h.succeed();
        } finally {
            bolts(player).forEach(Entity::discard);
            player.discard();
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "fireball_tiers")
    public static void fourthTierLocksThreeGiantOrbsAndTurnsTowardMovingTargets(GameTestHelper h) {
        var player = caster(h);
        var targets = List.of(enemy(h, player.position().add(-4, 0, 12)),
                enemy(h, player.position().add(0, 0, 14)), enemy(h, player.position().add(4, 0, 12)));
        try {
            cast(player, "flame_demon_wrath");
            var launched = bolts(player);
            h.assertTrue(launched.size() == 3 && launched.stream().allMatch(bolt -> bolt.ignitionTier() == 4 && bolt.radius() >= 1.65F),
                    "T4 launches three giant blue-tier orbs");
            h.assertTrue(launched.stream().map(TNFireBoltEntity::targetEntity).distinct().count() == 3,
                    "The volley distributes locks across three visible enemies");
            var bolt = launched.get(0);
            var target = bolt.targetEntity();
            Vec3 before = bolt.safeDirection();
            target.setPos(target.position().add(4, 0, 0));
            bolt.tick();
            Vec3 wanted = TNFireballChain.center(target).subtract(bolt.position()).normalize();
            h.assertTrue(bolt.safeDirection().dot(wanted) > before.dot(wanted), "An orb turns toward its moving target");
            h.succeed();
        } finally {
            bolts(player).forEach(Entity::discard);
            targets.forEach(Entity::discard);
            player.discard();
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 90, batch = "fireball_tiers")
    public static void rainContinuesAfterTheCastAndRecastingReplacesItsSchedule(GameTestHelper h) {
        var player = caster(h);
        var target = enemy(h, player.position().add(0, 0, 12));
        cast(player, "solar_judgment");
        h.assertTrue(bolts(player).size() == 1, "The rain starts immediately with one sky orb");
        bolts(player).forEach(Entity::discard);
        cast(player, "solar_judgment");
        bolts(player).forEach(Entity::discard);
        h.runAfterDelay(6, () -> {
            var emitted = bolts(player);
            h.assertTrue(emitted.size() == 1 && emitted.get(0).getY() > target.getY() + 5,
                    "Recasting leaves a single schedule and the next orb comes from above");
            h.assertTrue(emitted.get(0).targetEntity() == target, "Skyfire rain locks onto its victim");
            player.discard();
            emitted.forEach(Entity::discard);
        });
        h.runAfterDelay(14, () -> {
            try {
                h.assertTrue(bolts(player).isEmpty(), "A missing caster cancels later rain drops");
                h.succeed();
            } finally {
                bolts(player).forEach(Entity::discard);
                target.discard();
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "fireball_tiers")
    public static void homingOrbsStopAtAStoneWall(GameTestHelper h) {
        var player = caster(h);
        var target = enemy(h, player.position().add(0, 0, 10));
        var wall = BlockPos.containing(player.position().add(0, 0, 5));
        for (int x = -4; x <= 4; x++) for (int y = 0; y <= 4; y++) {
            h.getLevel().setBlockAndUpdate(wall.offset(x, y, 0), Blocks.STONE.defaultBlockState());
        }
        cast(player, "flame_demon_wrath");
        h.runAfterDelay(12, () -> {
            try {
                h.assertTrue(target.getHealth() == 300 && FireHeat.of(target) == 0,
                        "The wall blocks giant homing fireballs and heat");
                h.succeed();
            } finally {
                for (int x = -4; x <= 4; x++) for (int y = 0; y <= 4; y++) {
                    h.getLevel().setBlockAndUpdate(wall.offset(x, y, 0), Blocks.AIR.defaultBlockState());
                }
                bolts(player).forEach(Entity::discard);
                target.discard();
                player.discard();
            }
        });
    }
}
