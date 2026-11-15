package com.tnc.tnc.magic.fire;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNFireMechanics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Each combustion form attaches one additional combat behavior to its status effect. */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class TNFireForms {
    private static final List<String> FORMS = List.of(
            "fire_aspect", "ember_burn", "blaze_burn", "inferno_burn", "total_burn");

    private record Position(ServerLevel level, Vec3 at) {}
    private static final Map<UUID, Position> WALK = new HashMap<>();

    private TNFireForms() {}

    public static boolean handles(String path) {
        return FORMS.contains(path);
    }

    public static void cast(ServerPlayer player, String path) {
        int tier = FORMS.indexOf(path) + 1;
        if (tier == 0) return;
        WALK.remove(player.getUUID());
        emitForm(player, tier);
    }

    public static int tier(ServerPlayer player) {
        if (player == null) return 0;
        if (player.hasEffect(TNFireMechanics.TOTAL_BURN.get())) return 5;
        if (player.hasEffect(TNFireMechanics.INFERNO_BURN.get())) return 4;
        if (player.hasEffect(TNFireMechanics.BLAZE_BURN.get())) return 3;
        if (player.hasEffect(TNFireMechanics.EMBER_BURN.get())) return 2;
        if (player.hasEffect(TNFireMechanics.FIRE_ASPECT.get())) return 1;
        return 0;
    }

    public static float heatMultiplier(ServerPlayer player) {
        return tier(player) == 3 ? 1.5F : 1F;
    }

    private static void emitForm(ServerPlayer player, int tier) {
        TNFireRework.emit(player, FireReworkEffects.Kind.FORM, player.position(), Vec3.ZERO,
                1.15F + tier * .12F, tier / 5F, 31);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 5 != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            int tier = tier(player);
            if (tier > 0 && player.isAlive() && !player.isSpectator()
                    && event.getServer().getTickCount() % 30 == 0) emitForm(player, tier);

            if (tier != 1 || !player.isAlive() || player.isSpectator() || !player.onGround()) {
                WALK.remove(player.getUUID());
                continue;
            }
            Vec3 at = player.position();
            Position previous = WALK.put(player.getUUID(), new Position(player.serverLevel(), at));
            if (previous == null || previous.level() != player.level()) continue;
            Vec3 motion = at.subtract(previous.at());
            if (motion.horizontalDistanceSqr() > .16 && motion.lengthSqr() <= 16) {
                TNFireRework.footprint(player, previous.at(), new Vec3(motion.x, 0, motion.z).normalize());
            }
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        var source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        int tier = tier(player);
        var target = event.getEntity();
        if (!TNFireRework.canDamage(player, target)) return;
        if (tier == 2 && source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == player) {
            FireHeat.add(target, player, 10);
            TNFireRework.emit(player, FireReworkEffects.Kind.FORM_STRIKE, target.getBoundingBox().getCenter(),
                    player.getLookAngle(), 1.05F, .65F, 13);
        } else if (tier == 5 && TNFireRework.isFire(source)) {
            float missing = 1 - player.getHealth() / player.getMaxHealth();
            event.setAmount(event.getAmount() * (1 + missing * 1.2F));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLethal(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || tier(player) != 4
                || event.getAmount() < player.getHealth()
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        var data = player.getPersistentData();
        long now = player.serverLevel().getGameTime();
        if (data.getLong("TncEmberSaveUntil") > now) return;
        data.putLong("TncEmberSaveUntil", now + 400);
        if (player.getHealth() < 1) player.setHealth(1);
        event.setAmount(player.getHealth() - 1);
        Vec3 at = player.position();
        for (var target : TNFireRework.area(player, at, 5, 3.5)) {
            TNFireRework.damage(player, target, 30);
            FireHeat.add(target, player, 18);
        }
        TNFireRework.burst(player, at.add(0, .65, 0), 5, 100, FireReworkEffects.Kind.GHOST_BURST);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        WALK.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        WALK.clear();
    }
}
