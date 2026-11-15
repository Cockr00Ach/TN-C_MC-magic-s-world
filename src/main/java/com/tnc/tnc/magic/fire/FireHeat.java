package com.tnc.tnc.magic.fire;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.Element;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.SpellCatalog;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Heat belongs to the target and cools only after it stops receiving heat. */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class FireHeat {
    public static final int MAX = 100;
    public static final int IGNITE_AT = 60;
    public static final int DECAY_PER_SECOND = 3;
    public static final int COOLING_GRACE_TICKS = 140;
    public static final float IGNITED_TAKEN_MULTIPLIER = 1.25F;

    private record Heat(float amount, int nextCoolingTick, int control) {}

    private static final Map<UUID, Heat> HEAT = new HashMap<>();
    private static final Map<UUID, Integer> LAST_HINT = new HashMap<>();

    private FireHeat() {}

    public static float of(LivingEntity entity) {
        Heat heat = entity == null ? null : HEAT.get(entity.getUUID());
        return heat == null ? 0 : heat.amount;
    }

    public static float ratio(LivingEntity entity) {
        return of(entity) / MAX;
    }

    public static boolean ignited(LivingEntity entity) {
        return of(entity) >= IGNITE_AT;
    }

    public static float add(LivingEntity victim, ServerPlayer caster, float amount) {
        if (victim == null || !(victim.level() instanceof ServerLevel level) || !victim.isAlive()
                || !Float.isFinite(amount) || amount <= 0) return of(victim);
        UUID id = victim.getUUID();
        Heat old = HEAT.get(id);
        float previous = old == null ? 0 : old.amount;
        int control = Math.max(old == null ? 0 : old.control, control(caster));
        float now = Math.min(MAX, previous + amount * TNFireForms.heatMultiplier(caster));
        int tick = level.getServer().getTickCount();
        HEAT.put(id, new Heat(now, tick + graceTicks(control), control));

        if (previous < IGNITE_AT && now >= IGNITE_AT) {
            FireReworkEffects.emit(level, FireReworkEffects.Kind.HEAT_IGNITE, victim.getBoundingBox().getCenter(),
                    Vec3.ZERO, Math.max(1, victim.getBbWidth()), .7F, 20, caster);
            level.playSound(null, victim.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, .55F, .9F);
        }
        if (caster != null) hint(caster, victim, now, tick);
        return now;
    }

    static int control(ServerPlayer caster) {
        if (caster == null) return 0;
        var stone = MagicStone.getOrNull(caster);
        if (stone == null) return 0;
        int tier = 0;
        for (var id : stone.getLearned()) {
            var entry = SpellCatalog.byId(id);
            if (entry != null && entry.element() == Element.FIRE && !entry.independent()) {
                tier = Math.max(tier, entry.tier());
            }
        }
        return control(stone.getAffinity(Element.FIRE), tier);
    }

    static int control(int affinity, int learnedTier) {
        return Math.max(0, Math.min(6, affinity)) + Math.max(0, Math.min(5, learnedTier));
    }

    static int graceTicks(int control) {
        return COOLING_GRACE_TICKS + control * 10;
    }

    static float coolingRate(int control) {
        return DECAY_PER_SECOND - .16F * control;
    }

    static float costMultiplier(int control) {
        return 1F - .04F * control;
    }

    /** Spend a fraction of the current heat without resetting the cooling timer. */
    static float spend(LivingEntity victim, float fraction) {
        UUID id = victim.getUUID();
        Heat heat = HEAT.get(id);
        if (heat == null) return 0;
        float spent = heat.amount * fraction;
        float left = heat.amount - spent;
        if (left < .5F) {
            HEAT.remove(id);
            return heat.amount;
        }
        HEAT.put(id, new Heat(left, heat.nextCoolingTick, heat.control));
        return spent;
    }

    /** Clear heat and return the old amount. */
    public static float consume(LivingEntity victim) {
        return victim == null ? 0 : spend(victim, 1F);
    }

    public static void clear(LivingEntity victim) {
        if (victim == null) return;
        HEAT.remove(victim.getUUID());
        LAST_HINT.remove(victim.getUUID());
    }

    public static int activeCount() {
        return HEAT.size();
    }

    private static void hint(ServerPlayer caster, LivingEntity victim, float heat, int tick) {
        FireHeatFeedback.watch(caster, victim);
        Integer last = LAST_HINT.get(caster.getUUID());
        if (last != null && tick - last < 10) return;
        LAST_HINT.put(caster.getUUID(), tick);
        String label = heat >= MAX ? "§6§l满热" : heat >= IGNITE_AT ? "§c§l燃点" : "§e热量";
        caster.displayClientMessage(Component.literal(String.format("§c[TN-C] §r%s §e%.0f §7/ %d", label, heat, MAX)), true);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (ignited(event.getEntity()) && TNFireRework.isFire(event.getSource())) {
            event.setAmount(event.getAmount() * IGNITED_TAKEN_MULTIPLIER);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        int tick = event.getServer().getTickCount();
        if (tick % 20 != 0) return;
        for (var it = HEAT.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            Heat heat = entry.getValue();
            if (tick < heat.nextCoolingTick) continue;
            float left = heat.amount - coolingRate(heat.control);
            if (left <= 0) it.remove();
            else entry.setValue(new Heat(left, tick + 20, heat.control));
        }
        LAST_HINT.values().removeIf(last -> tick - last > 200);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        HEAT.clear();
        LAST_HINT.clear();
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level() instanceof ServerLevel) clear(event.getEntity());
    }
}
