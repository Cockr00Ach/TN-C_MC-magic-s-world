package com.tnc.tnc.magic.free.client;

import com.tnc.tnc.adventure.ElementWands;
import com.tnc.tnc.magic.*;
import com.tnc.tnc.magic.free.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;
import java.util.*;

/** Transcript-driven, isolated full-pack fire rendering check; never claims microphone coverage. */
final class FireNativeAudit {
    private static final String[] WORDS={"火球","巨大火球","强化火球","火蛇","火刃","岩浆喷射","紫火","持续喷火","火焰覆盖","火墙","火环","火雨"};
    private static int phase,ticks,captures;private static boolean prepared,submitted,saving;private static volatile boolean ready;
    private static Pig target;private static long started;private static final List<Map<String,Object>> views=new ArrayList<>();
    static void tick(Minecraft mc,Path output)throws Exception{
        if(System.currentTimeMillis()>=Long.getLong("tnc.auditStopAtMillis",Long.MAX_VALUE)){
            Files.writeString(output.resolve("fire-paused.txt"),"Stopped at the user's deadline; completed scenes="+phase);mc.stop();return;
        }
        if(started==0)started=System.nanoTime();
        if(System.nanoTime()-started>480_000_000_000L){Files.writeString(output.resolve("fire-failed.txt"),"Timed out at phase "+phase);mc.stop();return;}
        if(saving)return;if(mc.screen!=null)mc.setScreen(null);mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);
        if(phase>=WORDS.length){Files.writeString(output.resolve("fire-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("microphone_tested",false,"scenes",views)));mc.stop();return;}
        if(!prepared){prepared=true;ready=false;submitted=false;ticks=captures=0;
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayers().get(0);var level=com.tnc.tnc.world.SkyIslandWorlds.island(server);
                if(phase==0)for(int x=280;x<=320;x++)for(int z=-18;z<=18;z++)level.setBlock(new BlockPos(x,120,z),Blocks.DEEPSLATE_TILES.defaultBlockState(),2);
                if(target!=null)target.discard();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(level,286,123,0,-90,18);
                p.setItemInHand(InteractionHand.MAIN_HAND,ElementWands.stack(ElementWands.find("fire_wand_3")));
                var data=MagicStone.getOrNull(p);data.setAffinity(Element.FIRE,3);data.setMaxMana(10000);data.setMana(10000);for(int i=0;i<MagicStoneData.LOADOUT_SLOTS;i++)data.clearSlot(i);
                target=EntityType.PIG.create(level);target.setNoAi(true);target.setNoGravity(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);
                target.setPos(p.getEyePosition().add(p.getLookAngle().scale(10)).add(0,-.45,0));level.addFreshEntity(target);
                ready=true;
            });return;
        }
        if(!ready)return;ticks++;
        if(!submitted){if(ticks<60)return;mc.getSingleplayerServer().execute(()->FreeMagicServer.input(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0),new FreeMagicNetwork.Input(FreeMagicNetwork.BEGIN,2000+phase,"")));submitted=true;ticks=0;return;}
        if(ticks==3){FreeMagicClient.auditFinal(2000+phase,WORDS[phase]);return;}
        var state=ElementalVisuals.auditSnapshot();long forms=((Number)state.get("forms")).longValue(),active=((Number)state.get("active")).longValue();
        if(captures==0&&forms>0&&ticks>=10){capture(mc,output,"forming",state);captures++;}
        else if(captures==1&&active>0){capture(mc,output,"active",state);captures++;}
        int duration=ElementalIntent.parse(WORDS[phase],Element.FIRE).orElseThrow().charge()+100;
        if(ticks>duration){
            if(captures!=2)throw new IllegalStateException("Missing fire render phase: "+WORDS[phase]+" captures="+captures+" client="+FreeMagicClient.auditSnapshot());
            mc.getSingleplayerServer().execute(()->FreeMagicServer.input(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0),new FreeMagicNetwork.Input(FreeMagicNetwork.STOP,0,"")));
            phase++;prepared=false;
        }
    }
    private static void capture(Minecraft mc,Path output,String step,Map<String,Object> state){
        String name=String.format(Locale.ROOT,"%02d-fire-%s.png",phase,step);views.add(Map.of("image",name,"spell",WORDS[phase],"client",state));saving=true;
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/"+name),output.resolve(name),StandardCopyOption.REPLACE_EXISTING);}catch(Exception error){throw new RuntimeException(error);}finally{saving=false;}});
    }
    private FireNativeAudit(){}
}
