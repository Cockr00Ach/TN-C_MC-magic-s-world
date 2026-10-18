package com.tnc.tnc.life.routes.mech;

import com.tnc.tnc.life.routes.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tnc")
public final class MechCombat {
    public static ItemStack suit(Player p){var stack=p.getItemBySlot(EquipmentSlot.CHEST);return stack.getItem() instanceof RouteMechItem?stack:ItemStack.EMPTY;}
    public static long now(ServerPlayer p){return p.server.overworld().getGameTime();}
    public static void tick(ServerPlayer p,ItemStack stack){
        var tag=stack.getOrCreateTag();long now=now(p);if(tag.getLong("MechTick")==now)return;tag.putLong("MechTick",now);
        tag.putInt("MechHeat",Math.max(0,tag.getInt("MechHeat")-(tag.getLong("MechVent")>now?18:1)));
        if(!suit(p).isEmpty()&&tag.getLong("MechBoost")>now)p.fallDistance=0;
        RouteChargeLedger.get(p.serverLevel()).sync(stack);
    }
    public static boolean fire(ServerPlayer p){
        var stack=suit(p);if(stack.isEmpty()||!(p.getUseItem().getItem() instanceof RouteAccessory gun)||gun.kind!=RouteAccessory.Kind.GUN)return false;var tag=stack.getOrCreateTag();long now=now(p);boolean over=tag.getLong("MechOverdrive")>now;
        if(tag.getLong("MechVent")>now)return false;
        if(now-tag.getLong("MechLastShot")<MechRules.shotPeriod(over))return true;
        if(!RouteChargeLedger.get(p.serverLevel()).spend(stack,MechRules.shotCost(over)))return false;
        int element=Math.floorMod(tag.getInt("MechElement"),7);tag.putLong("MechLastShot",now);p.getUseItem().getOrCreateTag().putLong("MechFiring",now);p.getUseItem().getOrCreateTag().putInt("MechElement",element);tag.putInt("MechHeat",MechRules.nextHeat(tag.getInt("MechHeat"),over));
        Vec3 direction=p.getLookAngle(),from=p.getEyePosition(),to=from.add(direction.scale(64));
        var wall=p.serverLevel().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));to=wall.getLocation();
        var hit=ProjectileUtil.getEntityHitResult(p,from,to,p.getBoundingBox().expandTowards(direction.scale(64)).inflate(1),e->canHit(p,e),from.distanceToSqr(to));
        if(hit!=null&&hit.getEntity() instanceof LivingEntity victim){to=hit.getLocation();victim.invulnerableTime=Math.min(victim.invulnerableTime,1);float damage=over?6:4.5f;if(element==4)damage+=2;
            if(victim.hurt(p.damageSources().indirectMagic(p,p),damage)){
                if(element==0)victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,50,1));
                if(element==1)victim.setSecondsOnFire(3);
                if(element==3)victim.knockback(.4,-direction.x,-direction.z);
                if(element==5)victim.addEffect(new MobEffectInstance(MobEffects.GLOWING,80));
                if(element==6)p.heal(.15f);
                if(element==2){int count=0;for(var other:p.serverLevel().getEntitiesOfClass(LivingEntity.class,victim.getBoundingBox().inflate(4),e->e!=victim&&e instanceof Monster&&canHit(p,e))){if(count++==2)break;other.hurt(p.damageSources().indirectMagic(p,p),2);MechNetwork.fx(p,victim.getBoundingBox().getCenter(),other.getBoundingBox().getCenter(),2,0);}}
            }
        }
        MechNetwork.fx(p,from.add(direction.scale(.8)).add(0,-.12,0),to,element,0);
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.AMETHYST_BLOCK_HIT,SoundSource.PLAYERS,.2f,1.5f+p.getRandom().nextFloat()*.3f);
        if(tag.getInt("MechHeat")>=MechRules.MAX_HEAT){tag.putLong("MechVent",now+60);p.displayClientMessage(Component.literal("星炉过热 · 强制排热3秒"),true);return false;}return true;
    }
    public static boolean canHit(ServerPlayer p,Entity e){return e instanceof LivingEntity&&e.isAlive()&&e.isPickable()&&e!=p&&!p.isAlliedTo(e)&&!(e instanceof AbstractVillager)&&!(e instanceof TamableAnimal pet&&pet.isTame())&&(!(e instanceof Player other)||p.server.isPvpAllowed()&&p.canHarmPlayer(other))&&!(e.getType().getCategory()==MobCategory.MISC);}
    public static boolean activate(ServerPlayer p,MechNetwork.Action action){
        var stack=suit(p);if(stack.isEmpty()||!p.isAlive()||p.isSpectator())return false;var tag=stack.getOrCreateTag();long now=now(p);String key="MechCooldown"+action.name();if(tag.getLong(key)>now)return false;
        int cost=switch(action){case DASH->150;case SHIELD->200;case OVERDRIVE->1000;case NOVA->800;};
        if(!RouteChargeLedger.get(p.serverLevel()).spend(stack,cost)){p.displayClientMessage(Component.literal("机铠储魔不足"),true);return false;}
        int cooldown=switch(action){case DASH->100;case SHIELD->240;case OVERDRIVE->600;case NOVA->400;};tag.putLong(key,now+cooldown);
        int element=Math.floorMod(tag.getInt("MechElement"),7);
        switch(action){
            case DASH->{var look=p.getLookAngle();p.setDeltaMovement(look.x*1.65,Math.max(.2,Math.min(.8,look.y*1.4)),look.z*1.65);p.hurtMarked=true;tag.putLong("MechBoost",now+35);}
            case SHIELD->tag.putLong("MechShield",now+160);
            case OVERDRIVE->tag.putLong("MechOverdrive",now+160);
            case NOVA->{for(var target:p.serverLevel().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(6),e->e instanceof Monster&&canHit(p,e))){target.hurt(p.damageSources().indirectMagic(p,p),12);double dx=target.getX()-p.getX(),dz=target.getZ()-p.getZ();target.knockback(1.3,-dx,-dz);}}
        }
        MechNetwork.fx(p,p.position().add(0,1,0),p.position().add(0,1,0),element,action.ordinal()+1);
        p.serverLevel().playSound(null,p.blockPosition(),action==MechNetwork.Action.DASH?SoundEvents.FIREWORK_ROCKET_LAUNCH:SoundEvents.BEACON_ACTIVATE,SoundSource.PLAYERS,.65f,1.1f);return true;
    }
    @SubscribeEvent public static void hurt(LivingHurtEvent e){if(!(e.getEntity() instanceof ServerPlayer p)||e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))return;var stack=suit(p);if(stack.isEmpty()||stack.getOrCreateTag().getLong("MechShield")<=now(p))return;var ledger=RouteChargeLedger.get(p.serverLevel());int cost=Math.min(ledger.amount(stack),(int)Math.ceil(e.getAmount()*.8*35));float blocked=Math.min(e.getAmount()*.8f,cost/35f);if(blocked>0&&ledger.spend(stack,cost)){e.setAmount(Math.max(0,e.getAmount()-blocked));MechNetwork.fx(p,p.getBoundingBox().getCenter(),p.getBoundingBox().getCenter(),5,2);}}
}
