package com.tnc.tnc.magic.fire;

import com.tnc.tnc.magic.TNShockwaveEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Real registered fire hands and actual level entity lookup; no renderer or mock damage methods. */
@GameTestHolder("tncfirerefine")
@PrefixGameTestTemplate(false)
public final class FireHandSafetyGameTests {
    private static final String TEMPLATE = "building_test_empty";
    private static final String ARENA = "fireball_test_empty";
    private FireHandSafetyGameTests() {}

    private static Vec3 center(GameTestHelper h) {
        var p = h.absolutePos(new BlockPos(2, 2, 2));
        return new Vec3(p.getX() + .5, p.getY(), p.getZ() + .5);
    }

    private static Vec3 arena(GameTestHelper h) {
        var floor = h.absolutePos(new BlockPos(16, 3, 16));
        var chunk = new ChunkPos(floor);
        return new Vec3(chunk.getMinBlockX() + 6.5, floor.getY(), chunk.getMinBlockZ() + 8.5);
    }

    private static ServerPlayer caster(GameTestHelper h, Vec3 at) {
        // GameTestHelper's built-in mock hardcodes isCreative=true/isSpectator=false,
        // so it cannot verify survival self-damage or spectator cancellation.
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "FireHandTest"));
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(player.server,
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) {}
        };
        player.setGameMode(GameType.SURVIVAL);
        player.setInvulnerable(false);
        player.setPos(at);
        h.getLevel().addNewPlayer(player);
        h.assertTrue(player.level() == h.getLevel(), "Mock caster must belong to this real test level");
        h.assertTrue(h.getLevel().getEntity(player.getUUID()) == player,
                "Production hand resolves its caster through the actual UUID entity table");
        h.assertTrue(!player.isCreative() && !player.isSpectator(), "Safety fixture is a real survival player");
        return player;
    }

    private static Zombie enemy(GameTestHelper h, Vec3 at) {
        Zombie target = EntityType.ZOMBIE.create(h.getLevel());
        h.assertTrue(target != null, "Zombie fixture must be registered");
        target.setPos(at); target.setNoAi(true); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        target.setHealth(100);
        h.assertTrue(h.getLevel().addFreshEntity(target), "Enemy must enter the real level");
        return target;
    }

    private static void tickAt(TNFlameHandEntity hand, int tick) {
        hand.tickCount = tick;
        hand.tick();
    }

    private static void cleanup(GameTestHelper h, Vec3 at, Entity... entities) {
        var owners = new java.util.HashSet<UUID>();
        for (var entity : entities) if (entity instanceof ServerPlayer player) owners.add(player.getUUID());
        for (var entity : h.getLevel().getAllEntities()) {
            if (entity instanceof TNFlameHandEntity hand && owners.contains(hand.visualCaster())) hand.discard();
        }
        for (Entity entity : entities) if (entity != null) {
            if (entity instanceof net.minecraft.world.entity.LivingEntity living) FireHeat.clear(living);
            entity.discard();
        }
        // Null-target expiry deliberately reaches the burst: remove only its nearby temporary aftermath.
        AABB local = new AABB(at, at).inflate(6);
        h.getLevel().getEntitiesOfClass(TNShockwaveEntity.class, local).forEach(Entity::discard);
        h.getLevel().getEntitiesOfClass(TNLavaFieldEntity.class, local).forEach(Entity::discard);
    }

    @GameTest(template=ARENA, timeoutTicks=40, batch="fire_hand_tiers")
    public static void secondTierSeeksTwoEnemiesAndThirdTierReachesFurther(GameTestHelper h) {
        Vec3 at = arena(h);
        ServerPlayer player = caster(h, at.add(0, 0, -6));
        player.setNoGravity(true);
        player.setYRot(0); player.setXRot(0);
        Zombie aimed = enemy(h, at), nearby = enemy(h, at.add(4, 0, 0)), distant = enemy(h, at.add(9, 0, 0));
        try {
            h.assertTrue(player.hasLineOfSight(aimed) && player.hasLineOfSight(nearby),
                    "The arena leaves both T2 targets unobstructed");
            TNFireHands.cast(player, net.minecraft.resources.ResourceLocation.parse("tnc:blaze_clutch"));
            var hands = h.getLevel().getEntitiesOfClass(TNFlameHandEntity.class, new AABB(at, at).inflate(20),
                    hand -> player.getUUID().equals(hand.visualCaster()));
            h.assertTrue(hands.size() == 2 && hands.stream().allMatch(hand -> hand.target() != null),
                    "T2 automatically raises two hands against distinct nearby enemies");
            h.assertTrue(hands.get(0).target() != hands.get(1).target()
                    && hands.stream().noneMatch(hand -> hand.target() == distant), "T2 selects distinct targets inside six blocks");
            hands.forEach(Entity::discard);
            nearby.discard();
            TNFireHands.cast(player, net.minecraft.resources.ResourceLocation.parse("tnc:molten_crush"));
            hands = h.getLevel().getEntitiesOfClass(TNFlameHandEntity.class, new AABB(at, at).inflate(20),
                    hand -> player.getUUID().equals(hand.visualCaster()));
            h.assertTrue(hands.size() == 2 && hands.stream().anyMatch(hand -> hand.target() == distant),
                    "T3 reaches the enemy nine blocks from the aimed target");
            h.succeed();
        } finally { cleanup(h, at, aimed, nearby, distant, player); }
    }

    @GameTest(template=ARENA, timeoutTicks=100, batch="fire_hand_tiers")
    public static void fourthTierContinuesRaisingHandsAndRepeatedCastsStayBounded(GameTestHelper h) {
        Vec3 at = arena(h);
        ServerPlayer player = caster(h, at.add(0, 0, -6));
        player.setNoGravity(true);
        player.setYRot(0); player.setXRot(0);
        Zombie victim = enemy(h, at);
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        victim.setHealth(1000);
        TNFireHands.cast(player, net.minecraft.resources.ResourceLocation.parse("tnc:inferno_clench"));
        h.runAtTickTime(45, () -> {
            try {
                var area = new AABB(at, at).inflate(20);
                var hands = h.getLevel().getEntitiesOfClass(TNFlameHandEntity.class, area,
                        hand -> player.getUUID().equals(hand.visualCaster()));
                h.assertTrue(hands.size() == 4, "The sustained T4 field raises a second pair after two seconds");
                TNFireHands.cast(player, net.minecraft.resources.ResourceLocation.parse("tnc:inferno_clench"));
                h.assertTrue(h.getLevel().getEntitiesOfClass(TNFlameHandEntity.class, area,
                        hand -> player.getUUID().equals(hand.visualCaster())).size() == 4,
                        "Recasting cannot exceed four active hands per caster");
                h.succeed();
            } finally { cleanup(h, at, victim, player); }
        });
    }

    @GameTest(template=ARENA, timeoutTicks=40, batch="fire_hand_tiers")
    public static void giantHandPullsHostilesAndBurstsAtFortyHeatAfterFirstCrush(GameTestHelper h) {
        Vec3 at = arena(h);
        ServerPlayer player = caster(h, at.add(0, 0, -6));
        player.setNoGravity(true);
        Zombie held = enemy(h, at), pulled = enemy(h, at.add(4, 0, 0));
        held.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); held.setHealth(1000);
        var hand = TNFlameHandEntity.spawn(h.getLevel(), player, held, at, 5, 0, false);
        try {
            h.assertTrue(player.hasLineOfSight(pulled), "The pulled enemy is visible in the arena");
            h.assertTrue(hand.gripHeight() > 5 && com.tnc.tnc.client.FireHandPose.scale(5) >= 4,
                    "The giant hand's model and grip height both increase");
            tickAt(hand, 20);
            h.assertTrue(pulled.getDeltaMovement().x < 0, "T5 pulls nearby hostile enemies toward its palm: "
                    + pulled.getDeltaMovement() + ", hand at " + hand.position() + ", victim at " + pulled.position());
            FireHeat.add(held, player, 12);
            held.invulnerableTime = 20;
            tickAt(hand, 36);
            h.assertTrue(held.getHealth() < 1000, "Crush damage lands even while burn damage's immunity window is active");
            h.assertTrue(FireHeat.of(held) >= 40 && hand.phase() >= 62F / TNFlameHandEntity.LIFE,
                    "T5 begins its explosion at forty heat after the first crush");
            h.assertTrue(!h.getLevel().getEntitiesOfClass(TNLavaFieldEntity.class, new AABB(at, at).inflate(16)).isEmpty(),
                    "An early giant-hand burst creates its actual aftermath");
            h.succeed();
        } finally { cleanup(h, at, hand, held, pulled, player); }
    }

    @GameTest(template=TEMPLATE, timeoutTicks=40, batch="fire_hand_safety")
    public static void emptyCastCompletesAllBeatsWithoutNullTargetCrash(GameTestHelper h) {
        Vec3 at = center(h);
        ServerPlayer player = caster(h, at.add(0, 0, 1));
        TNFlameHandEntity hand = null;
        try {
            hand = TNFlameHandEntity.spawn(h.getLevel(), player, null, at, 1, 0, false);
            h.assertTrue(hand.target() == null, "Empty cast intentionally has no victim");
            h.assertTrue(player.getUUID().equals(hand.visualCaster()), "Visual caster is synchronized at spawn");
            float playerHealth = player.getHealth();
            for (int tick : new int[]{1, 26, 36, 46, 56, 62}) {
                tickAt(hand, tick);
                h.assertTrue(!hand.isRemoved(), "Null target must not prematurely remove a valid visual cast at " + tick);
            }
            tickAt(hand, TNFlameHandEntity.LIFE);
            h.assertTrue(hand.isRemoved(), "Empty hand expires at the real production lifetime");
            h.assertTrue(player.getHealth() == playerHealth, "Empty hand does not hurt its caster");
            h.succeed();
        } finally { cleanup(h, at, hand, player); }
    }

    @GameTest(template=TEMPLATE, timeoutTicks=40, batch="fire_hand_safety")
    public static void realCrushDamagesEnemyButProtectsCasterTamePetAndTeamAlly(GameTestHelper h) {
        Vec3 at = center(h);
        ServerPlayer player = caster(h, at.add(0, 0, 1));
        Zombie victim = enemy(h, at), ally = enemy(h, at.add(-.6, 0, .5));
        var wolf = EntityType.WOLF.create(h.getLevel());
        h.assertTrue(wolf != null, "Wolf fixture must be registered");
        wolf.setTame(true); wolf.setOwnerUUID(player.getUUID());
        wolf.setNoAi(true); wolf.setNoGravity(true); wolf.setPos(at.add(.6, 0, .5));
        h.assertTrue(h.getLevel().addFreshEntity(wolf), "Tame wolf must enter the real level");
        var scoreboard = h.getLevel().getScoreboard();
        var team = scoreboard.addPlayerTeam("fh-" + UUID.randomUUID().toString().substring(0, 8));
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(ally.getScoreboardName(), team);
        TNFlameHandEntity hand = null;
        try {
            float ownerHealth = player.getHealth(), wolfHealth = wolf.getHealth(), allyHealth = ally.getHealth();
            hand = TNFlameHandEntity.spawn(h.getLevel(), player, victim, at, 1, 0, false);
            tickAt(hand, 36);
            h.assertTrue(victim.getHealth() < 100, "Real production crush must actually damage the hostile victim");
            h.assertTrue(player.getHealth() == ownerHealth, "Caster inside the crush volume remains unharmed");
            h.assertTrue(wolf.getHealth() == wolfHealth, "Owned tame wolf inside the crush volume remains unharmed");
            h.assertTrue(ally.getHealth() == allyHealth, "Scoreboard ally inside the crush volume remains unharmed");
            h.succeed();
        } finally {
            scoreboard.removePlayerTeam(team);
            cleanup(h, at, hand, victim, ally, wolf, player);
        }
    }

    @GameTest(template=TEMPLATE, timeoutTicks=40, batch="fire_hand_safety")
    public static void blockedGripDoesNotTeleportVictimInsideSolidBlocks(GameTestHelper h) {
        Vec3 at = center(h), start = at.add(2, 0, 0);
        ServerPlayer player = caster(h, at.add(0, 0, 1));
        Zombie victim = enemy(h, start);
        TNFlameHandEntity hand = TNFlameHandEntity.spawn(h.getLevel(), player, victim, at, 1, 0, false);
        Vec3 feet = at.add(0, hand.gripHeight() - victim.getBbHeight() * .5, 0);
        BlockPos obstruction = BlockPos.containing(feet);
        var original = h.getLevel().getBlockState(obstruction);
        try {
            h.assertTrue(h.getLevel().noCollision(victim, victim.getBoundingBox().move(feet.subtract(victim.position()))),
                    "Fixture begins with an unobstructed grip destination");
            tickAt(hand, 26);
            h.assertTrue(victim.position().distanceToSqr(feet) < 1.0e-6,
                    "Positive control: valid live hand really moves its victim into the palm");
            victim.setPos(start);
            h.getLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
            h.assertTrue(!h.getLevel().noCollision(victim, victim.getBoundingBox().move(feet.subtract(victim.position()))),
                    "Fixture stone actually obstructs the complete destination bounding box");
            tickAt(hand, 27);
            h.assertTrue(victim.position().distanceToSqr(start) < 1.0e-6,
                    "Blocked grip must leave victim at its original, unblocked position");
            h.assertTrue(h.getLevel().getBlockState(obstruction).is(Blocks.STONE), "Grip never removes obstructing terrain");
            h.succeed();
        } finally {
            h.getLevel().setBlockAndUpdate(obstruction, original);
            cleanup(h, at, hand, victim, player);
        }
    }

    @GameTest(template=TEMPLATE, timeoutTicks=40, batch="fire_hand_safety")
    public static void lostCasterCancelsHandWithoutChangingVictimGravityOrAiFlags(GameTestHelper h) {
        Vec3 at = center(h);
        ServerPlayer player = caster(h, at.add(0, 0, 1));
        Zombie victim = enemy(h, at);
        TNFlameHandEntity hand = null;
        try {
            for (boolean originalFlag : new boolean[]{false, true}) {
                victim.setNoAi(originalFlag); victim.setNoGravity(originalFlag);
                hand = TNFlameHandEntity.spawn(h.getLevel(), player, victim, at, 1, 0, false);
                player.setGameMode(GameType.SPECTATOR);
                tickAt(hand, 26);
                h.assertTrue(hand.isRemoved(), "Spectator caster immediately cancels its active hand");
                h.assertTrue(victim.isNoAi() == originalFlag && victim.isNoGravity() == originalFlag,
                        "Cancellation preserves both original persistent AI/gravity flags: " + originalFlag);
                player.setGameMode(GameType.SURVIVAL);
            }
            hand = TNFlameHandEntity.spawn(h.getLevel(), player, victim, at, 1, 0, false);
            player.discard();
            tickAt(hand, 26);
            h.assertTrue(hand.isRemoved(), "Removed caster cannot leave a hand holding its former victim");
            h.assertTrue(victim.isNoAi() && victim.isNoGravity(), "Removed caster also leaves pre-existing flags untouched");
            h.succeed();
        } finally { cleanup(h, at, hand, victim, player); }
    }

    @GameTest(template=TEMPLATE, timeoutTicks=40, batch="fire_hand_safety")
    public static void laterTargetHeatUpdatesTheSyncedHandPeak(GameTestHelper h) {
        Vec3 at = center(h);
        ServerPlayer player = caster(h, at.add(0, 0, 1));
        Zombie victim = enemy(h, at);
        TNFlameHandEntity hand = TNFlameHandEntity.spawn(h.getLevel(), player, victim, at, 1, 0, false);
        try {
            h.assertTrue(hand.heat() == 0, "Hand begins without cached heat");
            FireHeat.add(victim, player, 20);
            tickAt(hand, 1);
            h.assertTrue(hand.heat() >= 20, "Later heat from the actual heat service reaches the synchronized hand field");
            FireHeat.add(victim, player, 35);
            tickAt(hand, 2);
            h.assertTrue(hand.heat() >= 55 && hand.heat() <= FireHeat.MAX,
                    "Further heating updates the visual/burst peak without crossing the heat cap");
            float peak = hand.heat();
            FireHeat.consume(victim);
            tickAt(hand, 3);
            h.assertTrue(hand.heat() == peak, "Hand retains its reached peak when another spell consumes the target heat");
            h.succeed();
        } finally { cleanup(h, at, hand, victim, player); }
    }
}
