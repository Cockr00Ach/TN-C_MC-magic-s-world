package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 喷吐蓄力的客户端表现（纯视觉，不参与任何判定）。
 *
 * <p>服务端只同步“开始时刻 + 总蓄力 tick”，客户端按自己的 tick 计数还原进度。
 * 球体本体由 {@link FireBlobFx} 的球面火团负责；这里只补少量向核心汇聚的火星，
 * 让蓄力有“吸气”感。发射瞬间由真正的投射物接管。
 */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class SpitChargeFx {

    /** bossId -> {serverStart, localTickAtStart} */
    private static final Map<Integer, long[]> SEEN = new ConcurrentHashMap<>();

    private SpitChargeFx() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            SEEN.clear();
            return;
        }
        long now = mc.level.getGameTime();
        java.util.Set<Integer> alive = new java.util.HashSet<>();

        if (mc.player == null) {
            return;
        }
        for (VolcanicTortoiseEntity boss : mc.level.getEntitiesOfClass(VolcanicTortoiseEntity.class,
                mc.player.getBoundingBox().inflate(64.0D))) {
            int start = boss.spitChargeStart();
            if (start < 0) {
                SEEN.remove(boss.getId());
                continue;
            }
            alive.add(boss.getId());
            long[] seen = SEEN.get(boss.getId());
            if (seen == null || seen[0] != start) {
                SEEN.put(boss.getId(), new long[]{start, now});
                continue;
            }
            int total = Math.max(1, boss.spitChargeTicks());
            long age = now - seen[1];
            if (age < 0 || age > total + 2) {
                continue;
            }
            float progress = Mth.clamp(age / (float) total, 0.0F, 1.0F);
            Vec3 center = chargeCenter(boss);
            // 少量火星向核心汇聚：越接近发射越密集。
            int count = 1 + (int) (progress * 2.0F);
            for (int i = 0; i < count; i++) {
                double angle = mc.level.random.nextDouble() * Math.PI * 2.0D;
                double radius = 1.1D + mc.level.random.nextDouble() * 0.9D;
                double x = center.x + Math.cos(angle) * radius;
                double y = center.y + (mc.level.random.nextDouble() - 0.35D) * 1.2D;
                double z = center.z + Math.sin(angle) * radius;
                mc.level.addParticle(ParticleTypes.FLAME, x, y, z,
                        (center.x - x) * 0.12D, (center.y - y) * 0.12D, (center.z - z) * 0.12D);
            }
        }
        Iterator<Map.Entry<Integer, long[]>> it = SEEN.entrySet().iterator();
        while (it.hasNext()) {
            if (!alive.contains(it.next().getKey())) {
                it.remove();
            }
        }
    }

    /** 与服务端 mouthPoint 完全一致的嘴前位置，再向正前方推 1.4 格。 */
    static Vec3 chargeCenter(VolcanicTortoiseEntity boss) {
        boolean two = boss.isPhaseTwo();
        double fwd = two ? 6.6D : 5.3D;
        double up = two ? 0.70D : 1.35D;
        double rad = Math.toRadians(boss.getYRot());
        Vec3 forward = new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
        return boss.position().add(forward.scale(fwd + 1.55D)).add(0.0D, up, 0.0D);
    }
}
