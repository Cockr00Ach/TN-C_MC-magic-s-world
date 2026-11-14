package com.tnc.tnc.world.yan;

import com.tnc.tnc.boss.YanDarkBossEntity;
import com.tnc.tnc.boss.YanExecutionRules;
import com.tnc.tnc.journey.JourneyData;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.network.MagicStoneNetwork;
import com.tnc.tnc.npc.TNNpcs;
import com.tnc.tnc.world.SkyIslandWorlds;
import com.tnc.tnc.world.SkyhavenTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "tnc")
public final class YanPrologue {
    public static final ResourceKey<Level> WORLD = ResourceKey.create(Registries.DIMENSION, YanPrologueScript.DIMENSION);
    private static final String COMPLETE = "YanPrologueComplete";
    private static final String KIT = "YanPrologueInventory";
    private static final String ACTOR = "TncYanPrologueActor";
    private static final UUID KNOCKBACK = UUID.fromString("f650c3a4-c87d-4b0a-a4ec-71c0ab8a329a");
    private static final TicketType<UUID> ARENA_TICKET = TicketType.create("tnc_yan_prologue", UUID::compareTo);
    static final BlockPos ENTRY = new BlockPos(0, YanSanctuaryLayout.ALTAR_HEIGHT + 1, 11);
    private static final Map<UUID, Scene> SCENES = new HashMap<>();
    enum Phase { STORY, LOADING, ARRIVING, COMBAT, DEFEAT, REWIND }

    static final class Scene {
        final ServerPlayer player;
        final ServerLevel arena;
        final long token;
        Phase phase = Phase.STORY;
        Actor boss;
        int age;
        boolean rewindAcknowledged;
        Vec3 executionPosition, defeatPosition;
        BlockPos altar = YanSanctuaryLayout.BOSS;
        BlockPos entry = ENTRY;
        Scene(ServerPlayer player, ServerLevel arena) {
            this.player = player;
            this.arena = arena;
            this.token = player.getRandom().nextLong();
        }
    }

    static final class Actor extends YanDarkBossEntity {
        Actor(ServerLevel level, UUID owner) {
            super(TNNpcs.YAN_DARK.get(), level);
            getPersistentData().putUUID(ACTOR, owner);
        }
        @Override public boolean shouldBeSaved() { return false; }
        @Override protected boolean shouldDespawnInPeaceful() { return false; }
        @Override protected boolean ticksExecutionInAi() { return false; }

        boolean tryExecution() { return maybeStartExecution(); }
        void advanceExecution() { if (execTick() >= 0) tickExecution(); }
    }

    private YanPrologue() {}

    public static boolean active(ServerPlayer player) {
        return SCENES.containsKey(player.getUUID());
    }

    static boolean inCombat(ServerPlayer player) {
        var scene = SCENES.get(player.getUUID());
        return scene != null && scene.player == player && scene.phase == Phase.COMBAT && player.level() == scene.arena;
    }

    static boolean canCast(ServerPlayer player) {
        if (!inCombat(player)) return false;
        var boss = SCENES.get(player.getUUID()).boss;
        return boss == null || boss.execTick() < YanExecutionRules.AT_PLAYER;
    }

    public static boolean usingKit(ServerPlayer player) {
        var scene = SCENES.get(player.getUUID());
        return scene != null && scene.player == player
                && (scene.phase == Phase.ARRIVING || scene.phase == Phase.COMBAT
                || scene.phase == Phase.DEFEAT || scene.phase == Phase.REWIND);
    }

    public static boolean awaiting(ServerPlayer player) {
        if (active(player)) return true;
        if (!SkyIslandWorlds.prologueEnabled(player.server) || player.server.getLevel(WORLD) == null
                || player.isCreative() || player.isSpectator()
                || player instanceof net.minecraftforge.common.util.FakePlayer) return false;
        var record = JourneyData.get(player.server).player(player.getUUID());
        return !record.getBoolean(COMPLETE) && !record.getBoolean("Named");
    }

    /** Called before the normal island-arrival scheduler. */
    public static boolean begin(ServerPlayer player) {
        if (active(player)) return true;
        if (!SkyIslandWorlds.prologueEnabled(player.server)) return false;
        var record = JourneyData.get(player.server).player(player.getUUID());
        recoverKit(player, record);
        if (!awaiting(player)) return false;
        var scene = reserveArena(player, player.server.getLevel(WORLD));
        YanPrologueNetwork.send(player, scene.token, YanPrologueNetwork.Stage.STORY);
        return true;
    }

