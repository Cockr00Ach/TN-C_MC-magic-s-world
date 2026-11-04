package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class WarriorBowCharge {
    private static final class Charge {
        final WarriorMove move;final ItemStack bow;final long start;final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final WarriorWeaponFamily family;final WarriorAnimation animation;
        boolean activated;
        long pulse;
        Charge(ServerPlayer p,WarriorMove m,boolean immediate){move=m;family=WarriorWeapons.family(p.getMainHandItem());animation=WeaponChargeRules.holdAnimation(family);bow=p.getMainHandItem();start=p.serverLevel().getGameTime()-(immediate?0:ComboTracker.HOLD_TICKS);pulse=p.serverLevel().getGameTime();dimension=p.level().dimension();}
    }
    private static final Map<UUID,Charge> ACTIVE=new HashMap<>();
    private static final java.util.Set<UUID> INTERRUPTED=new java.util.HashSet<>();
    private static final Map<UUID,Long> NEXT_SHOT=new HashMap<>();
    public static boolean input(ServerPlayer p,WarriorMove move,int mode){
        var id=p.getUUID();
        boolean immediate=mode==WarriorComboInputPacket.CHARGE_START && move==WarriorMove.HORIZONTAL;
        if(immediate){cancel(p);INTERRUPTED.remove(id);}
        if(INTERRUPTED.contains(id)){
            if(mode==WarriorComboInputPacket.RELEASE||mode==WarriorComboInputPacket.TAP)INTERRUPTED.remove(id);
            return true;
        }
        var charge=ACTIVE.get(id);
        if(charge!=null){
            if(move==WarriorMove.HORIZONTAL && mode==WarriorComboInputPacket.TAP){ACTIVE.remove(id);p.stopUsingItem();WarriorAnimationPacket.cancel(p);return false;}
            if(!valid(p,charge)){cancel(p);return true;}
            if(mode==WarriorComboInputPacket.HOLD && !charge.activated){activate(p,charge);return true;}
            if(move!=charge.move){if(mode==WarriorComboInputPacket.TAP)cancel(p);return true;}
            if(mode==WarriorComboInputPacket.KEEPALIVE){charge.pulse=p.serverLevel().getGameTime();return true;}
            if(mode==WarriorComboInputPacket.RELEASE){ACTIVE.remove(id);p.stopUsingItem();if(charge.activated)fire(p,charge);if(charge.family==WarriorWeaponFamily.BOW)WarriorAnimationPacket.release(p,charge.animation,20);return true;}
            return true;
        }
        var family=WarriorWeapons.family(p.getMainHandItem());
        if((mode!=WarriorComboInputPacket.HOLD && !immediate) || family==null || (move!=WarriorMove.HORIZONTAL && family!=WarriorWeaponFamily.BOW))return false;
        if(move==WarriorMove.RIGHT && WarriorComboService.state(p.getUUID()).tracker.moves().equals(WarriorMove.parse("eqe")))return false;
        long now=p.serverLevel().getGameTime();
        if(!WarriorComboService.eligible(p)||now<NEXT_SHOT.getOrDefault(id,0L)||(family==WarriorWeaponFamily.BOW&&!WarriorComboService.hasAmmo(p))||WarriorComboService.state(id).interruptedUntil>WarriorComboService.gameTime(p))return true;
        var created=new Charge(p,move,immediate);ACTIVE.put(id,created);
        if(!immediate)activate(p,created);
        return true;
    }
    private static void activate(ServerPlayer p,Charge c){
        c.activated=true;c.pulse=p.level().getGameTime();
        WarriorComboService.clearCombo(p);var state=WarriorComboService.state(p.getUUID());state.heldSkill=null;state.stanceUntil=Long.MIN_VALUE;state.finisherUntil=0;
        WarriorAnimationPacket.cancel(p);
        if(c.family==WarriorWeaponFamily.BOW)p.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        WarriorAnimationPacket.hold(p,c.animation,c.animation.durationTicks());
    }
    private static boolean valid(ServerPlayer p,Charge c){return WarriorComboService.eligible(p)&&p.level().dimension().equals(c.dimension)
            &&p.getMainHandItem()==c.bow&&WarriorWeapons.family(c.bow)==c.family;}
    public static void cancel(ServerPlayer p){if(p==null)return;var removed=ACTIVE.remove(p.getUUID());if(removed!=null){if(removed.move==WarriorMove.HORIZONTAL)INTERRUPTED.add(p.getUUID());p.stopUsingItem();WarriorAnimationPacket.cancel(p);}}
    private static void fire(ServerPlayer p,Charge c){
        if(!valid(p,c)||p.serverLevel().getGameTime()-c.pulse>40)return;
        if(c.family!=WarriorWeaponFamily.BOW){fireMelee(p,c);return;}
        if(!WarriorComboService.hasAmmo(p))return;
        float power=BowChargeRules.power(p.serverLevel().getGameTime()-c.start);
        ItemStack ammunition=WarriorComboService.ammo(p).copy();
        boolean infinite=p.getAbilities().instabuild||EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS,c.bow)>0;
        if(!infinite)WarriorComboService.ammo(p).shrink(1);
        int count=BowChargeRules.arrows(c.move);
        for(int i=0;i<count;i++){
            Arrow a=WarriorArrowEntity.create(p.level(),p,ammunition,c.bow);a.setPos(p.getX(),p.getEyeY()-.1,p.getZ());
            a.shootFromRotation(p,p.getXRot(),p.getYRot()+(i-(count-1)/2f)*(6-2*power),0,2+1.6f*power,.25f);
            a.setBaseDamage(c.move==WarriorMove.HORIZONTAL?8:count==3?2+2*power:3+5*power);
            if(c.move==WarriorMove.HORIZONTAL)a.getPersistentData().putDouble("TncZChargeDamage",3+5*WeaponChargeRules.multiplier(p.level().getGameTime()-c.start));a.setCritArrow(power>=1);a.setGlowingTag(true);
            if(c.move==WarriorMove.LEFT)a.setPierceLevel((byte)(power>=1?3:1));
            if(c.move==WarriorMove.HORIZONTAL){a.getPersistentData().putBoolean("TncImpactArrow",true);a.getPersistentData().putFloat("TncChargePower",power);a.getPersistentData().putDouble("TncChargeMultiplier",WeaponChargeRules.multiplier(p.level().getGameTime()-c.start));}
            a.pickup=infinite||count>1?AbstractArrow.Pickup.CREATIVE_ONLY:AbstractArrow.Pickup.ALLOWED;
            p.serverLevel().addFreshEntity(a);
        }
        c.bow.hurtAndBreak(1,p,owner->owner.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
        NEXT_SHOT.put(p.getUUID(),p.serverLevel().getGameTime()+10);
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.ARROW_SHOOT,SoundSource.PLAYERS,1,.7f+.4f*power);
        p.displayClientMessage(Component.literal(c.move==WarriorMove.HORIZONTAL?String.format(java.util.Locale.ROOT,"震荡箭 · 蓄力 %.1f×",WeaponChargeRules.multiplier(p.level().getGameTime()-c.start)):BowChargeRules.name(c.move)+" · "+Math.round(power*100)+"%"),true);
    }
    public static boolean charging(ServerPlayer p){return ACTIVE.containsKey(p.getUUID());}
    @SubscribeEvent public static void preventAutoAttack(net.minecraftforge.event.entity.player.AttackEntityEvent e){if(e.getEntity() instanceof ServerPlayer p && charging(p))e.setCanceled(true);}
    private static void fireMelee(ServerPlayer p,Charge c){
        double mult=WeaponChargeRules.multiplier(p.level().getGameTime()-c.start);
        boolean scythe=p.getMainHandItem().is(com.tnc.tnc.TNMod.DOOM_SCYTHE.get());
        var attack=scythe?WarriorAnimation.SCYTHE_REAP:switch(c.family){case GREATSWORD->WarriorAnimation.GS_SMASH;case HAMMER->WarriorAnimation.HM_QUAKE;case SPEAR->WarriorAnimation.SPEAR_BREAK;default->WarriorAnimation.SWORD_BREAKER;};
        WarriorAnimationPacket.broadcast(p,attack,attack.durationTicks());
        double radius=(c.family==WarriorWeaponFamily.SPEAR?4.2:3.2)*WarriorPhantom.scale(p);
        int arc=scythe?360:c.family==WarriorWeaponFamily.SPEAR?55:c.family==WarriorWeaponFamily.HAMMER?180:120;
        var base=WarriorCombos.bySlot(c.family,0);
        var charged=new WarriorSkill(base.id(),base.codexId(),base.name()+"·蓄力",base.sequence(),0,base.cooldownTicks(),(float)mult,
            radius/WarriorPhantom.scale(p),arc,.6,1,"松手蓄力攻击",c.family,WarriorChain.ATTACK,0,false,false,0,attack,
            java.util.Collections.nCopies(base.length(),false),0,0,0);
        WarriorComboService.melee(p,charged,WarriorComboService.state(p.getUUID()),WarriorComboService.gameTime(p));
        c.bow.hurtAndBreak(1,p,owner->owner.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
        NEXT_SHOT.put(p.getUUID(),p.level().getGameTime()+10);
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,1,.8f);
        p.displayClientMessage(Component.literal(String.format(java.util.Locale.ROOT,"蓄力斩 · %.1f×",mult)),true);
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGH)
    public static void unboundedArrowDamage(net.minecraftforge.event.entity.living.LivingHurtEvent e){
        if(e.getSource().getDirectEntity() instanceof Arrow arrow && arrow.getPersistentData().contains("TncZChargeDamage")){
            double base=arrow.getPersistentData().getDouble("TncZChargeDamage");
            if(base>0&&Double.isFinite(base))e.setAmount((float)(base*Math.max(.1,arrow.getDeltaMovement().length())));
        }
    }
    @SubscribeEvent public static void damaged(net.minecraftforge.event.entity.living.LivingDamageEvent e){if(e.getAmount()>0&&e.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        var c=ACTIVE.get(p.getUUID());if(c==null)return;
        long now=p.serverLevel().getGameTime();
        if(!valid(p,c)||now-c.pulse>40){cancel(p);return;}
        if(!c.activated)return;
        if(now%40==0)WarriorAnimationPacket.hold(p,c.animation,c.animation.durationTicks());
        if(now%4!=0)return;
        if(c.family!=WarriorWeaponFamily.BOW){
            p.displayClientMessage(Component.literal(String.format(java.util.Locale.ROOT,"蓄力 %.1f 秒 · %.1f× · 松手攻击／受击中断",(now-c.start)/20.0,WeaponChargeRules.multiplier(now-c.start))),true);return;
        }
        float power=BowChargeRules.power(now-c.start);
        var style=WarriorCombatEffects.style(p);
        if(style==WarriorEffectStyle.STEEL)style=c.move==WarriorMove.LEFT?WarriorEffectStyle.WIND:c.move==WarriorMove.RIGHT?WarriorEffectStyle.FIRE:WarriorEffectStyle.EARTH;
        int color=style.color;
        var dust=new DustParticleOptions(new Vector3f((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f),.7f+power*.4f);
        Vec3 tip=p.getEyePosition().add(p.getLookAngle().scale(.9)).add(0,-.15,0);
        for(int i=0;i<3;i++){
            double phase=(now*.12+i*Math.PI*2/3);Vec3 from=tip.add(Math.cos(phase)*1.1,Math.sin(phase)*.8,.3);
            Vec3 velocity=tip.subtract(from).scale(.16);
            p.serverLevel().sendParticles(dust,from.x,from.y,from.z,0,velocity.x,velocity.y,velocity.z,1);
        }
        if(c.move==WarriorMove.HORIZONTAL)p.displayClientMessage(Component.literal(String.format(java.util.Locale.ROOT,"蓄力 %.1f 秒 · %.1f× · 松手攻击／受击中断",(now-c.start)/20.0,WeaponChargeRules.multiplier(now-c.start))),true);
        else p.displayClientMessage(Component.literal(BowChargeRules.name(c.move)+" · 蓄力 "+Math.round(power*100)+"% · 松手发射"),true);
    }
    @SubscribeEvent public static void impact(ProjectileImpactEvent event){
        if(!(event.getProjectile() instanceof Arrow arrow)||arrow.level().isClientSide||!arrow.getPersistentData().getBoolean("TncImpactArrow")
                ||arrow.getPersistentData().getBoolean("TncImpactSpent")||!(arrow.getOwner() instanceof ServerPlayer p))return;
        arrow.getPersistentData().putBoolean("TncImpactSpent",true);
        float power=arrow.getPersistentData().getFloat("TncChargePower");Vec3 at=event.getRayTraceResult().getLocation();
        var direct=event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit?hit.getEntity():null;
        for(var target:p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new net.minecraft.world.phys.AABB(at,at).inflate(1.5+power),
                e->e!=p&&e!=direct&&e.isAlive()&&!p.isAlliedTo(e)&&p.canAttack(e))){
            if(p.serverLevel().clip(new net.minecraft.world.level.ClipContext(at.add(0,.1,0),target.getBoundingBox().getCenter(),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,arrow)).getType()!=net.minecraft.world.phys.HitResult.Type.MISS)continue;
            target.hurt(p.damageSources().playerAttack(p),(float)((2+3*power)*Math.max(1,arrow.getPersistentData().getDouble("TncChargeMultiplier"))));Vec3 push=target.position().subtract(at).normalize().scale(.4+.7*power);target.push(push.x,.2,push.z);
        }
        p.serverLevel().playSound(null,net.minecraft.core.BlockPos.containing(at),SoundEvents.PLAYER_ATTACK_KNOCKBACK,SoundSource.PLAYERS,.8f,.7f);
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){ACTIVE.remove(p.getUUID());NEXT_SHOT.remove(p.getUUID());INTERRUPTED.remove(p.getUUID());}}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e){ACTIVE.clear();NEXT_SHOT.clear();INTERRUPTED.clear();}
}
