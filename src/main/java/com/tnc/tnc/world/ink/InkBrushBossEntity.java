package com.tnc.tnc.world.ink;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** A separate brush boss; does not join or replace the crane/tiger/dragon encounter. */
public final class InkBrushBossEntity extends Monster {
    private final ServerBossEvent bar=new ServerBossEvent(Component.literal("墨笔 · 点山河"),BossEvent.BossBarColor.WHITE,BossEvent.BossBarOverlay.PROGRESS);
    private final ArrayDeque<Vec3> trail=new ArrayDeque<>();
    private int stillTicks;
    public InkBrushBossEntity(EntityType<? extends InkBrushBossEntity> type,Level level){super(type,level);setPersistenceRequired();xpReward=600;setCustomName(Component.literal("墨笔 · 点山河"));setMaxUpStep(1);}
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,900).add(Attributes.ATTACK_DAMAGE,16).add(Attributes.ARMOR,12).add(Attributes.MOVEMENT_SPEED,.24).add(Attributes.FOLLOW_RANGE,48).add(Attributes.KNOCKBACK_RESISTANCE,1);}
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(2,new MeleeAttackGoal(this,1,true));goalSelector.addGoal(6,new RandomStrollGoal(this,.8));goalSelector.addGoal(8,new LookAtPlayerGoal(this,Player.class,32));targetSelector.addGoal(1,new HurtByTargetGoal(this));targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true));}
    @Override public void tick(){super.tick();
        if(!level().isClientSide){bar.setName(getDisplayName());bar.setProgress(Math.max(0,Math.min(1,getHealth()/getMaxHealth())));return;}
        if(tickCount%2!=0)return;
        Vec3 at=position().add(0,.075,0),last=trail.peekFirst();
        if(last!=null&&at.distanceToSqr(last)>64){trail.clear();last=null;}
        if(onGround()&&(last==null||at.distanceToSqr(last)>.12)){
            trail.addFirst(at);stillTicks=0;while(trail.size()>64)trail.removeLast();
        }else if(++stillTicks>10&&!trail.isEmpty())trail.removeLast();
    }
    public Iterable<Vec3> inkTrail(){return trail;}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public void startSeenByPlayer(ServerPlayer p){super.startSeenByPlayer(p);bar.addPlayer(p);}
    @Override public void stopSeenByPlayer(ServerPlayer p){super.stopSeenByPlayer(p);bar.removePlayer(p);}
    @Override public void remove(RemovalReason reason){bar.removeAllPlayers();trail.clear();super.remove(reason);}
}