    static Scene reserveArena(ServerPlayer player, ServerLevel level) {
        var scene = new Scene(player, level);
        // Keep concurrent newcomers far enough apart that Yan's spells cannot cross encounters.
        int lane = 0;
        while (occupied(level, lane)) lane++;
        scene.entry = ENTRY.offset(lane * YanSanctuaryGenerator.LANE_WIDTH, 0, 0);
        scene.altar = YanSanctuaryLayout.BOSS.offset(lane * YanSanctuaryGenerator.LANE_WIDTH, 0, 0);
        level.getChunkSource().addRegionTicket(ARENA_TICKET, new ChunkPos(scene.altar), 2, player.getUUID());
        SCENES.put(player.getUUID(), scene);
        return scene;
    }

    private static boolean occupied(ServerLevel arena, int lane) {
        return SCENES.values().stream().anyMatch(scene -> scene.arena == arena && scene.entry.getX() == lane * YanSanctuaryGenerator.LANE_WIDTH);
    }

    static void advance(ServerPlayer player, long token) {
        var scene = SCENES.get(player.getUUID());
        if (scene == null || scene.player != player || scene.token != token || !player.isAlive()) return;
        switch (scene.phase) {
            case STORY -> { if (scene.age >= 60) loadArena(scene); }
            case ARRIVING -> startCombat(scene);
            case REWIND -> {
                if (scene.age >= YanPrologueScript.REWIND_TICKS - 4) scene.rewindAcknowledged = true;
            }
            default -> {}
        }
    }

    private static void loadArena(Scene scene) {
        scene.phase = Phase.LOADING;
        scene.age = 0;
        var server = scene.player.server;
        // The altar and arrival are in separate chunks; request both without blocking the game thread.
        var entry = scene.arena.getChunkSource().getChunkFuture(scene.entry.getX() >> 4, scene.entry.getZ() >> 4, ChunkStatus.FULL, true);
        var altar = scene.arena.getChunkSource().getChunkFuture(scene.altar.getX() >> 4, scene.altar.getZ() >> 4, ChunkStatus.FULL, true);
        entry.thenCombine(altar, (a, b) -> a.left().isPresent() && b.left().isPresent())
                .whenCompleteAsync((ready, error) -> {
                    var player = scene.player;
                    if (SCENES.get(player.getUUID()) != scene || player.hasDisconnected() || scene.phase != Phase.LOADING) return;
                    if (error != null || !Boolean.TRUE.equals(ready)) {
                        rewind(scene);
                        return;
                    }
                    var point = Vec3.atBottomCenterOf(scene.entry);
                    if (!scene.arena.noCollision(player, player.getBoundingBox().move(point.subtract(player.position())))) {
                        rewind(scene);
                        return;
                    }
                    scene.phase = Phase.ARRIVING;
                    scene.age = 0;
                    equip(player);
                    player.teleportTo(scene.arena, point.x, point.y, point.z, 180, 0);
                    player.setDeltaMovement(Vec3.ZERO);
                    player.fallDistance = 0;
                    YanPrologueNetwork.send(player, scene.token, YanPrologueNetwork.Stage.FIGHT);
                }, server);
    }

