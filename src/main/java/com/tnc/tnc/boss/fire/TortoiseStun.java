package com.tnc.tnc.boss.fire;

import com.tnc.tnc.magic.compat.CombatCasting;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** One 100-tick stun starts with the launch, permits its physics, and roots on landing. */
@Mod.EventBusSubscriber(modid="tnc")
public final class TortoiseStun {
    public static final int DURATION_TICKS = 100;
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS,"tnc");
    public static final RegistryObject<MobEffect> STUN = EFFECTS.register("tortoise_stun",Effect::new);
    private static final Map<ServerLevel,Map<UUID,Hold>> HOLDS = new WeakHashMap<>();
    private static final class Hold {
        final int launchedAt;
        Vec3 anchor;
        Hold(int launchedAt) { this.launchedAt=launchedAt; }
    }
    private TortoiseStun() {}
    public static void register(IEventBus bus) { EFFECTS.register(bus); }
    public static boolean isStunned(LivingEntity target) { return STUN.isPresent() && target.hasEffect(STUN.get()); }

    public static boolean apply(LivingEntity target, double launchSpeed) {
        if (!(target.level() instanceof ServerLevel level) || !target.isAlive()) return false;
        if (!target.addEffect(new MobEffectInstance(STUN.get(),DURATION_TICKS,0,false,false,true)) && !isStunned(target)) return false;
        target.stopRiding();
        target.setOnGround(false);
        Vec3 motion=target.getDeltaMovement();
        target.setDeltaMovement(motion.x,Math.max(motion.y,launchSpeed),motion.z);
        target.hurtMarked=true;
        target.fallDistance=0;
        HOLDS.computeIfAbsent(level,k->new HashMap<>()).put(target.getUUID(),new Hold(target.tickCount));
        suppressInput(target);
        return true;
    }

    private static void suppressInput(LivingEntity target) {
        target.xxa=0;target.yya=0;target.zza=0;
        target.setJumping(false);target.setSprinting(false);target.stopUsingItem();
        if (target instanceof Mob mob) mob.getNavigation().stop();
        if (target instanceof Player player) CombatCasting.cancel(player);
    }

    private static void enforce(LivingEntity target, Hold hold) {
        suppressInput(target);
        // Do not erase the launch impulse or teleport an airborne victim back to the floor.
        if (hold.anchor==null && target.onGround() && target.tickCount-hold.launchedAt>=3) hold.anchor=target.position();
        if (hold.anchor==null) { target.fallDistance=0; return; }
        boolean moved=target.position().distanceToSqr(hold.anchor)>1.0E-6D;
        boolean impulse=target.getDeltaMovement().lengthSqr()>1.0E-6D;
        if (moved) target.teleportTo(hold.anchor.x,hold.anchor.y,hold.anchor.z);
        target.setDeltaMovement(Vec3.ZERO);target.fallDistance=0;
        if (moved || impulse) target.hurtMarked=true;
    }

    @SubscribeEvent public static void livingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity target=event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || !isStunned(target)) return;
        Hold hold=HOLDS.computeIfAbsent(level,k->new HashMap<>()).computeIfAbsent(target.getUUID(),k->new Hold(target.tickCount-3));
        enforce(target,hold);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        Map<UUID,Hold> holds=HOLDS.get(level);if (holds==null) return;
        var iterator=holds.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry=iterator.next();var entity=level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity target) || !target.isAlive() || !isStunned(target)) { iterator.remove();continue; }
            enforce(target,entry.getValue());
        }
        if (holds.isEmpty()) HOLDS.remove(level);
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) HOLDS.remove(level);
    }
    @SubscribeEvent public static void fall(LivingFallEvent event) {
        if (isStunned(event.getEntity())) event.setDamageMultiplier(0);
    }
    @SubscribeEvent public static void attack(AttackEntityEvent event) {
        if (isStunned(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void melee(LivingAttackEvent event) {
        if (event.getSource().getDirectEntity() instanceof LivingEntity attacker && isStunned(attacker)) event.setCanceled(true);
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent event) {
        if (isStunned(event.getEntity()) && event.isCancelable()) event.setCanceled(true);
    }
    @SubscribeEvent public static void use(LivingEntityUseItemEvent.Start event) {
        if (isStunned(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if (isStunned(event.getPlayer())) event.setCanceled(true);
    }
    @SubscribeEvent public static void place(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof LivingEntity target && isStunned(target)) event.setCanceled(true);
    }

    private static final class Effect extends MobEffect {
        Effect() {
            super(MobEffectCategory.HARMFUL,0xE9BF58);
            addAttributeModifier(Attributes.MOVEMENT_SPEED,"64dbbfaf-c279-48e8-b00e-14d38bc6ec85",-1,AttributeModifier.Operation.MULTIPLY_TOTAL);
            if (ModList.get().isLoaded("spell_engine")) Engine.configure(this);
        }
    }
    /** Isolate optional engine classes; its native stun blocks movement, jumping, items and spells. */
    private static final class Engine {
        static void configure(MobEffect effect) {
            if (effect instanceof net.spell_engine.api.effect.ActionImpairing)
                net.spell_engine.api.effect.ActionImpairing.configure(effect,net.spell_engine.api.effect.EntityActionsAllowed.STUN);
        }
    }
}
