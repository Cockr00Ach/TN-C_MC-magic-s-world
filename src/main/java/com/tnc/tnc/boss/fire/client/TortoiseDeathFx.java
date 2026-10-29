package com.tnc.tnc.boss.fire.client;
import com.tnc.tnc.boss.fire.TortoiseDeath;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Last thin wisps of smoke; no impact, flash, debris or screen shake. */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TortoiseDeathFx {
    private TortoiseDeathFx() {}
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        int total = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof VolcanicTortoiseEntity boss) || !boss.isDyingAnimation()
                    || mc.player.distanceToSqr(boss) > 4096) continue;
            int age = (int) boss.deathAge(0);
            if (TortoiseDeath.particleBudget(age) == 0 || age % (age < 30 ? 4 : 9) != 0) continue;
            if (++total > 24) break;
            var at = boss.craterPoint();
            var particle = mc.particleEngine.createParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, 0, 0.02, 0);
            if (particle != null) {
                particle.scale(0.35F);
                particle.setLifetime(16);
                particle.setColor(0.4F, 0.4F, 0.4F);
            }
        }
    }
}
