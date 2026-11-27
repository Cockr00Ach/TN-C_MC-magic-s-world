package com.tnc.tnc.dungeon.mirror;

import com.tnc.tnc.dungeon.DungeonRealm;
import com.tnc.tnc.magic.compat.CombatCasting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/** The field suppresses spell sources only. Vanilla arrows, fishing hooks and utility stay usable. */
@Mod.EventBusSubscriber(modid="tnc")
public final class MirrorSilence {
    private static final String OWNER = "EchoCastOwner", ENTERED = "EchoSilenced", RUN = "EchoCastRun";
    private MirrorSilence() {}
    public static boolean active(LivingEntity entity) {
        return entity instanceof ServerPlayer p && !(p instanceof MirrorSpellHand) && MirrorFight.silent(p);
    }

    private static boolean inside(Level level) { return !level.isClientSide() && DungeonRealm.inside(level); }

    public static EchoDouble summonOwner(Entity entity) {
        var tag=entity.getPersistentData();
        if(!tag.hasUUID(OWNER) || !(entity.level() instanceof ServerLevel level)) return null;
        return level.getEntity(tag.getUUID(OWNER)) instanceof EchoDouble mirror ? mirror : null;
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void join(EntityJoinLevelEvent event) {
        if (!inside(event.getLevel())) return;
        Entity child = event.getEntity();
        MirrorSpellHand hand = MirrorSpellHand.CASTING.get();
        EchoDouble echo = hand != null ? hand.body : null;
        if (child instanceof Projectile missile && missile.getOwner() instanceof LivingEntity owner) {
            if (echo == null && !(owner instanceof EchoDouble d && d.melee())) echo = MirrorSpells.body(owner);
            // Native combat projectiles can keep the mob as owner. Authored spell projectiles
            // (e.g. ignition) call ServerPlayer-only impact handlers throughout their flight.
            if (owner instanceof MirrorSpellHand cast && vanilla(child)) missile.setOwner(cast.body);
            else if(owner instanceof EchoDouble restored && !restored.melee() && !vanilla(child))
                missile.setOwner(MirrorSpellHand.hand(restored));
            if (active(owner) && !vanilla(child)) { event.setCanceled(true); return; }
        }
        if (echo == null || echo.runId() == null || child == echo || child instanceof EchoCore || child instanceof EchoDouble) return;
        child.getPersistentData().putUUID(OWNER,echo.getUUID());
        child.getPersistentData().putUUID(RUN,echo.runId());
        if (child instanceof net.minecraft.world.entity.Mob mob && echo.getTarget() != null) mob.setTarget(echo.getTarget());
    }

    @SubscribeEvent
    public static void target(LivingChangeTargetEvent event) {
        var data = event.getEntity().getPersistentData();
        if (!data.hasUUID(OWNER) || !(event.getEntity().level() instanceof ServerLevel level)) return;
        if (!(level.getEntity(data.getUUID(OWNER)) instanceof EchoDouble echo) || echo.melee() || !echo.isAlive()) {
            event.setNewTarget(null);
            return;
        }
        if (!MirrorSpells.enemy(echo, event.getNewTarget())) event.setNewTarget(MirrorFight.target(echo));
    }

    private static boolean vanilla(Entity e) {
        var id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return id != null && id.getNamespace().equals("minecraft");
    }
    private static boolean spell(DamageSource source) {
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypes.DRAGON_BREATH) || source.is(DamageTypes.WITHER)) return true;
        if (source.typeHolder().unwrapKey().map(k -> !k.location().getNamespace().equals("minecraft")).orElse(false)) return true;
        Entity direct = source.getDirectEntity();
        return direct instanceof MirrorSpellHand || direct != null && (direct.getPersistentData().hasUUID(OWNER)
                || !vanilla(direct) && !(direct instanceof EchoDouble) && !(direct instanceof MirrorHook));
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void attack(LivingAttackEvent event) {
        LivingEntity target = event.getEntity();
        if (!inside(target.level())) return;
        DamageSource source = event.getSource();
        Entity attacker=source.getEntity();
        EchoDouble caster=attacker instanceof LivingEntity living ? MirrorSpells.body(living) : null;
        if(caster == null && attacker != null) caster=summonOwner(attacker);
        if (caster != null && !caster.melee() && !MirrorSpells.enemy(caster,target)) {
            event.setCanceled(true);
            return;
        }
        Entity direct = source.getDirectEntity();
        if (direct != null && direct.getPersistentData().hasUUID(OWNER)) {
            var owner = ((ServerLevel) target.level()).getEntity(direct.getPersistentData().getUUID(OWNER));
            if (!(owner instanceof EchoDouble echo) || !MirrorSpells.enemy(echo,target)) { event.setCanceled(true); return; }
        }
        if (!spell(source)) return;
        if (active(target) || target instanceof EchoDouble echo && echo.melee()
                || source.getEntity() instanceof LivingEntity from && active(from)) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof ServerPlayer p)) return;
        boolean active = active(p);
        var data = p.getPersistentData();
        boolean was = data.getBoolean(ENTERED);
        if (active == was) return;
        if (active) {
            data.putBoolean(ENTERED,true);
            CombatCasting.cancel(p);
            com.tnc.tnc.magic.free.FreeMagicServer.interruptSpells(p);
            com.tnc.tnc.magic.domain.DomainService.cancelPending(p.getUUID());
            // Remove earlier magical auras but keep vanilla combat consumables and unrelated player states.
            for (var effect : List.copyOf(p.getActiveEffects())) {
                ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect.getEffect());
                if (id != null && (id.getNamespace().equals("spell_engine") || id.getNamespace().equals("spell_power")
                        || id.getNamespace().equals("tnc") && com.tnc.tnc.magic.TNEffects.EFFECTS.getEntries()
                        .stream().anyMatch(e->e.getId().equals(id))
                        || com.tnc.tnc.magic.TNWindMechanics.WIND_EFFECTS.getEntries().stream().anyMatch(e->e.getId().equals(id))
                        || com.tnc.tnc.magic.TNFireMechanics.FIRE_EFFECTS.getEntries().stream().anyMatch(e->e.getId().equals(id)))) p.removeEffect(effect.getEffect());
            }
        } else data.remove(ENTERED);
        var magic = com.tnc.tnc.magic.MagicStone.getOrNull(p);
        if (magic != null) com.tnc.tnc.network.MagicStoneNetwork.syncTo(p);
    }
    @SubscribeEvent public static void removed(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof EchoDouble mob) || !(event.getLevel() instanceof ServerLevel level)) return;
        MirrorSpellHand.clear(mob);
        if (mob.getRemovalReason()!=null && mob.getRemovalReason().shouldDestroy()) {
            var id=mob.getUUID();
            for (var child:level.getEntities(mob,mob.getBoundingBox().inflate(128),e->{
                var data=e.getPersistentData();
                return data.hasUUID(OWNER) && id.equals(data.getUUID(OWNER));
            })) child.discard();
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { MirrorSpellHand.stop(); }
}
