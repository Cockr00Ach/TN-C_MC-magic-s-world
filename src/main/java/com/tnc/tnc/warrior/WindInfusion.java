package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.combat.DownedCombat;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Existing weapons retain their complete item data; server resolves a short, wall-clipped wind cut. */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class WindInfusion {
    public static final String ELEMENT_KEY = "TncWeaponElement";
    private WindInfusion() {}
    public static boolean weapon(ItemStack stack) { return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem; }
    public static boolean hasElement(ItemStack stack) { return stack.hasTag() && stack.getTag().contains(ELEMENT_KEY); }
    public static boolean isWind(ItemStack stack) {
        return weapon(stack) && stack.hasTag() && "wind".equals(stack.getTag().getString(ELEMENT_KEY));
    }
    public static ItemStack infuse(ItemStack stack) {
        if (!weapon(stack) || stack.getCount() != 1 || hasElement(stack)) return ItemStack.EMPTY;
        var copy = stack.copy(); copy.getOrCreateTag().putString(ELEMENT_KEY, "wind"); return copy;
    }
    @SubscribeEvent public static void tooltip(ItemTooltipEvent e) {
        if (!isWind(e.getItemStack())) return;
        e.getToolTip().add(Component.literal("风元素灌注 · 潜行对空右键：风刃"));
        e.getToolTip().add(Component.literal("需磨砺 200 · 伤害 4 · 射程 6 · 冷却 8 秒"));
        e.getToolTip().add(Component.literal("风之竞技场：射程延伸至 9；保留武器原属性"));
    }
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickItem e) {
        if (e.getHand() != InteractionHand.MAIN_HAND || !e.getEntity().isShiftKeyDown() || !isWind(e.getItemStack())) return;
        e.setCancellationResult(InteractionResult.SUCCESS); e.setCanceled(true);
        if (e.getEntity() instanceof ServerPlayer p) cast(p);
    }
    public static boolean cast(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || DownedCombat.isDowned(p) || !isWind(p.getMainHandItem())) return false;
        var data = TemperingData.get(p.server);
        if (!data.enrolled(p.getUUID()) || data.points(p.getUUID()) < WindInfusionRules.REQUIRED_TEMPERING) {
            p.displayClientMessage(Component.literal("风刃需要磨砺达到 200（扎根）。"), true); return false;
        }
        long now = p.server.overworld().getGameTime();
        if (!data.spendWindCooldown(p.getUUID(), now)) {
            p.displayClientMessage(Component.literal("风刃尚在冷却。"), true); return false;
        }
        double range = WindInfusionRules.range(p.level().dimension().location().toString().equals("tnc:wind_arena"));
        Vec3 start = p.getEyePosition();
        Vec3 end = p.level().clip(new ClipContext(start, start.add(p.getLookAngle().scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getLocation();
        LivingEntity target = null; double nearest = start.distanceToSqr(end);
        for (var candidate : p.serverLevel().getEntitiesOfClass(LivingEntity.class,
                new AABB(start, end).inflate(.35), e -> e instanceof Enemy && e.isAlive() && !p.isAlliedTo(e)&&(!TrainingGround.trainer(e)||SpecialistTrainers.isWindTarget(e)))) {
            var hit = candidate.getBoundingBox().inflate(.2).clip(start, end);
            if (hit.isPresent() && start.distanceToSqr(hit.get()) < nearest
                    && p.serverLevel().clip(new ClipContext(start, candidate.getBoundingBox().getCenter(),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                nearest = start.distanceToSqr(hit.get()); target = candidate;
            }
        }
        if (target != null) {
            end = start.add(p.getLookAngle().scale(Math.sqrt(nearest)));
            if(target.hurt(p.damageSources().indirectMagic(p, p), WindInfusionRules.DAMAGE))SpecialistTrainers.windHit(p,target);
        }
        WarriorCombatEffects.windCut(p,start,end);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, .8f, 1.3f);
        p.getMainHandItem().hurtAndBreak(1, p, player -> player.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        TemperingEvents.sync(p);
        return true;
    }
}
