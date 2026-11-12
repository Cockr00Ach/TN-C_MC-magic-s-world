package com.tnc.tnc.warrior;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.*;
import net.minecraft.sounds.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import com.tnc.tnc.warrior.WarriorDomainLink;

/** Finite server-owned cast; never modifies the persistent invulnerable player flag. */
@Mod.EventBusSubscriber(modid="tnc")
public final class WarriorUltimateService {
    private record Cast(WarriorUltimates.Art art,long start,ItemStack weapon,int slot,float yaw,
                        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {}
    private static final Map<UUID,Cast> ACTIVE=new HashMap<>();
    private static final Map<UUID,WarriorUltimateInput> INPUT=new HashMap<>();
    private static final String READY="tncUltimateReady";
    public static boolean active(ServerPlayer p){
        if(p==null)return false;var c=ACTIVE.get(p.getUUID());
        return c!=null && valid(p,c) && p.serverLevel().getGameTime()-c.start()<WarriorUltimates.DURATION;
    }
    private static boolean valid(ServerPlayer p,Cast c){return p.isAlive()&&!p.isSpectator()
            &&!com.tnc.tnc.combat.DownedCombat.isDowned(p)&&p.level().dimension().equals(c.dimension())
            &&p.getInventory().selected==c.slot()&&p.getMainHandItem()==c.weapon();}
    public static boolean input(ServerPlayer p,WarriorMove move,long now){
        var id=ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem());if(id==null)return false;
        var art=WarriorUltimates.forId(id.toString());
        if(!INPUT.computeIfAbsent(p.getUUID(),x->new WarriorUltimateInput()).push(id.toString(),art.keys(),move.key(),now))return false;
        WarriorBowCharge.cancel(p);
        var state=WarriorComboService.state(p.getUUID());state.heldSkill=null;state.stanceUntil=0;
        state.finisherUntil=now+WarriorUltimates.DURATION;
        p.getPersistentData().remove(READY);
        ACTIVE.put(p.getUUID(),new Cast(art,now,p.getMainHandItem(),p.getInventory().selected,p.getYRot(),p.level().dimension()));
        WarriorAnimationPacket.broadcast(p,WarriorAnimation.ULTIMATE,WarriorUltimates.DURATION);
        p.displayClientMessage(net.minecraft.network.chat.Component.literal("✦ "+art.name()+" · "+art.description()),true);
        return true;
    }
    public static void cancel(ServerPlayer p){if(p!=null){INPUT.remove(p.getUUID());WarriorSwordUltimate.clear(p);if(ACTIVE.remove(p.getUUID())!=null){WarriorComboService.state(p.getUUID()).finisherUntil=0;WarriorAnimationPacket.cancel(p);}}}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void immune(LivingAttackEvent e){if(e.getEntity() instanceof ServerPlayer p&&active(p))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void attack(net.minecraftforge.event.entity.player.AttackEntityEvent e){
        if(e.getEntity() instanceof ServerPlayer p&&active(p))e.setCanceled(true);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p))return;
        var c=ACTIVE.get(p.getUUID());if(c==null)return;
        long age=p.serverLevel().getGameTime()-c.start();
        if(!valid(p,c)||age>=WarriorUltimates.DURATION){cancel(p);return;}
        // Grounded choreography: no teleporting through boss walls, keep gravity intact.
        p.setDeltaMovement(0,p.getDeltaMovement().y,0);
        p.setYRot(c.yaw());p.yBodyRot=c.yaw();
        if(WarriorWeapons.family(p.getMainHandItem())==WarriorWeaponFamily.LONGSWORD){WarriorSwordUltimate.tick(p,(int)age);return;}
        for(int i=0;i<c.art().strokes().size();i++){
            var s=c.art().strokes().get(i);if(age==s.tick())strike(p,c,s,i==c.art().strokes().size()-1);
        }
    }
    private static void strike(ServerPlayer p,Cast c,WarriorUltimates.Stroke s,boolean finale){
        var direction=WarriorUltimates.direction(c.yaw(),s);
        String element=WarriorDomainLink.activeElementId(p);
        for(var target:WarriorComboService.targets(p,s.radius())){
            if(!WarriorUltimates.contains(s,target.position().subtract(p.position()),direction))continue;
            if(!target.hurt(p.damageSources().playerAttack(p),(float)p.getAttributeValue(Attributes.ATTACK_DAMAGE)*s.multiplier()))continue;
            if(finale){
                target.knockback(.7,direction.x*-1,direction.z*-1);
                if(element!=null)switch(element){
                    case "fire" -> target.setSecondsOnFire(5);
                    case "water" -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,1));
                    case "earth" -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,40,3));
                    case "dark" -> target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,80,0));
                    case "wind" -> target.knockback(1.5,-direction.x,-direction.z);
                    case "lightning" -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,16,4));
                    default -> {}
                }
            }
        }
        if(finale&&"light".equals(element))p.heal(4);
        p.serverLevel().playSound(null,p.blockPosition(),finale?SoundEvents.TRIDENT_THUNDER:SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,finale?.7f:.6f,finale?1.35f:1.1f);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){e.getEntity().getPersistentData().putLong(READY,e.getOriginal().getPersistentData().getLong(READY));}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e){ACTIVE.clear();INPUT.clear();}
}
