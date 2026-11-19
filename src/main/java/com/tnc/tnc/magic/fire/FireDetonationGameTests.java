package com.tnc.tnc.magic.fire;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("tncfirerefine")
@PrefixGameTestTemplate(false)
public final class FireDetonationGameTests {
    private static final String TEMPLATE = "building_test_empty";

    private static ServerPlayer player(GameTestHelper helper, Vec3 at) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "FireDetonateTest"));
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(player.server,
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) {}
        };
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(at.x, at.y, at.z, 0, 0);
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    private static Zombie enemy(GameTestHelper helper, Vec3 at) {
        var victim = EntityType.ZOMBIE.create(helper.getLevel());
        victim.setNoAi(true);
        victim.setNoGravity(true);
        victim.setBaby(false);
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        victim.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        victim.setHealth(1000);
        victim.setPos(at);
        helper.getLevel().addFreshEntity(victim);
        return victim;
    }

    private static void primeStrongerHit(ServerPlayer caster, Zombie target) {
        target.invulnerableTime = 0;
        target.hurt(target.damageSources().playerAttack(caster), 500);
        target.setHealth(1000);
    }

    private static void cast(ServerPlayer player, String path) {
        TNFireRework.cast(player, ResourceLocation.fromNamespaceAndPath("tnc", path));
    }

    private static void cleanup(ServerPlayer caster, Zombie victim) {
        FireHeat.clear(victim);
        caster.discard();
        victim.discard();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "fire_detonation")
    public static void zeroFinalDamageRetainsHeatAndRestoresCooldown(GameTestHelper helper) {
        var at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
        var caster = player(helper, at);
        var victim = enemy(helper, at.add(0, 0, 4));
        java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingDamageEvent> shield = event -> {
            if (event.getEntity() == victim) event.setAmount(0);
        };
        var bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        bus.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, false,
                net.minecraftforge.event.entity.living.LivingDamageEvent.class, shield);
        try {
            FireHeat.add(victim, caster, 80);
            victim.invulnerableTime = 13;
            cast(caster, "fireball");
            helper.assertTrue(FireHeat.of(victim) == 80 && victim.getHealth() == 1000
                    && victim.invulnerableTime == 13, "Zero final damage must retain heat and the prior cooldown");
            helper.succeed();
        } finally {
            bus.unregister(shield);
            cleanup(caster, victim);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "fire_detonation")
    public static void instantDetonationsDamageAfterStrongerHitsAndPreserveHeatWhenImmune(GameTestHelper helper) {
        var at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
        var caster = player(helper, at);
        var victim = enemy(helper, at.add(0, 0, 4));
        try {
            for (String spell : new String[]{"fireball", "great_fireball"}) {
                primeStrongerHit(caster, victim);
                FireHeat.clear(victim);
                FireHeat.add(victim, caster, 80);
                cast(caster, spell);
                helper.assertTrue(victim.getHealth() < 900, spell + " deals its damage during a previous hit's cooldown");
                helper.assertTrue(FireHeat.of(victim) > 0 && FireHeat.of(victim) < 80, "Successful damage consumes heat");

                victim.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100));
                victim.setHealth(1000);
                victim.invulnerableTime = 17;
                FireHeat.clear(victim);
                FireHeat.add(victim, caster, 80);
                cast(caster, spell);
                helper.assertTrue(victim.getHealth() == 1000 && FireHeat.of(victim) == 80,
                        "A fire-immune victim takes no damage and retains heat");
                helper.assertTrue(victim.invulnerableTime == 17, "An immunity rejection restores the prior hit cooldown");
                victim.removeEffect(MobEffects.FIRE_RESISTANCE);
            }
            helper.succeed();
        } finally {
            cleanup(caster, victim);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 85, batch = "fire_detonation")
    public static void seedExplosionSurvivesTheLastBurnTicksDamageWindow(GameTestHelper helper) {
        var at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
        var caster = player(helper, at);
        var victim = enemy(helper, at.add(0, 0, 4));
        cast(caster, "lava_fireball");
        helper.runAfterDelay(57, () -> {
            FireHeat.clear(victim);
            FireHeat.add(victim, caster, 80);
            primeStrongerHit(caster, victim);
        });
        helper.runAfterDelay(64, () -> {
            try {
                helper.assertTrue(victim.getHealth() < 900 && FireHeat.of(victim) < 80,
                        "The delayed seed damages and consumes heat despite a recent stronger hit");
                helper.succeed();
            } finally {
                cleanup(caster, victim);
            }
        });
    }

    @GameTest(template = "fireball_test_empty", timeoutTicks = 40, batch = "fire_detonation_overlap")
    public static void consecutiveMoltenCoresEachResolveTheirOwnHeatDamage(GameTestHelper helper) {
        var at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(18, 3, 8)));
        var caster = player(helper, at);
        caster.setNoGravity(true);
        var anchor = enemy(helper, at.add(0, 0, 4));
        // right(+Z) points toward -X, so the first left offset lands on +X.
        var victim = enemy(helper, at.add(1.9, 0, 4.5));
        for (int x = -6; x <= 6; x++) {
            for (int z = -2; z <= 10; z++) {
                helper.getLevel().setBlockAndUpdate(BlockPos.containing(at.add(x, -1, z)), Blocks.STONE.defaultBlockState());
            }
        }
        FireHeat.add(victim, caster, 80);
        cast(caster, "molten_skyfall");
        float[] afterFirst = new float[1];
        helper.runAfterDelay(23, () -> {
            helper.assertTrue(victim.getHealth() < 950, "The first molten core damages its displayed area: "
                    + victim.getHealth() + " at " + victim.position());
            afterFirst[0] = victim.getHealth();
        });
        helper.runAfterDelay(32, () -> {
            try {
                helper.assertTrue(victim.getHealth() < afterFirst[0] - 10,
                        "The lower-heat second core still damages the overlapping victim nine ticks later: "
                                + afterFirst[0] + " to " + victim.getHealth());
                helper.succeed();
            } finally {
                cleanup(caster, victim);
                FireHeat.clear(anchor);
                anchor.discard();
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "fire_detonation")
    public static void martyrExplosionSurvivesARecentStrongerHit(GameTestHelper helper) {
        var at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
        var caster = player(helper, at);
        var victim = enemy(helper, at.add(0, 0, 4));
        FireHeat.add(victim, caster, 80);
        cast(caster, "meteor_fall");
        helper.runAfterDelay(9, () -> primeStrongerHit(caster, victim));
        helper.runAfterDelay(16, () -> {
            try {
                helper.assertTrue(victim.getHealth() < 900 && FireHeat.of(victim) < 80,
                        "The scheduled martyr explosion damages and consumes heat during a previous hit's cooldown");
                helper.succeed();
            } finally {
                cleanup(caster, victim);
            }
        });
    }
}
