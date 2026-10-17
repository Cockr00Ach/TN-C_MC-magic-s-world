package com.tnc.tnc.tavern.client;

import com.tnc.tnc.tavern.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Nearby, occluded, staggered ambient speech. At most two bubbles in the entire client. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class TavernChatter {
    private record Bubble(String text,long start){}
    private static final Map<UUID,Bubble> ACTIVE=new HashMap<>();
    private static final Map<UUID,Long> NEXT=new HashMap<>();
    private static final List<String> LINES=new ArrayList<>();
    private static Object world;private static long clock;
    private static void load(){
        LINES.clear();
        try(var reader=Minecraft.getInstance().getResourceManager().openAsReader(ResourceLocation.fromNamespaceAndPath("tnc","tavern/chatter.json"))){
            for(var line:com.google.gson.JsonParser.parseReader(reader).getAsJsonArray())LINES.add(TavernChatterRules.compact(line.getAsString()));
        }catch(Exception ignored){LINES.add("炉火正暖，歇一会儿吧。");}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level!=world){world=mc.level;clock=0;ACTIVE.clear();NEXT.clear();if(world!=null)load();}
        if(mc.level==null||mc.player==null||mc.isPaused())return;
        clock++;ACTIVE.entrySet().removeIf(e->clock-e.getValue().start>=TavernChatterRules.DURATION);
        if(clock%40!=0||ACTIVE.size()>=TavernChatterRules.MAX_ACTIVE||mc.screen!=null)return;
        var nearby=mc.level.getEntitiesOfClass(TavernGuestEntity.class,mc.player.getBoundingBox().inflate(TavernChatterRules.RANGE),
                g->g.isAlive()&&g.distanceToSqr(mc.player)<=144&&!ACTIVE.containsKey(g.getUUID())&&NEXT.getOrDefault(g.getUUID(),0L)<=clock&&visible(g));
        if(nearby.isEmpty())return;
        var guest=nearby.get(mc.level.random.nextInt(nearby.size()));
        ACTIVE.put(guest.getUUID(),new Bubble(LINES.get(mc.level.random.nextInt(LINES.size())),clock));
        NEXT.put(guest.getUUID(),clock+TavernChatterRules.COOLDOWN+mc.level.random.nextInt(401));
        if(NEXT.size()>128)NEXT.keySet().removeIf(id->mc.level.getEntitiesOfClass(TavernGuestEntity.class,mc.player.getBoundingBox().inflate(32)).stream().noneMatch(g->g.getUUID().equals(id)));
    }
    private static boolean visible(TavernGuestEntity guest){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return false;
        return mc.level.clip(new ClipContext(mc.player.getEyePosition(),guest.position().add(0,1.45,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player)).getType()==HitResult.Type.MISS;
    }
    public static void render(TavernGuestEntity guest,float partial,PoseStack pose,MultiBufferSource buffers){
        var mc=Minecraft.getInstance();var bubble=ACTIVE.get(guest.getUUID());
        if(bubble==null||mc.player==null||mc.screen!=null||guest.distanceToSqr(mc.player)>144||!visible(guest))return;
        float alpha=TavernChatterRules.alpha(Math.max(0,clock-bubble.start));if(alpha<=0)return;
        var lines=mc.font.split(Component.literal(bubble.text),108);int count=Math.min(2,lines.size()),opacity=(int)(alpha*255);
        pose.pushPose();pose.translate(0,1.5,0);pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());pose.scale(-.014f,-.014f,.014f);
        for(int i=0;i<count;i++){
            var line=lines.get(i);float x=-mc.font.width(line)/2f;
            mc.font.drawInBatch(line,x,-count*10+i*10,(opacity<<24)|0x244a49,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,((int)(alpha*225)<<24)|0xf1ecd9,0xF000F0);
        }
        mc.font.drawInBatch("▾",-3,0,(opacity<<24)|0xf1ecd9,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,0xF000F0);
        pose.popPose();
    }
    private TavernChatter(){}
}
