package com.tnc.tnc.client;

import com.mojang.math.Axis;
import com.tnc.tnc.magic.wind.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Opt-in real entity renderer gallery, omitted from installed patches. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class WindTwoChainsClientAudit {
    private static boolean detached,started,saving;private static volatile boolean saved;private static int frames;
    private static final List<String> failures=new ArrayList<>();private static final List<WindVolleyEntity> volleys=new ArrayList<>();private static WindBallEntity ball;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception{
        if(!Boolean.getBoolean("tnc.windTwoChainsAudit")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(!detached){
            detached=true;
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkAimMechanics.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkHandCord.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(TNSpellClientVisuals.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(DarkGiantSiphonInput.class);
        }
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        if(!started){
            started=true;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(1);mc.resizeDisplay();
            var target=EntityType.ZOMBIE.create(mc.level);target.setPos(mc.player.position().add(0,0,20));
            ball=WindChainRegistration.BALL.create(mc.level);ball.configure(mc.player,5);ball.anchor();
            for(int t=1;t<=5;t++){var v=WindChainRegistration.VOLLEY.create(mc.level);v.configure(mc.player,t,target);volleys.add(v);}
            if(!(mc.getEntityRenderDispatcher().getRenderer(ball) instanceof WindChainRenderers.Ball))failures.add("Ball renderer registration");
            for(var v:volleys)if(!(mc.getEntityRenderDispatcher().getRenderer(v) instanceof WindChainRenderers.Volley))failures.add("Volley renderer registration");
            mc.setScreen(new Gallery());
        }
        if(++frames<40)return;
        if(!saving){saving=true;Screenshot.grab(mc.gameDirectory,"wind_two_chains.png",mc.getMainRenderTarget(),m->saved=true);return;}
        if(!saved)return;
        Files.writeString(Path.of(System.getProperty("tnc.windTwoChainsAuditReport")),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("failures",failures,"weaponCounts",volleys.stream().map(WindVolleyEntity::count).toList(),"giantScale",WindChainRules.modelScale(5),"scope","Actual registered entity renderers in isolated client; not a full-pack FPS test")));mc.stop();
    }
    @SuppressWarnings("unchecked") private static void age(Entity entity,int age)throws Exception{
        var field=entity.getClass().getDeclaredField("START");field.setAccessible(true);
        entity.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor<Long>)field.get(null),entity.level().getGameTime()-age);
    }
    private static final class Gallery extends Screen {
        Gallery(){super(Component.literal("Wind chains"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFF101E27);g.drawString(font,"WIND / PULLING CORE + ELEMENTAL WEAPON CHAINS / ACTUAL ENTITY RENDERERS",25,20,0xB8F5E3,false);
            var mc=Minecraft.getInstance();var buffer=mc.renderBuffers().bufferSource();
            for(int i=0;i<6;i++){
                int cell=width/3,row=i/3,col=i%3,x=col*cell,y=55+row*(height-85)/2,h=(height-85)/2-12;
                g.fill(x+12,y,x+cell-12,y+h,0xFF233A46);
                g.drawString(font,new String[]{"WINGBLADE / SPELL + HELD RELIC","WINDLANCE / SPELL + HELD RELIC","T2 / ORBIT ATTACK","T4 / THREE ORBIT REVOLUTIONS","T5 / SEQUENTIAL SEAL ASCENT / SCALE 60","T5 / GIANT COMPRESSING WIND CORE"}[i],x+22,y+15,0xC9F7E7,false);
                boolean pushed=false;
                try{
                    g.flush();g.pose().pushPose();pushed=true;g.pose().translate(x+cell/2,y+h/2+20,450);
                    if(i<2){
                        g.pose().scale(100,-100,100);g.pose().mulPose(Axis.XP.rotationDegrees(72));g.pose().mulPose(Axis.YP.rotationDegrees(-25));
                        WindConstructModels.weapon(i+1,g.pose(),buffer,.99F,false);buffer.endBatch();
                        var item=new net.minecraft.world.item.ItemStack(i==0?WindRelicItems.SWORD:WindRelicItems.SPEAR);
                        if(mc.getItemRenderer().getModel(item,mc.level,mc.player,0)==mc.getModelManager().getMissingModel())throw new IllegalStateException("Held item model missing");
                    }else{
                        Entity entity=i==5?ball:volleys.get(i==2?1:i==3?3:4);age(entity,i==5?10:i==4?65:60);
                        float scale=i==5?2:i==4?1.3F:9;
                        g.pose().scale(scale,-scale,scale);g.pose().mulPose(Axis.XP.rotationDegrees(12));g.pose().mulPose(Axis.YP.rotationDegrees(-25));
                        if(entity instanceof WindVolleyEntity volley){var relative=volley.aimPoint().subtract(entity.position());g.pose().translate(-relative.x,-(i==4?65:8),-relative.z);}
                        mc.getEntityRenderDispatcher().setRenderShadow(false);
                        mc.getEntityRenderDispatcher().render(entity,0,0,0,0,partial,g.pose(),buffer,net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);buffer.endBatch();
                    }
                }catch(Throwable t){if(!failures.contains(t.toString()))failures.add(t.toString());}
                finally{if(pushed)g.pose().popPose();mc.getEntityRenderDispatcher().setRenderShadow(true);}
            }
            g.drawString(font,"Native layered models / engraved metal / held item models checked / bounded wind trails",25,height-20,0x91BFB2,false);
        }
    }
}
