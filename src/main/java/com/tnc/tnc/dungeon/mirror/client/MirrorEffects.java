package com.tnc.tnc.dungeon.mirror.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.dungeon.mirror.EchoDouble;
import com.tnc.tnc.dungeon.mirror.MirrorEntities;
import com.tnc.tnc.dungeon.mirror.MirrorKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class MirrorEffects {
    static final ResourceLocation WHITE=ResourceLocation.parse("minecraft:textures/misc/white.png");
    private static final RenderType INK=RenderType.entityTranslucent(WHITE);
    private static int silenceAge;
    private static boolean silent;
    private MirrorEffects() {}

    @Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(MirrorEntities.DOUBLE.get(),EchoDoubleRenderer::new);
            e.registerEntityRenderer(MirrorEntities.CORE.get(),EchoCoreRenderer::new);
        }
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers e) {
            for(var skin:e.getSkins()) {
                PlayerRenderer player=e.getSkin(skin);
                if(player!=null) player.addLayer(new WhiteEyes(player));
            }
        }
    }
    private static class WhiteEyes extends RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
        WhiteEyes(PlayerRenderer r) {super(r);}
        @Override public void render(PoseStack ps,MultiBufferSource buf,int light,AbstractClientPlayer p,
                float swing,float amount,float partial,float age,float yaw,float pitch) {
            if(!(p instanceof EchoPlayer body) || !body.actor.melee()) return;
            ps.pushPose();getParentModel().head.translateAndRotate(ps);
            VertexConsumer out=buf.getBuffer(INK);
            for(int side:new int[]{-1,1}) {
                float x=side*.119f, y=-.222f, z=-.2515f;
                v(out,ps,x-.055f,y,z,0xd4f1ff,222);v(out,ps,x+.055f,y,z,0xd4f1ff,222);
                v(out,ps,x+.055f,y+.031f,z,0xffffff,244);v(out,ps,x-.055f,y+.031f,z,0xffffff,244);
            }
            ps.popPose();
        }
    }
    @SubscribeEvent public static void name(RenderNameTagEvent e) {
        if(e.getEntity() instanceof EchoPlayer) e.setResult(Event.Result.DENY);
    }

    static void footRipple(EchoDouble e,float partial,PoseStack ps,MultiBufferSource b) {
        float age=e.tickCount+partial;
        VertexConsumer out=b.getBuffer(INK);
        for(int ring=0;ring<2;ring++) {
            float t=(age/45+ring*.5f)%1, radius=.27f+.36f*(1-t);
            int alpha=(int)(82*Math.sin(t*Math.PI));
            for(int i=0;i<36;i++) {
                if((i+ring*4)%15>=12) continue;
                float a=i*Mth.TWO_PI/36+age*.012f, next=a+Mth.TWO_PI/36;
                int color=e.melee()?0xbfd6df:0x8cb9d9;
                for(int j=0;j<4;j++) {
                    float theta=j<2?a:next,r=radius+(j==1||j==2?.011f:0);
                    v(out,ps,Mth.cos(theta)*r,.025f+ring*.012f,Mth.sin(theta)*r,color,alpha);
                }
            }
        }
    }
    static void blackWater(PoseStack ps,MultiBufferSource b,float time,float strength) {
        var out=b.getBuffer(INK);
        for(int ring=0;ring<3;ring++) {
            float t=(time*.0075f+ring/3f)%1;
            float r=(1-t)*.78f+.07f;
            for(int i=0;i<64;i++) {
                float a=i*Mth.TWO_PI/64+ring*.73f;
                float h=.5f+.5f*Mth.sin(a*2.0f-t*6.28f);
                int alpha=(int)(strength*84*h*Mth.sin(t*Mth.PI));
                for(int j=0;j<4;j++) {
                    float u=j<2?a:a+Mth.TWO_PI/64;
                    float radius=r+(j==1||j==2?.019f:0);
                    v(out,ps,Mth.cos(u)*radius,2.95f+Mth.sin(u)*radius*1.12f,.536f,0xadb3d5,alpha);
                }
            }
        }
    }
    static void v(VertexConsumer out,PoseStack p,float x,float y,float z,int rgb,int a) {
        out.vertex(p.last().pose(),x,y,z).color(rgb>>16&255,rgb>>8&255,rgb&255,a).uv(.5f,.5f)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(p.last().normal(),0,1,0).endVertex();
    }
    @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END) return;
        var mc=Minecraft.getInstance();var p=mc.player;
        boolean wearing=false;
        if(p!=null) for(var slot:EquipmentSlot.values()) {
            var stack=p.getItemBySlot(slot);
            if(stack.hasTag() && stack.getTag().hasUUID(MirrorKit.LOAN)) {wearing=true;break;}
        }
        silent=wearing;
        if(!silent) silenceAge=0;
        else if(!mc.isPaused()) silenceAge=Math.min(100,silenceAge+1);
    }
    public static boolean silent() {return silent;}
    @SubscribeEvent public static void field(RenderGuiEvent.Post e) {
        var mc=Minecraft.getInstance();
        if(!silent || silenceAge>=42 || mc.options.hideGui) return;
        GuiGraphics g=e.getGuiGraphics();
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        float t=silenceAge/42f;
        int alpha=(int)(118*Mth.sin((float)(Math.PI*Math.sqrt(t))));
        int col=0xbfc5d8|(alpha<<24), transparent=0x00bfc5d8;
        g.fillGradient(0,0,w,h/5,col,transparent);
        g.fillGradient(0,h-h/5,w,h,transparent,col);
        int strip=Math.max(6,w/13);
        for(int i=0;i<strip;i+=2) {
            float strength=1-i/(float)strip;
            int edge=((int)(alpha*.68f*strength)<<24)|0xbfc5d8;
            g.fill(i,h/5,i+2,h-h/5,edge);
            g.fill(w-i-2,h/5,w-i,h-h/5,edge);
        }
    }
}
