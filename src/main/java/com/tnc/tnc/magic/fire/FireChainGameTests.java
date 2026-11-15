package com.tnc.tnc.magic.fire;

import com.tnc.tnc.magic.Element;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.TNFireMechanics;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
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
public final class FireChainGameTests {
    private static final String TEMPLATE = "building_test_empty";

    private static Vec3 origin(GameTestHelper h) {
        return Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 3, 2)));
    }

    private static ServerPlayer player(GameTestHelper h, Vec3 at) {
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "FireChainTest"));
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
        target.setBaby(false);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        target.setHealth(200);
        target.setPos(at);
        h.getLevel().addFreshEntity(target);
        return target;
    }

    private static void cast(ServerPlayer player, String spell) {
        TNFireRework.cast(player, ResourceLocation.fromNamespaceAndPath("tnc", spell));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 90, batch = "fire_chain_combat")
    public static void seedKeepsItsFuseAndSpendsHeatAppliedAfterCasting(GameTestHelper h) {
        Vec3 at = origin(h);
        ServerPlayer caster = player(h, at);
        Zombie victim = enemy(h, at.add(0, 0, 4));
        cast(caster, "lava_fireball");
        h.runAfterDelay(30, () -> {
            h.assertTrue(victim.getHealth() == 200, "The fuse has not exploded early");
            FireHeat.add(victim, caster, 46);
        });
        h.runAfterDelay(64, () -> {
            h.assertTrue(victim.getHealth() < 80, "The explosion uses the heat added during the fuse");
            h.assertTrue(FireHeat.of(victim) > 30 && FireHeat.of(victim) < 61, "The fuse spends only part of that heat");
            caster.discard();
            victim.discard();
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 45, batch = "fire_chain_combat")
    public static void wallStopsProjectileAndAimedDetonation(GameTestHelper h) {
        Vec3 at = origin(h);
        ServerPlayer caster = player(h, at);
        Zombie victim = enemy(h, at.add(0, 0, 4));
        BlockPos obstruction = BlockPos.containing(at.add(0, 0, 2));
        h.getLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(obstruction.above(), Blocks.STONE.defaultBlockState());
        FireHeat.add(victim, caster, 36);
        cast(caster, "sun_ray");
        cast(caster, "fireball");
        h.runAfterDelay(12, () -> {
            try {
                h.assertTrue(victim.getHealth() == 200 && FireHeat.of(victim) == 36, "Solid wall stops both attacks");
                h.getLevel().setBlockAndUpdate(obstruction.above(), Blocks.AIR.defaultBlockState());
                cast(caster, "fireball");
                h.assertTrue(victim.getHealth() < 200 && FireHeat.of(victim) > 0 && FireHeat.of(victim) < 36,
                        "Open aim damages the target and retains some heat");
                h.succeed();
            } finally {
                h.getLevel().setBlockAndUpdate(obstruction, Blocks.AIR.defaultBlockState());
                h.getLevel().setBlockAndUpdate(obstruction.above(), Blocks.AIR.defaultBlockState());
                caster.discard();
                victim.discard();
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180, batch = "fire_chain_combat")
    public static void fireballBurnsOnlyTheTargetAndStoresFullHeat(GameTestHelper h) {
        Vec3 at = origin(h);
        ServerPlayer caster = player(h, at);
        Zombie victim = enemy(h, at.add(0, 0, 5));
        Zombie neighbor = enemy(h, at.add(1.5, 0, 5));
        cast(caster, "sun_ray");
        h.assertTrue(victim.getHealth() == 200 && FireHeat.of(victim) == 0, "The projectile has to travel");
        h.runAfterDelay(12, () -> {
            h.assertTrue(FireHeat.of(victim) == 16 && victim.getHealth() < 200, "The orb damages and primes its victim");
            FireHeat.add(victim, caster, 82);
        });
        h.runAfterDelay(38, () -> {
            h.assertTrue(FireHeat.of(victim) == FireHeat.MAX, "Burn pulses add heat and keep the full meter");
            h.assertTrue(FireHeat.of(neighbor) == 0 && neighbor.getHealth() == 200,
                    "The impact wave and full meter cause no splash damage");
        });
        h.runAfterDelay(115, () -> {
            h.assertTrue(FireHeat.of(victim) == FireHeat.MAX, "Heat is retained after the burn");
            caster.discard();
            victim.discard();
            neighbor.discard();
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220, batch = "fire_chain_combat")
    public static void learnedFireTierAndAffinitySaveHeat(GameTestHelper h) {
        Vec3 at = origin(h);
        ServerPlayer caster = player(h, at);
        Zombie novice = enemy(h, at.add(0, 0, 5));
        Zombie expert = enemy(h, at.add(2.4, 0, 5));
        var stone = MagicStone.getOrNull(caster);
        h.assertTrue(stone != null, "The server player has a magic stone");
        stone.setAffinity(Element.FIRE, 0);
        FireHeat.add(novice, caster, 80);
        cast(caster, "fireball");
        float noviceRemaining = FireHeat.of(novice);

        stone.setAffinity(Element.FIRE, 6);
        h.assertTrue(stone.learn(ResourceLocation.fromNamespaceAndPath("tnc", "solar_judgment")), "Fire T5 is learned");
        h.assertTrue(FireHeat.control(caster) == 11, "The real stone contributes affinity plus its learned tier");
        FireHeat.add(expert, caster, 80);
        caster.setYRot((float) -Math.toDegrees(Math.atan2(2.4, 5)));
        cast(caster, "fireball");
        float expertRemaining = FireHeat.of(expert);
        h.assertTrue(noviceRemaining < expertRemaining && expertRemaining < 80, "Progression reduces consumption");
        h.assertTrue(Math.abs(novice.getHealth() - expert.getHealth()) < .01, "Saving heat does not reduce damage");
        h.runAfterDelay(185, () -> {
            h.assertTrue(FireHeat.of(novice) < noviceRemaining, "Novice heat has started cooling");
            h.assertTrue(FireHeat.of(expert) == expertRemaining, "Expert heat has not cooled yet");
            caster.discard();
            novice.discard();
            expert.discard();
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "fire_chain_combat")
    public static void formsUseStatusDurationAndDistinguishFireFromMelee(GameTestHelper h) {
        Vec3 at = origin(h);
        ServerPlayer caster = player(h, at);
        Zombie victim = enemy(h, at.add(0, 0, 3));
        try {
            caster.addEffect(new MobEffectInstance(TNFireMechanics.EMBER_BURN.get(), 40));
            TNFireRework.damage(caster, victim, 2);
            h.assertTrue(FireHeat.of(victim) == 0, "An owned fire spell is not a melee hit");
            victim.invulnerableTime = 0;
            victim.hurt(victim.damageSources().playerAttack(caster), 4);
            h.assertTrue(FireHeat.of(victim) == 10, "An actual melee strike adds ten heat");
            caster.removeEffect(TNFireMechanics.EMBER_BURN.get());
            caster.addEffect(new MobEffectInstance(TNFireMechanics.BLAZE_BURN.get(), 40));
            h.assertTrue(TNFireForms.heatMultiplier(caster) == 1.5F, "Burning Essence boosts heat");
            caster.removeEffect(TNFireMechanics.BLAZE_BURN.get());
            h.assertTrue(TNFireForms.tier(caster) == 0, "Removing the effect ends the form");
            h.succeed();
        } finally {
            caster.discard();
            victim.discard();
        }
    }
}
