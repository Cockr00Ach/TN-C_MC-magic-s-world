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

/**
 * 战技的<b>服务端</b>反馈：只有声音和"打实了"的小火花。
 *
 * <p>2026-11-03 起这条线不再广播任何"施放特效"粒子。原因不是省性能，是用户明确要求：
 * 粒子是假的体积感，挡视线、还会被客户端粒子上限吃掉一半，看起来像"糊了一团"。
 * 刀刃光效、剑气、刀波、武器虚影、龙，全部改成客户端渲染的真实几何
 * （{@code WarriorTrail} / {@code WarriorWaveRenderer} / {@code WarriorPhantomRenderer} /
 * {@code WarriorDragonRenderer}），服务端只管判定和声音。
 *
 * <p>这里保留的两处几何反馈是<b>命中反馈</b>，不是施放特效：
 * 打到人溅出的火花（{@link #hitSpark}）、格挡时身前的一圈防反光（{@link #guard}）。
 * 这两处都很小、持续时间不到 0.5 秒，而且只在"真的挡住了 / 真的打中了"时才出现。
 */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class WarriorCombatEffects {
    private static final Map<UUID,State> STATES=new HashMap<>();
    private static final class State{
        long block=Long.MIN_VALUE,impact=Long.MIN_VALUE,lastGuard=Long.MIN_VALUE,flash=Long.MIN_VALUE;
        long lastHitSound=Long.MIN_VALUE;
        long sparkTick=Long.MIN_VALUE;
        final Set<Integer> sparkTargets=new HashSet<>();
        boolean guarding;
        UUID guardTarget;
    }
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

    /**
     * 终结技的声音（剑气/刀波的几何在客户端，服务端只出声）。
     */
    static void finisherSound(ServerPlayer p,WarriorSkill skill){
        p.serverLevel().playSound(null,p.blockPosition(),
            skill.fullCircle()?SoundEvents.PLAYER_ATTACK_SWEEP:SoundEvents.PLAYER_ATTACK_CRIT,
            SoundSource.PLAYERS,1.0f,skill.fullCircle()?.8f:1.2f);
    }

    /**
     * 龙之剑收尾的那一声龙吼。
     *
     * <p>龙本体是客户端的 {@code WarriorDragonRenderer}（一条真实几何体的刃形长龙，
     * 沿玩家朝向冲出 7.5 格再淡出），服务端不广播任何粒子 —— 以前那条"粒子的龙"
     * 2026-11-03 整段删掉了，没有开关、没有残留代码。
     */
    static void dragonRoar(ServerPlayer p){
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.PLAYERS,.45f,1.35f);
    }

    /**
     * 命中反馈：在碰撞箱表面绘制短切痕和立体碎片，护甲命中增加金属火星。
     *
     * <p>只在真的造成伤害时调用（`hurt(...)` 返回 true 之后），所以它不是"施放特效"，
     * 而是"打实了"的反馈。
     */
    static void hitSpark(ServerPlayer p, net.minecraft.world.entity.LivingEntity target, boolean heavy) {
        var state=STATES.computeIfAbsent(p.getUUID(),k->new State());long now=p.level().getGameTime();
        if(state.sparkTick!=now){state.sparkTick=now;state.sparkTargets.clear();}
        if(state.sparkTargets.add(target.getId()))WarriorImpactPacket.hit(p,target,heavy,p.getEyePosition());

    }

    /** 格挡时身前那一圈防反光；完美格挡再加一下闪光。小、短、只在挡住时出现。 */
    static void guard(ServerPlayer p,boolean perfect,boolean raising){
        var center=p.getEyePosition().add(p.getLookAngle().scale(.85)).add(0,-.35,0);
        WarriorImpactPacket.send(p,center,1);
        if(perfect)p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,.45f,1.55f);
    }

    /** 风刃：走客户端渲染的刀波（没有粒子）。 */
    static void windCut(ServerPlayer p,Vec3 start,Vec3 end){
        var delta=end.subtract(start); double range=delta.length(); if(range<.01)return;
        var axis=delta.normalize();var side=new Vec3(-axis.z,0,axis.x);
        if(side.lengthSqr()<1e-6)side=new Vec3(1,0,0);side=side.normalize();
        var origin=start.subtract(side.cross(axis).normalize().scale(.65));
        com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.NEAR.with(() ->
                        new net.minecraftforge.network.PacketDistributor.TargetPoint(start.x,start.y,start.z,96,p.level().dimension())),
                new WarriorWavePacket(p.level().dimension().location(),origin,axis,false,
                        Math.max(.01,range/4),range,.8,0xCBFFF1,0x319C91,p.level().getGameTime()));
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        if(!eligible(p)){STATES.remove(p.getUUID());return;}
        var state=STATES.computeIfAbsent(p.getUUID(),k->new State());
        boolean guarding=p.isBlocking();if(guarding&&!state.guarding)guard(p,false,true);state.guarding=guarding;
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
        if(event.getAmount()<=0||!(event.getSource().getEntity() instanceof ServerPlayer p)||(!eligible(p)&&!(event.getEntity() instanceof TestDummyEntity))||WarriorWeapons.family(p.getMainHandItem())==null
            ||(event.getSource().getDirectEntity()!=p && !(event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow)))return;
        var state=STATES.computeIfAbsent(p.getUUID(),k->new State());long now=p.server.overworld().getGameTime();state.impact=now;
        var center=event.getEntity().getBoundingBox().getCenter();var s=style(p);boolean counter=state.lastGuard!=Long.MIN_VALUE&&now-state.lastGuard<=40&&event.getEntity().getUUID().equals(state.guardTarget);
        var sound=WarriorHitSound.profile(WarriorWeapons.family(p.getMainHandItem()),event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow);
        if(WarriorHitSound.ready(now,state.lastHitSound,sound.interval())){
            state.lastHitSound=now;p.serverLevel().playSound(null,event.getEntity().blockPosition(),sound.sound(),SoundSource.PLAYERS,sound.volume(),sound.pitch());
        }
        hitSpark(p,event.getEntity(),counter || event.getAmount()>=8 || WarriorWeapons.family(p.getMainHandItem())==WarriorWeaponFamily.GREATSWORD || WarriorWeapons.family(p.getMainHandItem())==WarriorWeaponFamily.HAMMER);
        if(counter){state.lastGuard=Long.MIN_VALUE;p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.PLAYER_ATTACK_CRIT,SoundSource.PLAYERS,.55f,1.2f);}
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
