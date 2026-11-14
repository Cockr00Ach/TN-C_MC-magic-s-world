package com.tnc.tnc.boss;

import com.tnc.tnc.magic.TNShockwaveEntity;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Boss 交互机制的<b>开场提示</b>（2026-11-15 作者追加）。
 *
 * <p>三件事，四个 Boss 共用：
 * <ol>
 *   <li><b>场地迸光</b>：按元素色在场地里炸一圈尘光（风=绿、土=土黄、水=蓝、暗=紫…）</li>
 *   <li><b>屏幕上方一句话</b>：告诉玩家这一轮要干什么（"找出真身" / "击碎土柱" …）</li>
 *   <li><b>时间临近抖屏</b>：最后 3 秒每秒抖一下，越接近越明显</li>
 * </ol>
 *
 * <p>用原版的 title/subtitle 包，不新开网络通道 —— 这套在任何客户端上都稳 ✓
 */
public final class BossTrialCue {

    private BossTrialCue() {
    }

    /**
     * 开场：场地迸光 + 屏幕上方写目标。
     *
     * @param colour 元素色（风 0.42/1.0/0.62，土 0.86/0.68/0.36，水 0.35/0.72/1.0，暗 0.62/0.36/0.92）
     * @param ticks  标题停留多久（一般 = 这一轮的判定时间）
     */
    public static void begin(ServerLevel level, Vec3 centre, double radius, String title,
                             float colourR, float colourG, float colourB, int ticks) {
        // 1) 场地迸光：一圈向上的尘柱
        int rings = 3;
        for (int ring = 0; ring < rings; ring++) {
            double r = radius * (0.35 + 0.25 * ring);
            int points = 24 + ring * 12;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2 * i / points;
                double x = centre.x + Math.cos(angle) * r;
                double z = centre.z + Math.sin(angle) * r;
                level.sendParticles(new DustParticleOptions(new Vector3f(colourR, colourG, colourB), 1.6F),
                        x, centre.y + 0.4, z, 2, 0.1, 0.35, 0.1, 0.02);
                level.sendParticles(new DustParticleOptions(new Vector3f(colourR, colourG, colourB), 2.4F),
                        x, centre.y + 1.6 + ring * 0.8, z, 1, 0.05, 0.5, 0.05, 0.0);
            }
        }
        level.sendParticles(new DustParticleOptions(new Vector3f(colourR, colourG, colourB), 3.0F),
                centre.x, centre.y + 2.0, centre.z, 60, radius * 0.25, 1.2, radius * 0.25, 0.1);

        // 2) 屏幕特效：元素色边框 + 倒计时 + 临近心跳红闪
        BossTrialSync.push(level, centre, ticks, Math.max(1, ticks), colourR, colourG, colourB);

        // 3) 屏幕上方写目标（title 常驻整轮，subtitle 留给倒计时）
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(centre, centre).inflate(radius + 48.0D), p -> p.isAlive() && !p.isSpectator())) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, Math.max(20, ticks), 10));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal("§7" + Math.max(1, ticks / 20) + " 秒")));
        }
    }

    /**
     * 时间临近抖屏：剩余 {@code ticksLeft} 在 3 秒内，每秒抖一次，越接近越狠。
     *
     * <p>复用 {@link TNShockwaveEntity#blast} 的镜头抖动（火系那套已经在用，稳）。
     */
    public static void warn(ServerLevel level, Vec3 centre, double radius, int ticksLeft) {
        if (ticksLeft <= 0) {
            return;
        }
        // 屏幕特效每 10 tick 更新一次（让边框一直收着走）
        if (ticksLeft % 10 == 0) {
            BossTrialSync.push(level, centre, ticksLeft, 60 * 20, 0.9F, 0.35F, 0.30F);
        }
        // 最后 5 秒：心跳声（音调越来越高 → 越接近越慌）
        if (ticksLeft <= 100 && ticksLeft % 20 == 0) {
            float pitch = 0.9F + (100 - ticksLeft) * 0.006F;
            float volume = 0.9F + (100 - ticksLeft) * 0.006F;
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                    new AABB(centre, centre).inflate(radius + 48.0D), p -> p.isAlive() && !p.isSpectator())) {
                level.playSound(null, player.blockPosition(),
                        net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT,
                        net.minecraft.sounds.SoundSource.AMBIENT, Math.min(1.6F, volume), pitch);
            }
        }
        if (ticksLeft > 60 || ticksLeft % 20 > 5) {
            return;
        }
        float shake = 1.4F + (60 - ticksLeft) * 0.06F;
        TNShockwaveEntity.blast(level, centre, 0.5D, shake, null, 0.0F);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(centre, centre).inflate(radius + 48.0D), p -> p.isAlive() && !p.isSpectator())) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal("§c" + Math.max(1, ticksLeft / 20) + " 秒")));
        }
    }
}
