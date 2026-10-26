package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.combat.DownedCombat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Field exercise, not a safe duel: normal world combat and death rules still apply. */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class TemperingTraining {
    private static final Map<UUID, GuardDrill> ACTIVE = new HashMap<>();
    private TemperingTraining() {}
    private static boolean eligible(ServerPlayer p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator() && !DownedCombat.isDowned(p)
            && TemperingData.get(p.server).enrolled(p.getUUID());
    }
    public static boolean start(ServerPlayer p) {
        if (!eligible(p)) return false;
        ACTIVE.put(p.getUUID(), new GuardDrill());
        p.sendSystemMessage(Component.literal("守势反击训练：持盾挡住敌对生物的三次攻击，再于最后一次格挡后四秒内持剑或斧近战反击该敌人。超时可再次格挡刷新窗口。野外实战照常受伤；首次完成 +120 磨砺。"));
        return true;
    }
    public static void cancel(ServerPlayer p) { ACTIVE.remove(p.getUUID()); }
    static int blockCount(ServerPlayer p) {
        var drill = ACTIVE.get(p.getUUID()); return drill == null ? 0 : drill.blocks();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void block(ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !eligible(p) || event.getBlockedDamage() <= 0
            || !(event.getDamageSource().getEntity() instanceof Enemy)
            || p.isAlliedTo(event.getDamageSource().getEntity())) return;
        var drill = ACTIVE.get(p.getUUID());
        if (drill != null && drill.block(event.getDamageSource().getEntity().getUUID(), p.server.overworld().getGameTime())) {
            p.displayClientMessage(Component.literal("守势反击 · 格挡 " + drill.blocks() + "/3"
                + (drill.blocks() == 3 ? " · 四秒内反击刚才的敌人" : "")), true);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void counter(LivingDamageEvent event) {
        if (event.getAmount() <= 0 || !(event.getEntity() instanceof Enemy)
            || !(event.getSource().getEntity() instanceof ServerPlayer p) || !eligible(p)
            || event.getSource().getDirectEntity() != p || !event.getSource().is(DamageTypes.PLAYER_ATTACK)
            || !TemperingEvents.weapon(p) || p.isAlliedTo(event.getEntity())) return;
        var drill = ACTIVE.get(p.getUUID());
        if (drill == null || !drill.counter(event.getEntity().getUUID(), p.server.overworld().getGameTime())) return;
        ACTIVE.remove(p.getUUID());
        var data = TemperingData.get(p.server);
        if (data.completeGuardDrill(p.getUUID())) {
            int gained = TemperingEvents.award(p, GuardDrill.REWARD);
            p.sendSystemMessage(Component.literal("守势反击 · 首次完成，磨砺 +" + gained + "。可再次训练，但不重复领取首次奖励。"));
        } else p.sendSystemMessage(Component.literal("守势反击 · 训练完成。首次奖励已领取，本次不重复加点。"));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if(e.getEntity() instanceof ServerPlayer p) cancel(p); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) { if(e.getEntity() instanceof ServerPlayer p) cancel(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if(e.getEntity() instanceof ServerPlayer p) cancel(p); }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { ACTIVE.clear(); }
}
