package com.tnc.tnc.magic.free.client;

import com.tnc.tnc.adventure.ElementWands;
import com.tnc.tnc.magic.*;
import com.tnc.tnc.magic.free.*;
import net.minecraft.client.*;
import net.minecraft.world.level.GameType;
import java.nio.file.*;
import java.util.*;

/** Isolated native rendering fixtures; transcript fixtures do not claim to test a microphone. */
public final class FreeMagicNativeAudit {
    private static int phase,ticks,capture,warmup,retries;
    private static volatile boolean ready;
    private static boolean prepared,saving,submitted;
    private static long started,shotStart;
    private static final String[] WORDS={"水球","巨大水球","巨大连续齐发水球","急速连续齐发水枪","强化水炮","震荡海浪","持久水龙卷","芙宁娜，赐福祈祷回应庇佑誓言，强化水龙"};
    private static final List<Map<String,Object>> views=new ArrayList<>();
    private static Path output(){return Path.of(System.getProperty("tnc.dungeonAuditOutput"));}
    public static void tick(Minecraft mc)throws Exception{
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().equals(Path.of("D:/TNC-Audits/dungeons-20261019").toAbsolutePath().normalize()))
            throw new IllegalStateException("Isolated free magic audit only");
        // Let resource reload, shaders and the first world frames settle before a short charge.
        if(warmup++<240){if(mc.screen!=null)mc.setScreen(null);return;}
        if(Boolean.getBoolean("tnc.sixElementAudit")){SixElementNativeAudit.tick(mc,output());return;}
            if(Boolean.getBoolean("tnc.fireMagicAudit")){FireNativeAudit.tick(mc,output());return;}
        if(started==0)started=System.nanoTime();
        if(System.nanoTime()-started>480_000_000_000L){Files.writeString(output().resolve("free-magic-failed.txt"),"Render audit timed out at "+phase);mc.stop();return;}
        if(saving)return;
        if(phase>=WORDS.length){
            if(!FeedbackNativeAudit.tick(mc,output()))return;
            Files.writeString(output().resolve("free-magic-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                    .toJson(Map.of("transcripts","final-only fixtures through actual client FINAL handler and server validation",
                            "microphone_tested",false,"views",views)));
            mc.stop();return;
        }
        if(mc.screen!=null)mc.setScreen(null);
        mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);
        var intent=FreeMagicIntent.parse(WORDS[phase]).orElseThrow();var shape=intent.shape();
        if(!prepared){
            prepared=true;ready=false;submitted=false;ticks=capture=0;shotStart=((Number)FreeMagicClient.auditSnapshot().get("received_shots")).longValue();
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayers().get(0);
                var sea=com.tnc.tnc.world.SkyIslandWorlds.island(server);
                com.tnc.tnc.journey.JourneyData.get(server).player(p.getUUID()).putBoolean("Named",true);
                sea.setWeatherParameters(12000,0,false,false);server.overworld().setDayTime(phase==5?6000:18000);
                p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();
                p.teleportTo(sea,286,125,0,-90,0);
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ElementWands.stack(ElementWands.find("water_wand_3")));
                var data=MagicStone.getOrNull(p);data.setAffinity(Element.WATER,3);for(int slot=0;slot<MagicStoneData.LOADOUT_SLOTS;slot++)data.clearSlot(slot);data.setMaxMana(10000);data.setMana(10000);
                FreeMagicServer.input(p,new FreeMagicNetwork.Input(FreeMagicNetwork.BEGIN,500+phase,""));
                ready=true;
            });return;
        }
        if(!ready)return;
        if(!submitted){
            FreeMagicClient.auditFinal(500+phase,WORDS[phase]);
            submitted=true;return;
        }
        ticks++;
        double progress=FreeMagicClient.auditProgress();
        long released=((Number)FreeMagicClient.auditSnapshot().get("received_shots")).longValue()-shotStart;
        boolean captureNow=capture==0?progress>=.3:capture==1?progress>=.78:released>=intent.formationCount(3)&&ticks>=intent.chargeTicks()+5;
        if(capture<3&&captureNow){
            String name=String.format(Locale.ROOT,"%02d-%s-%s.png",phase,shape.name().toLowerCase(Locale.ROOT),new String[]{"forming","ready","flight"}[capture]);
            views.add(Map.of("image",name,"client",FreeMagicClient.auditSnapshot(),"tick",ticks,"spell",intent.description(),"expected_total",intent.count(3),"received_in_cast",released));
            saving=true;
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{
                try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/"+name),output().resolve(name),StandardCopyOption.REPLACE_EXISTING);}
                catch(Exception ex){throw new RuntimeException(ex);}finally{saving=false;}
            });capture++;
        }
        if(ticks>intent.chargeTicks()+intent.waveInterval()*(intent.waves(3)-1)+intent.lifetime()+20){
            if(released!=intent.count(3))throw new IllegalStateException("Composition attack count failed: "+WORDS[phase]+" attacks="+released);
            // PNG encoding can miss a short ready frame on a cold shader cache. Retry the
            // complete cast, keeping only a complete set of actual rendered frames.
            if(capture!=3){
                if(retries++>=2)throw new IllegalStateException("Composition screenshots failed: "+WORDS[phase]+" captures="+capture);
                String prefix=String.format(Locale.ROOT,"%02d-",phase);
                views.removeIf(view->view.get("image").toString().startsWith(prefix));
                prepared=false;return;
            }
            phase++;prepared=false;retries=0;
        }
    }
    private FreeMagicNativeAudit(){}
}