    static void startCombat(Scene scene) {
        if (scene.player.level() != scene.arena) return;
        var boss = new Actor(scene.arena, scene.player.getUUID());
        var at = Vec3.atBottomCenterOf(scene.altar);
        boss.moveTo(at.x, at.y, at.z, 0, 0);
        boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(80);
        boss.setHealth(boss.getMaxHealth());
        boss.restrictTo(scene.altar, 30);
        boss.setPersistenceRequired();
        scene.boss = boss;
        scene.phase = Phase.COMBAT;
        scene.age = 0;
        if (!scene.arena.addFreshEntity(boss)) {
            rewind(scene);
            return;
        }
        boss.setTarget(scene.player);
        scene.player.displayClientMessage(Component.literal("序幕法杖已就绪。使用法术栏施放八个法术。"), true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (var scene : List.copyOf(SCENES.values())) {
            if (scene.player.server != event.getServer()) continue;
            if (scene.player.hasDisconnected()) {
                abandon(scene);
                continue;
            }
            scene.age++;
            switch (scene.phase) {
                case STORY -> { if (scene.age > 1800) loadArena(scene); }
                case LOADING, ARRIVING -> { if (scene.age > 1800) rewind(scene); }
                case COMBAT -> tickFight(scene);
                case DEFEAT -> {
                    if (scene.boss != null && !scene.boss.isRemoved()) scene.boss.advanceExecution();
                    holdPlayer(scene, scene.defeatPosition);
                    if (scene.age >= YanPrologueScript.DEFEAT_TICKS) rewind(scene);
                }
                case REWIND -> {
                    if (scene.defeatPosition != null) holdPlayer(scene, scene.defeatPosition);
                    if (scene.age >= YanPrologueScript.REWIND_TICKS
                            && (scene.rewindAcknowledged || scene.age >= YanPrologueScript.REWIND_TICKS + 100)) finish(scene);
                }
            }
        }
    }

    private static void tickFight(Scene scene) {
        var player = scene.player;
        var boss = scene.boss;
        if (player.level() != scene.arena || boss == null || boss.isRemoved()) {
            rewind(scene);
            return;
        }
        if (boss.execTick() < 0 && player.distanceToSqr(Vec3.atCenterOf(scene.entry)) > 54 * 54) {
            var at = Vec3.atBottomCenterOf(scene.entry);
            player.teleportTo(at.x, at.y, at.z);
        }
        if (boss.getTarget() != player) boss.setTarget(player);
        if (boss.execTick() >= YanExecutionRules.AT_PLAYER && boss.execTick() <= YanExecutionRules.AT_IMPACT) {
            if (scene.executionPosition == null) scene.executionPosition = player.position();
            holdPlayer(scene, scene.executionPosition);
        }
        if (boss.execTick() >= 0) {
            boss.advanceExecution();
            return;
        }
        if (scene.age % 20 == 0) healPulse(player);
        if (boss.tryExecution()) {
            player.displayClientMessage(Component.literal("衍 · 万雷归体"), true);
        } else if (boss.execUsed()) {
            // Recover the story if the already-started execution loses its target or the impact is blocked.
            defeat(scene);
        }
    }

    private static void defeat(Scene scene) {
        if (scene.phase != Phase.COMBAT) return;
        var player = scene.player;
        var feet = player.position();
        var floor = scene.arena.clip(new net.minecraft.world.level.ClipContext(feet.add(0, .2, 0),
                feet.add(0, -32, 0), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        scene.defeatPosition = floor.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                ? floor.getLocation() : Vec3.atBottomCenterOf(scene.entry);
        scene.phase = Phase.DEFEAT;
        scene.age = 0;
        // Hold a visual death until the time rewind. A vanilla respawn would clone the player and kit.
        player.setHealth(1);
        player.stopUsingItem();
        player.clearFire();
        YanPrologueNetwork.sendDefeat(player, scene.token, scene.defeatPosition, player.getYRot());
        holdPlayer(scene, scene.defeatPosition);
    }

    private static void holdPlayer(Scene scene, Vec3 point) {
        var player = scene.player;
        if (player.level() != scene.arena) return;
        if (player.distanceToSqr(point) > .04) {
            player.teleportTo(point.x, point.y, point.z);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
    }

    private static void rewind(Scene scene) {
        if (scene.phase == Phase.REWIND) return;
        scene.phase = Phase.REWIND;
        scene.age = 0;
        var player = scene.player;
        player.setHealth(Math.max(1, player.getHealth()));
        player.setDeltaMovement(Vec3.ZERO);
        if (scene.boss != null) {
            scene.boss.setTarget(null);
            scene.boss.setNoAi(true);
        }
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                SoundSource.PLAYERS, .65F, .62F);
        YanPrologueNetwork.send(player, scene.token, YanPrologueNetwork.Stage.REWIND);
    }

    private static void finish(Scene scene) {
        var player = scene.player;
        var data = JourneyData.get(player.server);
        var record = data.player(player.getUUID());
        recoverKit(player, record);
        record.putBoolean(COMPLETE, true);
        data.setDirty();
        abandon(scene);
        YanPrologueNetwork.send(player, scene.token, YanPrologueNetwork.Stage.END);
        SkyhavenTravel.login(player);
    }

    static void abandon(Scene scene) {
        SCENES.remove(scene.player.getUUID(), scene);
        scene.arena.getChunkSource().removeRegionTicket(ARENA_TICKET, new ChunkPos(scene.altar), 2, scene.player.getUUID());
        if (scene.boss != null) scene.boss.discard();
        var area = new net.minecraft.world.phys.AABB(scene.entry).inflate(88, 64, 88);
        for (var entity : scene.arena.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, area,
                mob -> scene.player.getUUID().equals(summoner(mob)))) entity.discard();
    }

    static void equip(ServerPlayer player) {
        var data = JourneyData.get(player.server);
        var record = data.player(player.getUUID());
        player.closeContainer();
        var original = new CompoundTag();
        original.put("Items", player.getInventory().save(new ListTag()));
        original.putInt("Slot", player.getInventory().selected);
        original.putInt("Mode", player.gameMode.getGameModeForPlayer().getId());
        original.putFloat("Health", player.getHealth());
        original.putInt("Food", player.getFoodData().getFoodLevel());
        original.putFloat("Saturation", player.getFoodData().getSaturationLevel());
        var magic = MagicStone.getOrNull(player);
        if (magic != null) {
            original.putInt("Mana", magic.getMana());
            original.putInt("MaxMana", magic.getMaxMana());
        }
        var effects = new ListTag();
        for (var effect : player.getActiveEffects()) effects.add(effect.save(new CompoundTag()));
        original.put("Effects", effects);
        record.put(KIT, original);
        data.setDirty();
        player.server.overworld().getDataStorage().save();
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, YanPrologueSpells.wand());
        player.getInventory().setItem(1, new ItemStack(Items.DIAMOND_SWORD));
        player.getInventory().setItem(2, new ItemStack(Items.BOW));
        player.getInventory().setItem(3, new ItemStack(Items.ARROW, 32));
        player.getInventory().setItem(4, new ItemStack(Items.COOKED_BEEF, 8));
        player.getInventory().setItem(Inventory.INVENTORY_SIZE, new ItemStack(Items.IRON_BOOTS));
        player.getInventory().setItem(Inventory.INVENTORY_SIZE + 1, new ItemStack(Items.IRON_LEGGINGS));
        player.getInventory().setItem(Inventory.INVENTORY_SIZE + 2, new ItemStack(Items.IRON_CHESTPLATE));
        player.getInventory().setItem(Inventory.INVENTORY_SIZE + 3, new ItemStack(Items.IRON_HELMET));
        player.getInventory().offhand.set(0, new ItemStack(Items.SHIELD));
        player.setGameMode(GameType.ADVENTURE);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(10);
        var knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        knockback.removeModifier(KNOCKBACK);
        knockback.addTransientModifier(new AttributeModifier(KNOCKBACK, "Yan prologue knockback resistance",
                Math.max(0, .9 - knockback.getValue()), AttributeModifier.Operation.ADDITION));
        if (magic != null) {
            magic.setMaxMana(YanPrologueSpells.MAX_MANA);
            magic.setMana(YanPrologueSpells.MAX_MANA);
            syncMana(player);
        }
        player.inventoryMenu.broadcastChanges();
    }

    static void recoverKit(ServerPlayer player, CompoundTag record) {
        if (!record.contains(KIT, Tag.TAG_COMPOUND)) return;
        var original = record.getCompound(KIT);
        // The cursor stack is outside Inventory; clear it before returning the real inventory.
        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.closeContainer();
        player.getInventory().load(original.getList("Items", Tag.TAG_COMPOUND));
        player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).removeModifier(KNOCKBACK);
        com.tnc.tnc.light.TNLightChainMechanics.endGraceFlight(player);
        var magic = MagicStone.getOrNull(player);
        if (magic != null && original.contains("MaxMana", Tag.TAG_INT)) {
            magic.setMaxMana(original.getInt("MaxMana"));
            magic.setMana(original.getInt("Mana"));
            syncMana(player);
        }
        player.getInventory().selected = original.getInt("Slot");
        player.setGameMode(GameType.byId(original.getInt("Mode")));
        player.setHealth(Math.max(1, Math.min(original.getFloat("Health"), player.getMaxHealth())));
        player.getFoodData().setFoodLevel(original.getInt("Food"));
        player.getFoodData().setSaturation(original.getFloat("Saturation"));
        player.clearFire();
        player.removeAllEffects();
        for (Tag raw : original.getList("Effects", Tag.TAG_COMPOUND)) {
            var effect = net.minecraft.world.effect.MobEffectInstance.load((CompoundTag) raw);
            if (effect != null) player.addEffect(effect);
        }
        player.setAirSupply(player.getMaxAirSupply());
        player.inventoryMenu.broadcastChanges();
        record.remove(KIT);
        JourneyData.get(player.server).setDirty();
    }

