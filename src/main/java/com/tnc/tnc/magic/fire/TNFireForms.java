package com.tnc.tnc.magic.fire;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNShockwaveEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 燃烧形态链（原燃烧链）接上热量经济：把"以伤换伤"改造成<b>改变你怎么点火</b>。
 *
 * <table border="1">
 *   <tr><th>档</th><th>形态</th><th>热经济里的作用</th></tr>
 *   <tr><td>1 燎肤</td><td>燎原</td><td>移动时脚下留火痕，路过的敌人被点着（+6 热/次）</td></tr>
 *   <tr><td>2 灼脉</td><td>灼脉</td><td>近战命中也会叠热（+10）</td></tr>
 *   <tr><td>3 焚元</td><td>焚元</td><td>所有火系叠热 ×1.5（点火链直接变快）</td></tr>
 *   <tr><td>4 烬魂</td><td>烬魂</td><td>致命伤时把身上的热量全炸出去，并留 1 点血</td></tr>
 *   <tr><td>5 殉命</td><td>殉命</td><td>血越少火伤越高（1 血 = +120%）</td></tr>
 * </table>
 *
 * <h2>和旧实现的关系</h2>
 * 旧的一整套（火伤加成 / 挨打加成 / 结束时反噬）保留 —— 那是作者定的"以伤换伤"手感；
 * 这里只是在它上面<b>再接一条热量线</b>，所以关掉形态也不会影响旧数值 ✓
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class TNFireForms {

    /** 持续时间：60 秒（比旧 buff 长，玩家不用一直补）。 */
    private static final int DURATION_TICKS = 1200;

    /** uuid → {档位, 到期时刻}。 */
    private static final Map<UUID, long[]> ACTIVE = new ConcurrentHashMap<>();

    /** 火痕节流：uuid → 上次留痕的 tick。 */
    private static final Map<UUID, Long> LAST_TRAIL = new ConcurrentHashMap<>();

    private static final Map<String, Integer> TIERS = Map.of(
            "fire_aspect", 1,
            "ember_burn", 2,
            "blaze_burn", 3,
            "inferno_burn", 4,
            "total_burn", 5);

    private TNFireForms() {
    }

    public static boolean handles(String path) {
        return TIERS.containsKey(path);
    }

    /** 施放时激活对应形态。 */
    public static void cast(ServerPlayer player, String path) {
        Integer tier = TIERS.get(path);
        if (tier == null) {
            return;
        }
        long until = player.level().getGameTime() + DURATION_TICKS;
        ACTIVE.put(player.getUUID(), new long[]{tier, until});
        player.getPersistentData().putInt("TncFireForm", tier);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§6[TN-C] §r燃烧形态 §c" + tier + " 档 §7已点燃（60 秒）"), true);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1, player.getZ(),
                    24, 0.6, 0.8, 0.6, 0.05);
            level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE,
                    SoundSource.PLAYERS, 1.0F, 0.7F);
        }
    }

    /** 当前形态档位（0 = 没有）。 */
    public static int tier(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        long[] state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return 0;
        }
        if (player.level().getGameTime() > state[1]) {
            ACTIVE.remove(player.getUUID());
            return 0;
        }
        return (int) state[0];
    }

    /** 焚元：火系叠热 ×1.5。 */
    public static float heatMultiplier(ServerPlayer player) {
        return tier(player) >= 3 ? 1.5F : 1.0F;
    }

    // ------------------------------------------------------------- 事件

    /** 3 档：出门走路就把火铺上；顺手给附近敌人叠热。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) {
            return;
        }
        if (event.getServer().getTickCount() % 10 != 0 || ACTIVE.isEmpty()) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            int tier = tier(player);
            if (tier < 1 || !(player.level() instanceof ServerLevel level)) {
                continue;
            }
            long now = level.getGameTime();
            Long last = LAST_TRAIL.get(player.getUUID());
            if (last != null && now - last < 10) {
                continue;
            }
            boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 0.004;
            if (tier == 1 && !moving) {
                continue;
            }
            LAST_TRAIL.put(player.getUUID(), now);
            Vec3 at = player.position();
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.15, at.z, 6, 0.35, 0.1, 0.35, 0.02);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(at, at).inflate(1.6D),
                    t -> t != player && FireSpellRules.hittable(player, t))) {
                FireHeat.add(e, player, 6.0F);
            }
            if (tier >= 4) {
                level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.2, at.z, 2, 0.3, 0.1, 0.3, 0.05);
            }
        }
    }

    /** 2 档：近战也叠热；5 档：血越少火伤越高。 */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int tier = tier(player);
        if (tier <= 0) {
            return;
        }
        boolean melee = event.getSource().getDirectEntity() == player;
        if (tier >= 2 && melee) {
            FireHeat.add(event.getEntity(), player, 10.0F);
        }
        if (tier >= 5) {
            float missing = 1.0F - Math.max(0.0F, player.getHealth() / player.getMaxHealth());
            if (missing > 0.15F) {
                event.setAmount(event.getAmount() * (1.0F + missing * 1.2F));
            }
        }
    }

    /** 4 档：致命伤时把热量炸出去，并留 1 点血。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLethal(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (tier(player) < 4) {
            return;
        }
        if (player.getHealth() - event.getAmount() > 1.0F) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        long now = level.getGameTime();
        if (data.getLong("TncEmberSaveUntil") > now) {
            return;
        }
        data.putLong("TncEmberSaveUntil", now + 400);
        float heat = 40.0F + 20.0F * (tier(player) - 3);
        Vec3 at = player.position();
        TNShockwaveEntity.blast(level, at, 5.0D, 6.0F, player, 1.0F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(at, at).inflate(5.5D),
                t -> t != player && FireSpellRules.hittable(player, t))) {
            e.hurt(level.damageSources().onFire(), heat * 0.5F);
            FireHeat.add(e, player, 18.0F);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 2, 0.3, 0.3, 0.3, 0);
        event.setAmount(Math.max(0.0F, player.getHealth() - 1.0F));
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§6[TN-C] §r烬魂 §7——烧掉了这一下致命伤"), true);
    }
}
