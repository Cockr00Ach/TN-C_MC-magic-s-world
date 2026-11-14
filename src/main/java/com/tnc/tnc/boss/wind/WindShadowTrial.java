package com.tnc.tnc.boss.wind;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 风将的交互玩法（2026-11-15 作者设计）。
 *
 * <ol>
 *   <li>血量掉到 60% 以下触发一次：风将回到场地中心，然后<b>消失</b></li>
 *   <li>场地里出现 <b>5 个虚影</b>（4 个空手、1 个手里有武器），10 秒内找出并打碎正确的那个</li>
 *   <li>打碎<b>假的</b> ⇒ 所有虚影变成巨型龙卷风，风将现身</li>
 *   <li>打碎<b>真的</b> ⇒ 风将现身并<b>眩晕 10 秒</b>，同时被风长枪（{@code wind_weapon_3}）命中，掉 <b>10%</b> 血</li>
 * </ol>
 *
 * <p>虚影实体和渲染器都做成内嵌类，省掉两套注册文件（本类 < 400 行，够用）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class WindShadowTrial {

    public static final int SHADOWS = 5;
    public static final int WINDOW_TICKS = 200;      // 10 秒
    public static final int STUN_TICKS = 200;        // 眩晕 10 秒
    public static final float REAL_DAMAGE_PERCENT = 0.10F;

    private static final Map<UUID, Round> ROUNDS = new ConcurrentHashMap<>();

    private static final class Round {
        final List<UUID> shadows = new ArrayList<>();
        final UUID real;
        final long deadline;
        boolean resolved;

        Round(UUID real, long deadline) {
            this.real = real;
            this.deadline = deadline;
        }
    }

    private WindShadowTrial() {
    }

    /** 纯逻辑：打碎的是真身 → 眩晕；否则 → 龙卷。 */
    public static boolean isReal(boolean brokenIsReal) {
        return brokenIsReal;
    }

    public static boolean active(WindGeneralEntity boss) {
        Round round = ROUNDS.get(boss.getUUID());
        return round != null && !round.resolved;
    }

    public static void start(WindGeneralEntity boss, ServerLevel level) {
        if (ROUNDS.containsKey(boss.getUUID())) {
            return;
        }
        Vec3 centre = boss.position();
        int realIndex = level.random.nextInt(SHADOWS);
        UUID realId = null;
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < SHADOWS; i++) {
            double angle = Math.PI * 2 * i / SHADOWS;
            Vec3 at = centre.add(Math.cos(angle) * 9.0D, 0, Math.sin(angle) * 9.0D);
            Shadow shadow = Shadow.spawn(level, boss, at, i == realIndex);
            ids.add(shadow.getUUID());
            if (i == realIndex) {
                realId = shadow.getUUID();
            }
        }
        ROUNDS.put(boss.getUUID(), new Round(realId, level.getGameTime() + WINDOW_TICKS));
        // 风将本体消失（不可见 + 不可打）
        boss.setInvisible(true);
        boss.setInvulnerable(true);
        boss.getNavigation().stop();
        level.sendParticles(ParticleTypes.CLOUD, centre.x, centre.y + 1, centre.z, 60, 1.0, 1.2, 1.0, 0.2);
        level.playSound(null, boss.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 1.6F, 0.8F);
        com.tnc.tnc.boss.BossTrialCue.begin(level, centre, 40.0D, "§a找出真身",
                0.42F, 1.0F, 0.62F, WINDOW_TICKS);
        announce(level, centre, "§7[风将] §r他消失了 —— §e十个呼吸内找出拿武器的那一个。");
    }

    public static void tick(WindGeneralEntity boss) {
        Round round = ROUNDS.get(boss.getUUID());
        if (round == null || round.resolved || !(boss.level() instanceof ServerLevel level)) {
            return;
        }
        com.tnc.tnc.boss.BossTrialCue.warn(level, boss.position(), 40.0D,
                (int) (round.deadline - level.getGameTime()));
        if (level.getGameTime() > round.deadline) {
            // 超时：等同打碎假的
            fakeBroken(level, boss, round);
        }
    }

    /** 某个虚影被打碎。 */
    public static void onShadowDestroyed(ServerLevel level, UUID bossId, UUID shadowId) {
        Round round = ROUNDS.get(bossId);
        if (round == null || round.resolved) {
            return;
        }
        if (!(level.getEntity(bossId) instanceof WindGeneralEntity boss)) {
            ROUNDS.remove(bossId);
            return;
        }
        if (shadowId.equals(round.real)) {
            realBroken(level, boss, round);
        } else {
            fakeBroken(level, boss, round);
        }
    }

    /** 打碎真身：风将现身、眩晕 10 秒、吃一发风长枪（10% 血）。 */
    private static void realBroken(ServerLevel level, WindGeneralEntity boss, Round round) {
        round.resolved = true;
        cleanup(level, boss, round);
        boss.setInvisible(false);
        boss.setInvulnerable(false);
        Vec3 at = boss.position();
        float damage = boss.getMaxHealth() * REAL_DAMAGE_PERCENT;
        boss.hurt(level.damageSources().magic(), damage);
        boss.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, STUN_TICKS, 6));
        boss.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.WEAKNESS, STUN_TICKS, 4));
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y + 1.2, at.z, 12, 0.8, 0.8, 0.8, 0.1);
        level.playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_THUNDER,
                SoundSource.HOSTILE, 2.0F, 0.9F);
        announce(level, at, "§b[风将] §r真身被识破 —— 他被自己的风长枪钉住，眩晕 10 秒！");
    }

    /** 打碎假的（或超时）：所有虚影变巨型龙卷，风将现身。 */
    private static void fakeBroken(ServerLevel level, WindGeneralEntity boss, Round round) {
        round.resolved = true;
        Vec3 centre = boss.position();
        cleanup(level, boss, round);
        boss.setInvisible(false);
        boss.setInvulnerable(false);
        // 五个位置的巨型龙卷：粒子 + 范围伤害，持续 6 秒
        for (int i = 0; i < SHADOWS; i++) {
            double angle = Math.PI * 2 * i / SHADOWS;
            Vec3 at = centre.add(Math.cos(angle) * 9.0D, 0, Math.sin(angle) * 9.0D);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y + 1.5, at.z, 30, 1.2, 2.0, 1.2, 0.4);
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 1.0, at.z, 40, 1.5, 1.0, 1.5, 0.3);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                    new AABB(at, at).inflate(6.0D), p -> p.isAlive() && !p.isSpectator())) {
                player.hurt(level.damageSources().magic(), 10.0F);
                Vec3 push = player.position().subtract(at).normalize().scale(0.9D).add(0, 0.5, 0);
                player.setDeltaMovement(player.getDeltaMovement().add(push));
                player.hurtMarked = true;
            }
        }
        level.playSound(null, boss.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP,
                SoundSource.HOSTILE, 2.2F, 1.1F);
        announce(level, centre, "§4[风将] §r砍错了 —— 五个虚影全成了龙卷风，他现身了。");
    }

    private static void cleanup(ServerLevel level, WindGeneralEntity boss, Round round) {
        for (UUID id : round.shadows) {
            Entity shadow = level.getEntity(id);
            if (shadow != null && shadow.isAlive()) {
                shadow.discard();
            }
        }
        ROUNDS.remove(boss.getUUID());
    }

    private static void announce(ServerLevel level, Vec3 at, String message) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(at, at).inflate(80.0D))) {
            player.displayClientMessage(Component.literal(message), false);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof WindGeneralEntity boss) {
            ROUNDS.remove(boss.getUUID());
        }
    }

    // ================================================================= 虚影实体

    /** 风将的虚影：不会动、有血量；{@link #armed} 决定"手里有没有武器"（真身才拿）。 */
    public static final class Shadow extends Entity {

        private static final EntityDataAccessor<Boolean> ARMED =
                SynchedEntityData.defineId(Shadow.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Float> HEALTH =
                SynchedEntityData.defineId(Shadow.class, EntityDataSerializers.FLOAT);

        public static final float MAX_HEALTH = 40.0F;

        private UUID bossId;

        public Shadow(EntityType<? extends Shadow> type, Level level) {
            super(type, level);
            this.noPhysics = true;
            this.noCulling = true;
        }

        @Override
        protected void defineSynchedData() {
            entityData.define(ARMED, false);
            entityData.define(HEALTH, MAX_HEALTH);
        }

        public static Shadow spawn(ServerLevel level, WindGeneralEntity boss, Vec3 at, boolean armed) {
            Shadow shadow = new Shadow(TNOrbEntities.WIND_SHADOW.get(), level);
            shadow.setPos(at.x, at.y, at.z);
            shadow.bossId = boss.getUUID();
            shadow.entityData.set(ARMED, armed);
            level.addFreshEntity(shadow);
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 1, at.z, 20, 0.4, 0.8, 0.4, 0.1);
            return shadow;
        }

        public boolean armed() {
            return entityData.get(ARMED);
        }

        public float health() {
            return entityData.get(HEALTH);
        }

        @Override
        public boolean isPickable() {
            return true;
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            if (level().isClientSide() || amount <= 0) {
                return false;
            }
            float left = health() - amount;
            entityData.set(HEALTH, Math.max(0, left));
            if (level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1, getZ(), 8, 0.4, 0.5, 0.4, 0.1);
                if (left <= 0) {
                    if (bossId != null) {
                        onShadowDestroyed(level, bossId, getUUID());
                    }
                    discard();
                }
            }
            return true;
        }

        @Override
        public void tick() {
            super.tick();
            if (!level().isClientSide() && tickCount > 260) {
                discard();
            }
        }

        @Override
        protected void readAdditionalSaveData(CompoundTag tag) {
            entityData.set(ARMED, tag.getBoolean("Armed"));
            entityData.set(HEALTH, Math.max(1.0F, tag.getFloat("Health")));
            if (tag.hasUUID("Boss")) {
                bossId = tag.getUUID("Boss");
            }
        }

        @Override
        protected void addAdditionalSaveData(CompoundTag tag) {
            tag.putBoolean("Armed", armed());
            tag.putFloat("Health", health());
            if (bossId != null) {
                tag.putUUID("Boss", bossId);
            }
        }
    }

    // ================================================================= 虚影渲染

    /** 虚影：一个半透明的人形剪影；真身右手多一把长矛（玩家要认这个）。 */
    public static final class ShadowRenderer extends net.minecraft.client.renderer.entity.EntityRenderer<Shadow> {

        public ShadowRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(Shadow entity) {
            return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
        }

        @Override
        public void render(Shadow entity, float yaw, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int packedLight) {
            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
            PoseStack.Pose m = pose.last();
            float a = 0.45F;
            // 身体：三段方柱
            box(vc, m, -0.28, 0.0, -0.18, 0.28, 1.1, 0.18, 0.62F, 0.85F, 0.78F, a);
            box(vc, m, -0.20, 1.1, -0.16, 0.20, 1.55, 0.16, 0.70F, 0.92F, 0.85F, a);
            box(vc, m, -0.14, 1.55, -0.14, 0.14, 1.86, 0.14, 0.80F, 0.96F, 0.90F, a);
            // 手臂
            box(vc, m, -0.44, 0.75, -0.10, -0.28, 1.35, 0.10, 0.55F, 0.80F, 0.74F, a * 0.9F);
            box(vc, m, 0.28, 0.75, -0.10, 0.44, 1.35, 0.10, 0.55F, 0.80F, 0.74F, a * 0.9F);
            if (entity.armed()) {
                // 右手的长矛：真身唯一的外观差异
                box(vc, m, 0.40, 1.15, -0.05, 0.50, 2.35, 0.05, 1.0F, 0.95F, 0.55F, 0.95F);
                box(vc, m, 0.36, 2.30, -0.08, 0.54, 2.52, 0.08, 1.0F, 0.85F, 0.35F, 1.0F);
            }
        }

        private static void box(VertexConsumer vc, PoseStack.Pose m,
                                double x0, double y0, double z0, double x1, double y1, double z1,
                                float r, float g, float b, float a) {
            double[][] c = {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1},
                    {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
            int[][] faces = {{0, 1, 2, 3}, {4, 5, 6, 7}, {0, 1, 5, 4}, {1, 2, 6, 5}, {2, 3, 7, 6}, {3, 0, 4, 7}};
            for (int[] f : faces) {
                for (int i : f) {
                    vc.vertex(m.pose(), (float) c[i][0], (float) c[i][1], (float) c[i][2])
                            .color((int) (r * 255), (int) (g * 255), (int) (b * 255), (int) (a * 255))
                            .endVertex();
                }
            }
        }
    }
}
