package com.tnc.tnc.warrior;

import com.tnc.tnc.bronze.BronzeWeaponItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Adapt the unique thrown dagger to existing warrior target rules and impact effects. */
@Mod.EventBusSubscriber(modid="tnc")
public final class WarriorBronzeBridge {
    private WarriorBronzeBridge(){}
    public static boolean canThrow(Player p){
        return p.isAlive()&&!p.isSpectator()&&!com.tnc.tnc.bronze.BronzeTwinWeapon.blocked(p)&&!com.tnc.tnc.combat.DownedCombat.isDowned(p)
            &&!com.tnc.tnc.boss.fire.TortoiseStun.isStunned(p)
            &&(!(p instanceof ServerPlayer server)||!WarriorComboService.performing(server,p.level().getGameTime()));
    }
    public static boolean validTarget(ServerPlayer player,LivingEntity target){
        return WarriorComboService.validTarget(player,target);
    }
    public static void impact(ServerPlayer player,LivingEntity target,boolean heavy){WarriorCombatEffects.hitSpark(player,target,heavy);}
    public static java.util.List<WarriorSkill> puppetSkills(){return WarriorCombos.forFamily(WarriorWeaponFamily.LONGSWORD).stream().filter(s->!s.ranged()&&!s.anyHold()&&s.guardTicks()==0).limit(3).toList();}
    /** Mob adaptation of the same warrior skill data, target rules, arc and interruption effects. */
    public static void puppetMelee(com.tnc.tnc.bronze.BronzePuppetEntity actor,ServerPlayer owner,WarriorSkill skill){
        if(!com.tnc.tnc.bronze.BronzeTwinWeapon.active(actor))return;
        for(var target:actor.level().getEntitiesOfClass(LivingEntity.class,actor.getBoundingBox().inflate(skill.radius()),t->t!=actor&&validTarget(owner,t)&&!actor.isAlliedTo(t))){
            if(!actor.hasLineOfSight(target)||!WarriorComboService.inArc(actor,target,skill.arcDegrees())||actor.distanceTo(target)>skill.radius()+target.getBbWidth()/2)continue;
            if(target.hurt(actor.damageSources().mobAttack(actor),(float)actor.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)*skill.damageMultiplier())){
                target.knockback(skill.knockback(),actor.getX()-target.getX(),actor.getZ()-target.getZ());target.hurtMarked=true;
                WarriorCombatEffects.hitSpark(owner,target,true);
                if(skill.interrupt()||skill.staggerTicks()>0)WarriorComboService.interrupt(owner,target,skill.staggerTicks());
            }
        }
    }
    @SubscribeEvent public static void attack(AttackEntityEvent e){
        var stack=e.getEntity().getMainHandItem();
        if(stack.getItem() instanceof BronzeWeaponItem&&(BronzeWeaponItem.inFlight(stack,e.getEntity().level())||com.tnc.tnc.bronze.BronzeTwinWeapon.blocked(e.getEntity())))e.setCanceled(true);
    }
    @SubscribeEvent public static void equipment(net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent e){
        if(!e.getEntity().level().isClientSide)com.tnc.tnc.bronze.BronzeTwinWeapon.updateAttributes(e.getEntity());
    }
    @SubscribeEvent public static void anvil(AnvilUpdateEvent e){
        for(var stack:new net.minecraft.world.item.ItemStack[]{e.getLeft(),e.getRight()})
            if(stack.getItem() instanceof BronzeWeaponItem&&stack.hasTag()&&stack.getTag().contains(BronzeWeaponItem.FLIGHT)){e.setCanceled(true);return;}
    }
}
