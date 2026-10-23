package com.tnc.tnc.dungeon.ecology;

import com.tnc.tnc.dungeon.DungeonService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** A closed coffer becomes a six-legged crystal predator, rather than a walking chest. */
public final class MimicCrystalCrab extends EcologyMob {
    public MimicCrystalCrab(EntityType<? extends Monster> type,Level level){super(type,level,"crab");}
    @Override public String ecologyKind(){return "crab";}
    public boolean awakened(){return action()!=0;}
    public void awaken(){if(!awakened()){action(1);playSound(SoundEvents.CHEST_OPEN,1,.65f);}}
    @Override protected void registerGoals(){
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(2,new MeleeAttackGoal(this,1.15,false){@Override public boolean canUse(){return awakened()&&canFight()&&super.canUse();}@Override public boolean canContinueToUse(){return awakened()&&canFight()&&super.canContinueToUse();}});
        goalSelector.addGoal(6,new LookAtPlayerGoal(this,Player.class,8));
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,10,true,false,p->p instanceof ServerPlayer sp&&DungeonService.mayTarget(this,sp)&&(awakened()||distanceToSqr(sp)<3.24)));
    }
    @Override public void tick(){super.tick();if(level().isClientSide||!canFight())return;if(getTarget()!=null&&distanceToSqr(getTarget())<3.24)awaken();if(!awakened())getNavigation().stop();}
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand){if(player instanceof ServerPlayer p&&DungeonService.mayTarget(this,p)&&canFight()){awaken();setTarget(player);p.displayClientMessage(net.minecraft.network.chat.Component.literal("宝匣里传来晶钳的咔哒声！"),true);}return InteractionResult.sidedSuccess(level().isClientSide);}
    @Override public boolean hurt(DamageSource source,float amount){if(!level().isClientSide&&amount>0)awaken();return super.hurt(source,amount);}
    @Override public boolean applyPotion(ServerPlayer player,String potion){
        if(EcologyHooks.wastePotion(potion)&&canReceivePotion(player)){awaken();playSound(SoundEvents.GLASS_BREAK,1,.65f);return hurt(player.damageSources().indirectMagic(player,player),Float.MAX_VALUE);}
        return super.applyPotion(player,potion);
    }
}
