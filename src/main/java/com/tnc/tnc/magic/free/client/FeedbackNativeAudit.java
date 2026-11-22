package com.tnc.tnc.magic.free.client;

import com.tnc.tnc.magic.*;
import com.tnc.tnc.magic.free.*;
import com.tnc.tnc.adventure.ElementWands;
import com.tnc.tnc.boss.eclipse.*;
import net.minecraft.client.*;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.*;
import java.util.*;

/** Continuation of the isolated audit: ordinary engine voice casting and enlarged jaw framing. */
final class FeedbackNativeAudit {
    private static int stage,ticks,captures;
    private static boolean requested,saving,seenCast;
    private static volatile boolean ready,spent;
    private static EclipseLeviathanEntity boss;
    static boolean tick(Minecraft mc,Path output)throws Exception{
        if(!requested){
            net.spell_engine.client.SpellEngineClient.config.holdToCastCharged=true;
            net.spell_engine.client.SpellEngineClient.config.holdToCastChannelled=true;
            requested=true;ready=false;
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ElementWands.stack(ElementWands.find("fire_wand_3")));
                var data=MagicStone.getOrNull(p);data.setAffinity(Element.FIRE,3);data.setMaxMana(10000);data.setMana(10000);
                var id=ResourceLocation.fromNamespaceAndPath("tnc","fireball");data.learn(id);data.setSlot(12,id);
                FreeMagicServer.input(p,new FreeMagicNetwork.Input(FreeMagicNetwork.BEGIN,900,""));ready=true;
            });return false;
        }
        if(!ready||saving)return false;
        if(stage==0){
            FreeMagicClient.auditFinal(900,"释放出我的火球术！");stage=1;ticks=0;return false;
        }
        ticks++;
        if(stage==1){
            var caster=(net.spell_engine.internals.casting.SpellCasterClient)mc.player;
            var process=caster.getSpellCastProcess();
            if(process!=null&&process.id().getPath().equals("fireball")){
                seenCast=true;
                if(captures==0&&ticks>12){capture(mc,output,"skill-fireball-windup.png");captures++;}
            }
            // Remember a debit even after the normal regeneration delay expires.
            if(ticks%10==0)mc.getSingleplayerServer().execute(()->spent|=MagicStone.getOrNull(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0)).getMana()<10000);
            if(spent&&ticks>100){
                if(!seenCast)throw new IllegalStateException("Voice skill skipped the engine windup");
                capture(mc,output,"skill-fireball-released.png");stage=2;ticks=0;return false;
            }
            if(ticks>240)throw new IllegalStateException("Equipped voice fireball failed to spend mana; engine cast observed="+seenCast+" client="+FreeMagicClient.auditSnapshot()+" focused="+mc.isWindowActive()+" screen="+mc.screen+" paused="+mc.isPaused());
            return false;
        }
        if(boss==null){
            boss=EclipseRegistration.ECLIPSE_LEVIATHAN.create(mc.level);
            mc.level.putNonPlayerEntity(1800000000,boss);
        }
        double distance=stage==2?35:stage==3?14:7;
        var eye=mc.player.getEyePosition();var at=eye.add(mc.player.getLookAngle().scale(distance));
        boss.setPos(at);boss.setYRot(mc.player.getYRot()+180);boss.yRotO=boss.getYRot();
        boss.xo=at.x;boss.yo=at.y;boss.zo=at.z;
        set(boss,"PHASE",EclipseLeviathanEntity.Phase.WINDUP.ordinal());set(boss,"PHASE_START",mc.level.getGameTime()-28);
        if(ticks==18)capture(mc,output,"eclipse-jaw-distance-"+(int)distance+".png");
        if(ticks>25){stage++;ticks=0;}
        if(stage>4){
            boss.discard();
            Files.writeString(output.resolve("feedback-extra-report.json"),"{\"voice_engine_windup\":true,\"voice_engine_mana_spent\":true,\"boss_scale\":3.4,\"boss_render_fixture\":\"client-only windup at 35,14,7 blocks; pursuit tested separately by GameTest\"}");
            return true;
        }
        return false;
    }
    @SuppressWarnings("unchecked") private static <T> void set(EclipseLeviathanEntity boss,String name,T value)throws Exception{
        var field=EclipseLeviathanEntity.class.getDeclaredField(name);field.setAccessible(true);
        boss.getEntityData().set((EntityDataAccessor<T>)field.get(null),value);
    }
    private static void capture(Minecraft mc,Path output,String name){
        saving=true;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{
            try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/"+name),output.resolve(name),StandardCopyOption.REPLACE_EXISTING);}
            catch(Exception error){throw new RuntimeException(error);}finally{saving=false;}
        });
    }
    private FeedbackNativeAudit(){}
}
