package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.combat.DownedCombat;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/** Server-confirmed vanilla particle effects: nearby clients see the same bounded geometry. */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class WarriorCombatEffects {
    private static final Map<UUID,State> STATES=new HashMap<>();
    private static final class State{long slash=Long.MIN_VALUE,block=Long.MIN_VALUE,impact=Long.MIN_VALUE,lastGuard=Long.MIN_VALUE,flash=Long.MIN_VALUE;boolean guarding;int combo;UUID guardTarget;}
    private WarriorCombatEffects(){}
    private static boolean eligible(ServerPlayer p){return p.isAlive()&&!p.isSpectator()&&!p.isCreative()&&!DownedCombat.isDowned(p)&&TemperingData.get(p.server).enrolled(p.getUUID());}
    static WarriorEffectStyle style(ServerPlayer p){
        var stack=p.getMainHandItem();
        var infused=WindInfusion.weapon(stack)&&stack.hasTag()?WarriorEffectStyle.find(stack.getTag().getString(WindInfusion.ELEMENT_KEY)):null;
        return infused!=null?infused:WarriorEffectsData.get(p.server).style(p.getUUID());
    }
    private static Vec3 forward(ServerPlayer p){double yaw=Math.toRadians(p.getYRot());return new Vec3(-Math.sin(yaw),0,Math.cos(yaw));}
    private static Vec3 right(ServerPlayer p){var f=forward(p);return new Vec3(-f.z,0,f.x);}
    private static DustParticleOptions dust(WarriorEffectStyle s,float size){int c=s.color;return new DustParticleOptions(new Vector3f(((c>>16)&255)/255f,((c>>8)&255)/255f,(c&255)/255f),size);}
    private static ParticleOptions accent(WarriorEffectStyle s){return switch(s){case FIRE->ParticleTypes.SMALL_FLAME;case WATER->ParticleTypes.SPLASH;case ICE->ParticleTypes.SNOWFLAKE;case LIGHTNING->ParticleTypes.ELECTRIC_SPARK;case WIND->ParticleTypes.CLOUD;case EARTH->new BlockParticleOption(ParticleTypes.BLOCK,net.minecraft.world.level.block.Blocks.SANDSTONE.defaultBlockState());case LIGHT->ParticleTypes.END_ROD;case DARK->ParticleTypes.WITCH;default->ParticleTypes.CRIT;};}
    private static void point(ServerPlayer p,ParticleOptions particle,Vec3 at){p.serverLevel().sendParticles(particle,at.x,at.y,at.z,1,0,0,0,0);}
    static void slash(ServerPlayer p,boolean reverse){
        var s=style(p);var origin=p.position().add(0,1.05,0);var f=forward(p);var r=right(p);var dust=dust(s,.85f);
        for(int i=0;i<WarriorEffectStyle.budget("slash");i++){
            double t=i/26.0,a=-1.15+2.3*t;double vertical=(reverse?-1:1)*(.38-.76*t);
            double radius=1.65;
            if(s==WarriorEffectStyle.LIGHTNING){radius+=(i%2==0?.08:-.08);vertical+=(i%3-1)*.06;}
            if(s==WarriorEffectStyle.WATER)radius+=Math.sin(t*Math.PI*4)*.10;
            if(s==WarriorEffectStyle.FIRE)vertical+=Math.sin(t*Math.PI)*.22;
            if(s==WarriorEffectStyle.EARTH)vertical*=.45;
            if(s==WarriorEffectStyle.DARK)vertical=-vertical;
            var at=origin.add(f.scale(Math.cos(a)*radius)).add(r.scale(Math.sin(a)*radius)).add(0,vertical,0);
            point(p,dust,at);if(i%6==0)point(p,accent(s),at);
            if(i%3==0)point(p,dust,origin.add(at.subtract(origin).scale(.84)));
            if(s==WarriorEffectStyle.ICE&&i%9==0){point(p,dust,at.add(0,.13,0));point(p,dust,at.add(0,-.13,0));}
        }
    }
    static void guard(ServerPlayer p,boolean perfect,boolean raising){
        var s=style(p);var f=p.getLookAngle().normalize();var r=right(p);if(r.lengthSqr()<.1)r=new Vec3(1,0,0);
        var up=f.cross(r).normalize();var center=p.getEyePosition().add(f.scale(.85)).add(0,-.35,0);
        int n=raising?12:WarriorEffectStyle.budget("block");
        for(int i=0;i<n;i++){double a=Math.PI*2*i/n;double radius=s==WarriorEffectStyle.LIGHTNING&&i%2==0?.68:.58;var at=center.add(r.scale(Math.cos(a)*radius)).add(up.scale(Math.sin(a)*radius));point(p,dust(s,perfect?1.1f:.75f),at);if(!raising&&i%6==0){point(p,accent(s),at);if(s==WarriorEffectStyle.LIGHT||s==WarriorEffectStyle.ICE)point(p,dust(s,.8f),center.add(at.subtract(center).scale(.55)));}}
        if(perfect){p.serverLevel().sendParticles(ParticleTypes.FLASH,center.x,center.y,center.z,1,0,0,0,0);p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,.45f,1.55f);}
    }
    static void windCut(ServerPlayer p,Vec3 start,Vec3 end){
        var delta=end.subtract(start);var axis=delta.normalize();var side=axis.cross(new Vec3(0,1,0));if(side.lengthSqr()<.01)side=new Vec3(1,0,0);side=side.normalize();var up=side.cross(axis).normalize();
        int steps=Math.max(1,Math.min(36,(int)(delta.length()*4)));var color=dust(WarriorEffectStyle.WIND,.8f);
        for(int i=0;i<=steps;i++){double t=i/(double)steps,a=t*Math.PI*4;var center=start.add(delta.scale(t));var offset=side.scale(Math.cos(a)*.16).add(up.scale(Math.sin(a)*.16));point(p,color,center.add(offset));point(p,color,center.subtract(offset));if(i%4==0)point(p,ParticleTypes.CLOUD,center);}
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        if(!eligible(p)){STATES.remove(p.getUUID());return;}
        var state=STATES.computeIfAbsent(p.getUUID(),k->new State());long now=p.server.overworld().getGameTime();
        boolean guarding=p.isBlocking();if(guarding&&!state.guarding)guard(p,false,true);state.guarding=guarding;
        if(WindInfusion.weapon(p.getMainHandItem())&&p.swinging&&p.swingTime<=1&&(state.slash==Long.MIN_VALUE||now-state.slash>=6)){
            if(state.slash==Long.MIN_VALUE||now-state.slash>20)state.combo=0;
            state.slash=now;slash(p,(state.combo++&1)==1);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void block(ShieldBlockEvent event){
        if(!(event.getEntity() instanceof ServerPlayer p)||!eligible(p)||event.getBlockedDamage()<=0)return;
        var state=STATES.computeIfAbsent(p.getUUID(),k->new State());long now=p.server.overworld().getGameTime();state.lastGuard=now;
        state.guardTarget=event.getDamageSource().getEntity()==null?null:event.getDamageSource().getEntity().getUUID();
        if(state.block!=Long.MIN_VALUE&&now-state.block<6)return;state.block=now;
        boolean perfect=p.getTicksUsingItem()<=10&&(state.flash==Long.MIN_VALUE||now-state.flash>=20);if(perfect)state.flash=now;
        guard(p,perfect,false);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void hit(LivingDamageEvent event){
        if(event.getAmount()<=0||!(event.getSource().getEntity() instanceof ServerPlayer p)||!eligible(p)||!WindInfusion.weapon(p.getMainHandItem())
            ||event.getSource().getDirectEntity()!=p||!event.getSource().is(DamageTypes.PLAYER_ATTACK))return;
        var state=STATES.computeIfAbsent(p.getUUID(),k->new State());long now=p.server.overworld().getGameTime();if(state.impact!=Long.MIN_VALUE&&now-state.impact<6)return;state.impact=now;
        var center=event.getEntity().getBoundingBox().getCenter();var s=style(p);boolean counter=state.lastGuard!=Long.MIN_VALUE&&now-state.lastGuard<=40&&event.getEntity().getUUID().equals(state.guardTarget);
        int count=counter?12:6;p.serverLevel().sendParticles(accent(s),center.x,center.y,center.z,count,.2,.3,.2,.025);
        if(counter){p.serverLevel().sendParticles(ParticleTypes.CRIT,center.x,center.y,center.z,8,.2,.25,.2,.1);state.lastGuard=Long.MIN_VALUE;p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.PLAYER_ATTACK_CRIT,SoundSource.PLAYERS,.55f,1.2f);}
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("tempering").then(Commands.literal("effects")
            .executes(ctx->{var p=ctx.getSource().getPlayerOrException();p.sendSystemMessage(Component.literal("战技光效："+WarriorEffectsData.get(p.server).style(p.getUUID()).label+"。可选 steel/fire/water/ice/lightning/wind/earth/light/dark；武器灌注优先。仅改变表现。"));return 1;})
            .then(Commands.argument("style",StringArgumentType.word()).suggests((ctx,b)->{for(var s:WarriorEffectStyle.values())if(s.id.startsWith(b.getRemaining()))b.suggest(s.id);return b.buildFuture();}).executes(ctx->{
                var p=ctx.getSource().getPlayerOrException();var s=WarriorEffectStyle.find(StringArgumentType.getString(ctx,"style"));
                if(s==null||!TemperingData.get(p.server).enrolled(p.getUUID())){p.sendSystemMessage(Component.literal("请选择有效元素光效，并先具备战士天赋。"));return 0;}
                WarriorEffectsData.get(p.server).set(p.getUUID(),s);p.sendSystemMessage(Component.literal("战技光效已切换为"+s.label+"；灌注武器优先使用自身元素。"));guard(p,false,true);return 1;
            }))));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){STATES.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void changed(PlayerEvent.PlayerChangedDimensionEvent e){STATES.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){STATES.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){STATES.clear();}
}
