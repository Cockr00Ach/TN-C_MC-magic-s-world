package com.tnc.tnc.life.routes.mech;
import com.tnc.tnc.life.routes.RouteMechItem;
import com.tnc.tnc.mana.sight.client.ManaSightParticles;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class MechClient {
    private static KeyMapping key(String name,int code){return new KeyMapping("key.tnc.mech_"+name,net.minecraftforge.client.settings.KeyConflictContext.IN_GAME,com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,code,"key.categories.tnc_mech");}
    public static final KeyMapping DASH=key("dash",GLFW.GLFW_KEY_V),SHIELD=key("shield",GLFW.GLFW_KEY_B),OVERDRIVE=key("overdrive",GLFW.GLFW_KEY_G);
    @Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static class Keys{
        @SubscribeEvent public static void models(net.minecraftforge.client.event.ModelEvent.RegisterAdditional e){for(String p:java.util.List.of("body","barrels","glow"))e.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","item/mech_spellgun_"+p));}
        @SubscribeEvent public static void colors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item e){e.register((stack,tint)->MechRules.COLORS[Math.floorMod(stack.getOrCreateTag().getInt("MechElement"),7)],com.tnc.tnc.life.routes.RouteContent.item("mech_spellgun"));}
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){e.register(DASH);e.register(SHIELD);e.register(OVERDRIVE);}}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        boolean worn=mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof RouteMechItem;
        while(DASH.consumeClick())if(worn&&mc.screen==null)MechNetwork.send(mc.player.isShiftKeyDown()?MechNetwork.Action.NOVA:MechNetwork.Action.DASH);
        while(SHIELD.consumeClick())if(worn&&mc.screen==null)MechNetwork.send(MechNetwork.Action.SHIELD);
        while(OVERDRIVE.consumeClick())if(worn&&mc.screen==null)MechNetwork.send(MechNetwork.Action.OVERDRIVE);
        if(mc.isPaused()||mc.player.tickCount%4!=0)return;
        long time=mc.level.getGameTime();for(var p:mc.level.players())if(p.distanceToSqr(mc.player)<40*40){var stack=p.getItemBySlot(EquipmentSlot.CHEST);if(!(stack.getItem() instanceof RouteMechItem)||!stack.hasTag())continue;var tag=stack.getTag();
            if(tag.getLong("MechShield")>time)for(int i=0;i<8;i++){double a=(time+i*17)*.28;Vec3 at=p.position().add(Math.cos(a)*.9,.2+((time+i*31)%100)/60.,Math.sin(a)*.9);ManaSightParticles.effect(at,at.add(0,.08,0),0xa2eaff,1,0,.065f,12);}
            if(tag.getLong("MechBoost")>time||tag.getLong("MechOverdrive")>time){Vec3 at=p.position().add(p.getLookAngle().scale(-.5)).add(0,1,0);for(int i=0;i<6;i++)ManaSightParticles.effect(at,at.add((Math.random()-.5)*.7,-.6,0),0x5ce4ee,0,3,.09f,12);}
        }
    }
    @SubscribeEvent public static void pose(net.minecraftforge.client.event.RenderLivingEvent.Pre<?,?> e){if(e.getEntity() instanceof net.minecraft.world.entity.player.Player p&&p.isUsingItem()&&p.getUseItem().is(com.tnc.tnc.life.routes.RouteContent.item("mech_spellgun"))&&e.getRenderer().getModel() instanceof net.minecraft.client.model.HumanoidModel<?> m){if(p.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT){m.rightArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.CROSSBOW_HOLD;m.leftArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;}else{m.leftArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.CROSSBOW_HOLD;m.rightArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;}}}
    public static void accept(MechNetwork.Fx fx){var mc=Minecraft.getInstance();if(mc.level==null||fx.element()<0||fx.element()>=7||fx.type()>4||fx.from().distanceToSqr(fx.to())>128*128)return;int color=MechRules.COLORS[fx.element()];
        if(fx.type()==0){
            for(int i=0;i<14;i++){Vec3 at=fx.from().add((Math.random()-.5)*.09,(Math.random()-.5)*.09,(Math.random()-.5)*.09);ManaSightParticles.effect(at,fx.to(),color,i==0?1:0,2,i==0?.16f:.08f,Math.max(6,Math.min(18,(int)(fx.from().distanceTo(fx.to())/4))));}
            for(int i=0;i<5;i++)ManaSightParticles.effect(fx.to(),fx.to().add((Math.random()-.5)*.8,Math.random()*.7,(Math.random()-.5)*.8),color,0,3,.07f,10);
        }else{
            int n=fx.type()==4?140:80;for(int i=0;i<n;i++){double a=i*2.3999632,y=1-2*(i+.5)/n,r=Math.sqrt(1-y*y),radius=fx.type()==4?5.5:fx.type()==2?1.3:2.2;Vec3 to=fx.from().add(Math.cos(a)*r*radius,y*radius,Math.sin(a)*r*radius);ManaSightParticles.effect(fx.type()==2?to:fx.from(),to,fx.type()==2?0xb5edff:color,i%9==0?1:0,3,.08f,15+i%12);}
        }
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui||mc.screen!=null)return;var stack=mc.player.getItemBySlot(EquipmentSlot.CHEST);if(!(stack.getItem() instanceof RouteMechItem))return;var tag=stack.getOrCreateTag();GuiGraphics g=event.getGuiGraphics();int x=10,y=g.guiHeight()-91,w=192;g.fill(x,y,x+w,y+52,0xd00a141d);g.fill(x,y,x+2,y+52,0xff77dbd4);int element=Math.floorMod(tag.getInt("MechElement"),7);g.drawString(mc.font,"天穹式 · "+MechRules.ELEMENTS[element]+"晶铳",x+8,y+5,0xffead7b2,false);g.drawString(mc.font,"储魔 "+tag.getInt("ChargedMana")+" / "+MechRules.CAPACITY,x+8,y+17,0xffa8e7e1,false);g.fill(x+8,y+30,x+w-8,y+33,0xff1a2b34);g.fill(x+8,y+30,x+8+(w-16)*Math.min(1000,tag.getInt("MechHeat"))/1000,y+33,0xffe7a25e);g.drawString(mc.font,DASH.getTranslatedKeyMessage().getString()+" 推进 / 潜行震荡  "+SHIELD.getTranslatedKeyMessage().getString()+" 屏障  "+OVERDRIVE.getTranslatedKeyMessage().getString()+" 过载",x+8,y+39,0xffb9c5c5,false);}
}
