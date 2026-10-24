package com.tnc.tnc.dungeon.ecology;

import com.tnc.tnc.dungeon.DungeonService;
import com.tnc.tnc.mana.sight.ManaSightServer;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Gaze is validated against each observer, including occlusion and a per-player cooldown. */
public final class GlareGargoyle extends EcologyMob {
    private final Map<UUID,Long> flashed=new HashMap<>();
    public GlareGargoyle(EntityType<? extends Monster> type,Level level){super(type,level,"flash");}
    @Override public String ecologyKind(){return "flash";}
    @Override protected void registerGoals(){
        goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(2,new MeleeAttackGoal(this,1,false){@Override public boolean canUse(){return canFight()&&super.canUse();}@Override public boolean canContinueToUse(){return canFight()&&super.canContinueToUse();}});
        goalSelector.addGoal(7,new LookAtPlayerGoal(this,Player.class,12));goalSelector.addGoal(8,new RandomStrollGoal(this,.65));
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,10,true,false,p->p instanceof ServerPlayer sp&&DungeonService.mayTarget(this,sp)));
    }
    public boolean tryFlash(ServerPlayer player){
        if(!canFight()||!DungeonService.mayTarget(this,player)||!ManaSightServer.enabled(player)||distanceToSqr(player)>14*14||!player.hasLineOfSight(this))return false;
        long now=level().getGameTime();if(flashed.getOrDefault(player.getUUID(),Long.MIN_VALUE)>now)return false;
        var direction=position().add(0,getBbHeight()*.62,0).subtract(player.getEyePosition());if(direction.lengthSqr()<.01||player.getLookAngle().dot(direction.normalize())<.94)return false;
        flashed.put(player.getUUID(),now+140);action(16);player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,100));EcologyNetwork.flash(player,100);playSound(SoundEvents.AMETHYST_BLOCK_RESONATE,.9f,1.9f);return true;
    }
    @Override public void tick(){super.tick();if(!(level() instanceof ServerLevel level))return;if(action()>0)action(action()-1);if(!canFight()||tickCount%5!=0)return;for(var player:level.players())tryFlash(player);if(tickCount%200==0)flashed.entrySet().removeIf(e->e.getValue()<level.getGameTime());}
}