    private static void syncMana(ServerPlayer player) {
        if (!(player instanceof net.minecraftforge.common.util.FakePlayer)) MagicStoneNetwork.syncTo(player);
    }

    static void healPulse(ServerPlayer player) {
        if (player.getHealth() < player.getMaxHealth()) player.heal(2);
    }

    private static UUID summoner(Entity attacker) {
        if (attacker instanceof com.tnc.tnc.dark.TNDarkSummonEntity summon) return summon.owner();
        if (attacker instanceof com.tnc.tnc.light.TNFightingAngelEntity angel) return angel.owner();
        if (attacker instanceof OwnableEntity owned) return owned.getOwnerUUID();
        return null;
    }

    private static boolean spellHit(DamageSource source) {
        return source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypeTags.IS_FIRE)
                || source.typeHolder().unwrapKey().map(key -> key.location().getNamespace().equals("spell_power")).orElse(false)
                || source.getDirectEntity() instanceof com.tnc.tnc.magic.fire.TNFlameHandEntity
                || summoner(source.getEntity()) != null;
    }

    private static boolean executionImpact(Scene scene, DamageSource source) {
        var boss = scene.boss;
        return scene.phase == Phase.COMBAT && boss != null && boss.getPhase() == 2
                && source.getEntity() == boss && source.getDirectEntity() == boss
                && boss.execTargetId() == scene.player.getId() && boss.execTick() == YanExecutionRules.AT_IMPACT + 1;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void combatDamage(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && inCombat(player)) {
            if (!executionImpact(SCENES.get(player.getUUID()), event.getSource())) event.setAmount(event.getAmount() * .10F);
        } else if (event.getEntity() instanceof Actor boss && spellHit(event.getSource())) {
            event.setAmount(event.getAmount() * 3F);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stopAtExecutionThreshold(LivingDamageEvent event) {
        if (event.getEntity() instanceof Actor boss && boss.getPhase() == 2) {
            float threshold = boss.getMaxHealth() * YanExecutionRules.TRIGGER_HEALTH_RATIO;
            event.setAmount(Math.min(event.getAmount(), Math.max(0, boss.getHealth() - threshold)));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(LivingAttackEvent event) {
        if (event.getEntity() instanceof YanDarkBossEntity boss && boss.getPersistentData().hasUUID(ACTOR)) {
            Entity attacker = event.getSource().getEntity();
            UUID caster = attacker instanceof ServerPlayer player ? player.getUUID() : summoner(attacker);
            if (!boss.getPersistentData().getUUID(ACTOR).equals(caster)
                    || boss instanceof Actor actor && actor.execUsed()) event.setCanceled(true);
        }
        if (event.getEntity() instanceof ServerPlayer player && active(player)) {
            var scene = SCENES.get(player.getUUID());
            if (scene.phase != Phase.COMBAT
                    || event.getSource().getEntity() instanceof ServerPlayer other && other != player) event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && active(player)) {
            event.setCanceled(true);
            var scene = SCENES.get(player.getUUID());
            if (executionImpact(scene, event.getSource())) {
                defeat(scene);
            } else if (scene.boss != null && scene.boss.getPhase() == 1 && scene.phase == Phase.COMBAT) {
                player.setHealth(Math.max(1, player.getMaxHealth() * .45F));
            } else player.setHealth(1);
        } else if (event.getEntity() instanceof YanDarkBossEntity boss && boss.getPersistentData().hasUUID(ACTOR)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void toss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && active(player) && player.level().dimension().equals(WORLD)) {
            player.getInventory().add(event.getEntity().getItem());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void pickup(EntityItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && active(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void cleanupActor(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(WORLD)) return;
        var entity = event.getEntity();
        if (!entity.getPersistentData().hasUUID(ACTOR)) return;
        var scene = SCENES.get(entity.getPersistentData().getUUID(ACTOR));
        if (scene == null || scene.boss != entity) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var scene = SCENES.get(player.getUUID());
        if (scene != null) {
            recoverKit(player, JourneyData.get(player.server).player(player.getUUID()));
            abandon(scene);
        }
    }

    @SubscribeEvent
    public static void stop(net.minecraftforge.event.server.ServerStoppingEvent event) {
        for (var scene : List.copyOf(SCENES.values())) {
            if (scene.player.server == event.getServer()) abandon(scene);
        }
    }
}
