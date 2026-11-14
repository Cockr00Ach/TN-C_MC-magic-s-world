package com.tnc.tnc.travel;

import com.tnc.tnc.TNMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 服务端事件接线：tick、受伤、目标抑制、交互封锁、登出清理。
 *
 * <p>为什么不把这些塞进 {@link TravelFormService}：服务类保持可读，
 * 事件只做“谁在什么时候调用”；也方便以后把 M2/M3 的技能单独挂事件。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class TravelFormEvents {

    private TravelFormEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        TravelFormService.tick(player);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        TravelFormService.onHurt(event.getEntity(), event.getSource(), event.getAmount());
    }

    /**
     * 下界补偿：不能附身，但拿着元素法杖时跳得更高（作者的"下界只能跳"）。
     *
     * <p>为什么用跳跃事件而不是每 tick 加药水效果：给跳跃本身加一次向上的冲量，
     * 手感是"跳得高"，不会像药水那样把移动也一起改了 ✓。
     */
    @SubscribeEvent
    public static void onJump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().dimension() != net.minecraft.world.level.Level.NETHER) {
            return;
        }
        if (TravelStaff.of(player.getMainHandItem()) == null) {
            return;
        }
        player.push(0.0D, 0.32D, 0.0D);
        player.hurtMarked = true;
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer player
                && TravelFormService.suppressesTarget(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && TravelFormService.active(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && TravelFormService.active(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && TravelFormService.active(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && TravelFormService.active(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && TravelFormService.active(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && TravelFormService.active(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItem(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player && TravelFormService.active(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer receiver
                && event.getTarget() instanceof ServerPlayer target
                && TravelFormService.active(target)) {
            TravelNetwork.sendAppearanceTo(target, receiver);
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TravelNetwork.sendState(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TravelFormService.logout(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TravelFormService.exit(player, true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TravelFormService.exit(player, true);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TravelFormService.clear(player.getUUID());
            TravelNetwork.sendAppearance(player, false);
            TravelNetwork.sendState(player);
        }
    }
}
