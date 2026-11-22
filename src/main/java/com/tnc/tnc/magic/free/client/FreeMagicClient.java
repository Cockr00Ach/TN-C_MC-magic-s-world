package com.tnc.tnc.magic.free.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.magic.free.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import static com.tnc.tnc.magic.free.FreeMagicNetwork.*;

@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class FreeMagicClient {
    private static final KeyMapping KEY=new KeyMapping("key.tnc.free_magic_chant",KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F9,"key.categories.tnc");
    private static final KeyMapping STOP_KEY=new KeyMapping("key.tnc.free_magic_stop",KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_UNKNOWN,"key.categories.tnc");
    @Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event){event.register(KEY);event.register(STOP_KEY);}
    }
    private record Chant(long token,int shape,int count,long started,int duration,boolean confirmed,long seen,List<Vec3> targets,int modifiers,int chantPower){}
    private record Shot(Event packet,long received,Vec3 previous){}
    private record Impact(Event packet,long received){}
    private static final Map<UUID,Chant> CHANTS=new HashMap<>();
    private static final Map<UUID,ChantCaption> CAPTIONS=new HashMap<>();
    private static final Map<Long,Shot> SHOTS=new LinkedHashMap<>();
    private static final Map<Long,Impact> IMPACTS=new LinkedHashMap<>();
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new BufferBuilder(524288));
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static boolean previousDown,recording,waiting,microphoneReady;
    private static long session,nextImpact,renderedFrames,receivedShots;
    private static String status="";
    private static int statusTicks;

    static Map<String,Object> auditSnapshot(){return Map.of("chants",CHANTS.size(),"captions",CAPTIONS.size(),"shots",SHOTS.size(),
            "received_shots",receivedShots,"rendered_frames",renderedFrames,"status",status,
            "confirmed",CHANTS.values().stream().filter(Chant::confirmed).count());}
    static double auditProgress(){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return -1;
        var c=CHANTS.get(mc.player.getUUID());return c==null||!c.confirmed()?-1:(double)(mc.level.getGameTime()-c.started())/c.duration();
    }
    private static void caption(UUID owner,long token,String text,long now,boolean terminal){
        if(!text.isBlank())CAPTIONS.computeIfAbsent(owner,id->new ChantCaption()).update(token,text,now,terminal);
    }
    public static void accept(Event event){
        var mc=Minecraft.getInstance();
        if(mc.level==null||!mc.level.dimension().location().equals(event.dimension()))return;
        long now=mc.level.getGameTime();
        switch(event.kind()){
            case CHANT -> {
                caption(event.owner(),event.token(),event.text(),now,event.duration()>0);
                var current=CHANTS.get(event.owner());
                if(current==null||current.token()!=event.token()||!current.confirmed())
                    CHANTS.put(event.owner(),new Chant(event.token(),event.shape(),Math.max(1,Math.min(8,event.count())),
                            now,0,false,now,List.of(),event.modifiers(),event.chantPower()));
            }
            case CHARGE -> {
                caption(event.owner(),event.token(),event.text(),now,true);
                CHANTS.put(event.owner(),new Chant(event.token(),event.shape(),Math.max(1,Math.min(8,event.count())),
                        event.started(),Math.max(1,event.duration()),true,now,event.targets(),event.modifiers(),event.chantPower()));
            }
            case CLEAR,CAST_END -> {
                var chant=CHANTS.get(event.owner());
                if(chant!=null&&chant.token()==event.token())CHANTS.remove(event.owner());
                var text=CAPTIONS.get(event.owner());
                if(text!=null&&text.token()==event.token()){
                    if(event.kind()==CLEAR)text.cancel(now);else text.finish(now);
                }
            }
            case SHOT -> {
                if(event.shape()>=FreeMagicIntent.Shape.values().length)return;
                var old=SHOTS.get(event.token());
                if(old==null)receivedShots++;
                SHOTS.put(event.token(),new Shot(event,now,old==null?null:position(old,now)));
                while(SHOTS.size()>128)SHOTS.remove(SHOTS.keySet().iterator().next());
            }
            case HIT -> SHOTS.remove(event.token());
            case IMPACT -> {
                if(event.shape()>=FreeMagicIntent.Shape.values().length)return;
                IMPACTS.put(++nextImpact,new Impact(event,now));
                while(IMPACTS.size()>96)IMPACTS.remove(IMPACTS.keySet().iterator().next());
            }
            case EQUIPPED_CAST -> {
                if(mc.player!=null&&event.owner().equals(mc.player.getUUID())&&event.token()==session)
                    EquippedSpellVoiceClient.queue(event.text());
            }
            case ERROR -> {
                FreeMagicVoice.cancel();recording=waiting=microphoneReady=false;
                status=event.text();statusTicks=100;
            }
            default -> {}
        }
    }
    private static void send(int action,long token,String text){CHANNEL.sendToServer(new Input(action,token,text.length()>128?text.substring(0,128):text));}
    private static void cancel(){
        var mc=Minecraft.getInstance();
        if(recording||waiting){FreeMagicVoice.cancel();send(CANCEL,session,"");}
        if(mc.player!=null){
            var chant=CHANTS.remove(mc.player.getUUID());
            if(chant!=null&&chant.confirmed())send(CANCEL,chant.token(),"");
            var text=CAPTIONS.get(mc.player.getUUID());
            if(text!=null&&mc.level!=null)text.cancel(mc.level.getGameTime());
        }
        recording=waiting=microphoneReady=false;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.level!=world){
            FreeMagicVoice.close();EquippedSpellVoiceClient.clear();world=mc.level;recording=waiting=previousDown=false;
            CHANTS.clear();CAPTIONS.clear();SHOTS.clear();IMPACTS.clear();ChantTextVisuals.clear();
        }
        if(statusTicks>0)statusTicks--;
        if(mc.player==null||mc.level==null)return;
        boolean usable=mc.screen==null&&!mc.isPaused()&&mc.player.isAlive()&&mc.isWindowActive();
        while(STOP_KEY.consumeClick())send(STOP,session,"");
        if(!usable&&!Boolean.getBoolean("tnc.freeMagicAudit")&&ElementalVisuals.maintaining(mc.player.getUUID()))send(STOP,session,"");
        // Offscreen audit supplies transcripts without giving its window input focus.
        boolean auditUsable=Boolean.getBoolean("tnc.freeMagicAudit")&&mc.screen==null&&!mc.isPaused()&&mc.player.isAlive();
        String skillStatus=EquippedSpellVoiceClient.tick(usable||auditUsable);
        if(!skillStatus.isEmpty()){status=skillStatus;statusTicks=100;}
        boolean down=KEY.isDown()&&usable;
        // Native audit fixture packets have no held input. Real pending input always cancels on focus loss.
        if(!usable&&(recording||waiting||(!Boolean.getBoolean("tnc.freeMagicAudit")&&confirmed(mc.player.getUUID()))))cancel();
        if(down&&!previousDown&&!waiting){
            if(confirmed(mc.player.getUUID())){status="水形正在凝聚…";statusTicks=40;}
            else if(FreeMagicVoice.start()){
                session=System.nanoTime();recording=true;microphoneReady=false;status="正在打开麦克风…";statusTicks=200;
                send(BEGIN,session,"");
            }
        }
        if(!down&&previousDown&&recording){FreeMagicVoice.release();recording=false;waiting=true;status="正在辨认吟唱…";statusTicks=100;}
        previousDown=down;
        for(var update:FreeMagicVoice.drainUpdates())consume(update);
        long now=mc.level.getGameTime();
        CHANTS.values().removeIf(c->now-c.seen()>(c.confirmed()?80:400));
        CAPTIONS.values().removeIf(c->!c.visible(now));
        SHOTS.values().removeIf(s->now-s.packet().started()>s.packet().duration()+12);
        IMPACTS.values().removeIf(i->now-i.received()>Math.max(12,i.packet().duration()));
    }
    private static boolean confirmed(UUID owner){var c=CHANTS.get(owner);return c!=null&&c.confirmed();}
    private static void consume(FreeMagicVoice.Update update){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||(!recording&&!waiting))return;
        if(update.kind()==FreeMagicVoice.Kind.READY){microphoneReady=true;return;}
        if(update.kind()==FreeMagicVoice.Kind.ERROR){status=update.text();statusTicks=160;cancel();return;}
        var context=ElementalRuntime.context(mc.player);
        String spoken=EquippedSpellVoice.containsName(update.text())?update.text():ElementalSpeech.resolve(update.text(),update.shapeHint(),context);
        boolean elemental=ElementalIntent.parse(spoken,context).isPresent();
        String raw=EquippedSpellVoice.containsName(update.text())||elemental?FreeMagicIntent.clean(spoken):FreeMagicIntent.resolve(update.text(),update.shapeHint());
        String display=EquippedSpellVoice.containsName(raw)?raw:elemental?ElementalSpeech.normalize(raw,context):FreeMagicIntent.normalize(raw);
        long now=mc.level.getGameTime();
        if(update.kind()==FreeMagicVoice.Kind.FINAL){
            if(!waiting){cancel();status="吟唱已中止，请松开按键后重试。";statusTicks=100;return;}
            // A short utterance can have only FINAL. It still gets a full caption lifetime.
            caption(mc.player.getUUID(),session,display,now,true);
            send(FINAL,session,raw);recording=waiting=false;
            status=EquippedSpellVoice.containsName(raw)?"正在确认已装配技能与自由魔法…":elemental?ElementalIntent.parse(raw,context).map(i->i.description()+" · 每次 "+i.cost()+" 魔力").orElse(""):FreeMagicIntent.parse(raw).map(i->"塑形 · "+i.description()+" · 每发 "+i.costPerProjectile()+" 魔力")
                    .orElse(raw.isBlank()?"未听清吟唱":"没有辨认出当前元素的能力关键词");statusTicks=100;
        }else{
            caption(mc.player.getUUID(),session,display,now,false);
            var intent=FreeMagicIntent.parse(raw);
            CHANTS.put(mc.player.getUUID(),new Chant(session,intent.map(i->i.shape().ordinal()+1).orElse(0),1,now,0,false,now,List.of(),
                    intent.map(FreeMagicIntent.Intent::modifiers).orElse(0),intent.map(FreeMagicIntent.Intent::chantPower).orElse(0)));
            send(PARTIAL,session,raw);
        }
    }
    /** Exercise the same final-only recognition branch without recording a microphone in isolated visual tests. */
    static void auditFinal(long token,String raw){session=token;recording=false;waiting=true;consume(new FreeMagicVoice.Update(FreeMagicVoice.Kind.FINAL,raw));}
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.options.hideGui||(!recording&&!waiting&&statusTicks<=0))return;
        String message=recording?(microphoneReady?"吟唱 · "+KEY.getTranslatedKeyMessage().getString()+" 松开塑形":"正在打开麦克风…"):status;
        event.getGuiGraphics().drawCenteredString(mc.font,message,event.getGuiGraphics().guiWidth()/2,event.getGuiGraphics().guiHeight()-66,0xA9EDFF);
    }
    private static Vec3 position(Shot shot,double now){
        var p=shot.packet();var shape=FreeMagicIntent.Shape.values()[p.shape()];
        double elapsed=Math.max(0,now-shot.received());
        Vec3 target=shape.stationary()||shape.behavior==FreeMagicIntent.Behavior.SWEEP?p.at():p.at().add(p.velocity().scale(elapsed));
        return shot.previous()!=null&&elapsed<3?target.add(shot.previous().subtract(p.at()).scale(1-elapsed/3)):target;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        var camera=event.getCamera().getPosition();var pose=new PoseStack();double now=mc.level.getGameTime()+event.getPartialTick();
        renderedFrames++;
        for(var entry:CAPTIONS.entrySet()){
            var player=mc.level.getPlayerByUUID(entry.getKey());
            if(player!=null&&player.distanceToSqr(mc.player)<=4096)
                ChantTextVisuals.draw(pose,BUFFER,camera,player,entry.getValue(),now,event.getPartialTick());
        }
        for(var entry:CHANTS.entrySet()){
            var player=mc.level.getPlayerByUUID(entry.getKey());
            if(player==null||player.distanceToSqr(mc.player)>4096)continue;
            var c=entry.getValue();
            var shape=c.shape()>0&&c.shape()<=FreeMagicIntent.Shape.values().length?FreeMagicIntent.Shape.values()[c.shape()-1]:null;
            double progress=c.confirmed()?Math.max(0,Math.min(1,(now-c.started())/c.duration())):0;
            var intent=shape==null?null:new FreeMagicIntent.Intent(shape,FreeMagicIntent.Mode.SINGLE,c.modifiers(),c.chantPower());
            if(c.confirmed())WaterCastVisuals.formation(pose,BUFFER,camera,player.getEyePosition(event.getPartialTick()),player.getLookAngle(),intent,c.count(),progress,true,now,c.targets());
            else WaterCastVisuals.gather(pose,BUFFER,camera,player.getEyePosition(event.getPartialTick()),player.getLookAngle(),now);
        }
        for(var shot:SHOTS.values()){
            var p=shot.packet();
            WaterCastVisuals.attack(pose,BUFFER,camera,visualIntent(p),position(shot,now),p.velocity(),now-p.started(),p.duration(),now);
        }
        for(var impact:IMPACTS.values()){
            var p=impact.packet();var anchor=p.anchor()<0?null:mc.level.getEntity(p.anchor());
            Vec3 at=anchor==null?p.at():anchor.getPosition(event.getPartialTick()).add(0,anchor.getBbHeight()*.5,0);
            if(p.anchor()==-2)WaterCastVisuals.residue(pose,BUFFER,camera,visualIntent(p),at,now-impact.received(),p.duration(),now);
            else WaterCastVisuals.impact(pose,BUFFER,camera,visualIntent(p),at,now-impact.received(),p.duration(),now);
        }
        BUFFER.endBatch();
    }
    private FreeMagicClient(){}
    private static FreeMagicIntent.Intent visualIntent(Event p){return new FreeMagicIntent.Intent(FreeMagicIntent.Shape.values()[p.shape()],FreeMagicIntent.Mode.SINGLE,p.modifiers(),p.chantPower());}
}
