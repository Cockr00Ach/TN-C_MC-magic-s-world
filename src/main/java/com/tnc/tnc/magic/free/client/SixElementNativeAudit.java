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
final class SixElementNativeAudit {
    private record Scene(Element element,String word){}
    private static final Scene[] SCENES={
        new Scene(Element.FIRE,"火球"),new Scene(Element.FIRE,"巨大火球"),new Scene(Element.FIRE,"强化火球"),new Scene(Element.FIRE,"火蛇"),new Scene(Element.FIRE,"岩浆喷射"),new Scene(Element.FIRE,"紫火"),new Scene(Element.FIRE,"持续喷火"),new Scene(Element.FIRE,"火墙"),new Scene(Element.FIRE,"火雨"),
        new Scene(Element.WIND,"风球"),new Scene(Element.WIND,"风刃"),new Scene(Element.WIND,"聚怪"),new Scene(Element.WIND,"升龙"),new Scene(Element.WIND,"龙卷"),new Scene(Element.WIND,"起飞"),
        new Scene(Element.EARTH,"岩球"),new Scene(Element.EARTH,"岩突"),new Scene(Element.EARTH,"巨大岩突"),new Scene(Element.EARTH,"岩合"),new Scene(Element.EARTH,"地刺"),new Scene(Element.EARTH,"岩墙"),new Scene(Element.EARTH,"落岩"),
        new Scene(Element.LIGHTNING,"雷球"),new Scene(Element.LIGHTNING,"九重雷击"),new Scene(Element.LIGHTNING,"连锁雷电"),new Scene(Element.LIGHTNING,"雷网"),new Scene(Element.LIGHTNING,"雷速"),
        new Scene(Element.LIGHT,"光球"),new Scene(Element.LIGHT,"激光"),new Scene(Element.LIGHT,"棱镜折射"),new Scene(Element.LIGHT,"治疗"),new Scene(Element.LIGHT,"极昼"),
        new Scene(Element.DARK,"暗球"),new Scene(Element.DARK,"恶灵"),new Scene(Element.DARK,"黑暗仪式"),new Scene(Element.DARK,"影刺"),new Scene(Element.DARK,"禁疗"),new Scene(Element.DARK,"痛苦链接"),new Scene(Element.DARK,"迟发咒印"),new Scene(Element.DARK,"蚀甲"),new Scene(Element.DARK,"噤声")};
    private static int phase=Integer.getInteger("tnc.sixAuditStart",0),ticks,captures;private static boolean prepared,submitted,saving;private static volatile boolean ready;
    private static Pig target;private static long started;private static final List<Map<String,Object>> views=new ArrayList<>();
    static void tick(Minecraft mc,Path output)throws Exception{
        if(System.currentTimeMillis()>=Long.getLong("tnc.auditStopAtMillis",Long.MAX_VALUE)){
            Files.writeString(output.resolve("elements-paused.txt"),"Stopped at the user's deadline; completed scenes="+phase);mc.stop();return;
        }
        if(started==0)started=System.nanoTime();
        if(System.nanoTime()-started>1_800_000_000_000L){Files.writeString(output.resolve("elements-failed.txt"),"Timed out at phase "+phase);mc.stop();return;}
        if(saving)return;if(mc.screen!=null)mc.setScreen(null);mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);
        if(phase>=SCENES.length){Files.writeString(output.resolve("elements-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("microphone_tested",false,"scenes",views)));mc.stop();return;}
        if(!prepared){prepared=true;ready=false;submitted=false;ticks=captures=0;
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayers().get(0);var level=com.tnc.tnc.world.SkyIslandWorlds.island(server);level.setWeatherParameters(6000,0,false,false);
                if(phase==0)for(int x=280;x<=320;x++)for(int z=-18;z<=18;z++)level.setBlock(new BlockPos(x,120,z),Blocks.DEEPSLATE_TILES.defaultBlockState(),2);
                FreeMagicServer.input(p,new FreeMagicNetwork.Input(FreeMagicNetwork.STOP,0,""));
                com.tnc.tnc.magic.free.ElementalRuntime.auditClear(p);if(target!=null)target.discard();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(level,286,(SCENES[phase].word().contains("岩突")||SCENES[phase].word().equals("起飞"))?121:123,0,-90,18);if((SCENES[phase].word().contains("岩突")||SCENES[phase].word().equals("起飞"))){p.getAbilities().flying=false;p.onUpdateAbilities();p.setOnGround(true);}
                p.setItemInHand(InteractionHand.MAIN_HAND,ElementWands.stack(ElementWands.find(SCENES[phase].element().id()+"_wand_3")));
                var data=MagicStone.getOrNull(p);data.setAffinity(SCENES[phase].element(),3);data.setMaxMana(10000);data.setMana(10000);for(int i=0;i<MagicStoneData.LOADOUT_SLOTS;i++)data.clearSlot(i);
                target=EntityType.PIG.create(level);target.setNoAi(true);target.setNoGravity(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);
                target.setPos(p.getEyePosition().add(p.getLookAngle().scale(ElementalIntent.parse(SCENES[phase].word(),SCENES[phase].element()).orElseThrow().ability().target==ElementalIntent.Target.PROJECTILE?20:10)).add(0,-.45,0));level.addFreshEntity(target);
                ready=true;
            });return;
        }
        if(!ready)return;ticks++;
        if(!submitted){if(ticks<60)return;mc.getSingleplayerServer().execute(()->FreeMagicServer.input(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0),new FreeMagicNetwork.Input(FreeMagicNetwork.BEGIN,2000+phase,"")));submitted=true;ticks=0;return;}
        if(ticks==3){FreeMagicClient.auditFinal(2000+phase,SCENES[phase].word());return;}
        var state=ElementalVisuals.auditSnapshot();long forms=((Number)state.get("forms")).longValue(),active=((Number)state.get("active")).longValue();
        if(captures==0&&forms>0&&ticks>=10){capture(mc,output,"forming",state);captures++;}
        else if(captures==1&&active>0&&ticks>=ElementalIntent.parse(SCENES[phase].word(),SCENES[phase].element()).orElseThrow().charge()+(ElementalIntent.parse(SCENES[phase].word(),SCENES[phase].element()).orElseThrow().ability().target==ElementalIntent.Target.SELF||ElementalIntent.parse(SCENES[phase].word(),SCENES[phase].element()).orElseThrow().ability().target==ElementalIntent.Target.FRIEND?2:6)){capture(mc,output,"active",state);captures++;}
        int duration=ElementalIntent.parse(SCENES[phase].word(),SCENES[phase].element()).orElseThrow().charge()+100;
        if(ticks>duration){
            if(captures!=2)throw new IllegalStateException("Missing elemental render phase: "+SCENES[phase].word()+" captures="+captures+" client="+FreeMagicClient.auditSnapshot());
            mc.getSingleplayerServer().execute(()->FreeMagicServer.input(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0),new FreeMagicNetwork.Input(FreeMagicNetwork.STOP,0,"")));
            phase++;prepared=false;
        }
    }
    private static void capture(Minecraft mc,Path output,String step,Map<String,Object> state){
        String name=String.format(Locale.ROOT,"%02d-elements-%s.png",phase,step);views.add(Map.of("image",name,"spell",SCENES[phase].word(),"element",SCENES[phase].element().id(),"client",state));saving=true;
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/"+name),output.resolve(name),StandardCopyOption.REPLACE_EXISTING);}catch(Exception error){throw new RuntimeException(error);}finally{saving=false;}});
    }
    private SixElementNativeAudit(){}
}
