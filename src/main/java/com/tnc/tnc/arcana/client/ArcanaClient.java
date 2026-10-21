package com.tnc.tnc.arcana.client;
import com.tnc.tnc.arcana.*;
import com.tnc.tnc.mana.sight.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.eventbus.api.SubscribeEvent;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ArcanaClient {
    public static int toxicity;public static double sanity;public static boolean clear,sun,aurora,master,bathing;public static long overloadUntil;public static int overloadStage;
    public static boolean spiritVisible(){return ManaSightClient.enabled()&&sanity>80;}
    public static void receive(CompoundTag t){var mc=Minecraft.getInstance();switch(t.getString("Type")){
        case "physiology"->{bathing=t.getBoolean("Bathing");toxicity=t.getInt("Toxicity");sanity=t.getDouble("Sanity");clear=t.getBoolean("Clear");sun=t.getBoolean("Sun");aurora=t.getBoolean("Aurora");master=t.getBoolean("MasterAura");}
        case "contract"->mc.setScreen(new ContractScreen(t.getInt("Entity")));
        case "overload"->{overloadUntil=t.getLong("Until");overloadStage=t.getInt("Stage");}
        case "entity_scale"->{if(mc.level!=null&&mc.level.getEntity(t.getInt("Entity")) instanceof net.minecraft.world.entity.LivingEntity a){a.getPersistentData().putFloat("ArcanaScale",t.getFloat("Scale"));a.getPersistentData().putLong("ClientScaleUntil",mc.level.getGameTime()+60);a.refreshDimensions();}}
        default->{}
    }}
    @SubscribeEvent public static void hud(net.minecraftforge.client.event.RenderGuiOverlayEvent.Post e){var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||!e.getOverlay().id().toString().equals("minecraft:hotbar"))return;var g=e.getGuiGraphics();boolean sight=ManaSightClient.enabled();
        if(sight){g.fill(8,43,222,68,0x990b1820);g.drawString(mc.font,"内视 · 淤积 "+toxicity+" /100  精神过载 "+(int)sanity+"%",13,48,0xffc5e9e1,false);if(clear)g.drawString(mc.font,"经脉通畅",13,59,0xffffdf94,false);
            if(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit){var be=com.tnc.tnc.alchemy.AlchemyBlock.master(mc.level,hit.getBlockPos());if(be!=null){String text=!be.ready.isEmpty()?"药液已成 · 玻璃瓶装取":be.sealed?"封盖续煮 · 药性已锁定":be.perfectRemaining>0?"★ 漩涡同心 · 此刻封盖 ★":"观察锅周颜色与药材漩涡";g.drawCenteredString(mc.font,text,mc.getWindow().getGuiScaledWidth()/2,mc.getWindow().getGuiScaledHeight()/2+29,be.perfectRemaining>0?0xffffe7a3:0xff000000|RitualRules.temperatureColor(be.temperature));}}
            if(sanity>40){int alpha=(int)Math.min(70,(sanity-40));int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();for(int i=0;i<8;i++){int a=Math.max(0,alpha-i*5);g.fill(i*3,0,i*3+3,h,(a<<24)|0x8b51c6);g.fill(w-i*3-3,0,w-i*3,h,(a<<24)|0x39d6d3);}}
            if(overloadStage==3&&mc.level.getGameTime()<overloadUntil)g.fill(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0x55a50019);
        }
    }
    @SubscribeEvent public static void inventory(net.minecraftforge.client.event.ScreenEvent.Render.Post e){if(!(e.getScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen inv)||!ManaSightClient.enabled())return;var mc=Minecraft.getInstance();e.getGuiGraphics().drawString(mc.font,"内视 · 淤积 "+toxicity+" /100",8,8,toxicity>50?0xffc896e8:0xff9ee9e9,false);
        var g=e.getGuiGraphics();int x=inv.getGuiLeft()+59,y=inv.getGuiTop()+43,c=toxicity>50?0xff725089:0xb778eee1;
        for(int k=0;k<5;k++){g.fill(x-1,y+k*3,x+1,y+k*3+3,c);g.fill(x-1-k,y+3+k*2,x-k,y+4+k*2,c);g.fill(x+k,y+3+k*2,x+1+k,y+4+k*2,c);}
        for(int k=0;k<toxicity/20;k++)g.fill(x-4+(k%3)*4,y+2+k*3,x-1+(k%3)*4,y+5+k*3,0xff392641);}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null){sanity=0;toxicity=0;aurora=false;return;}long now=mc.level.getGameTime();if(now%2!=0)return;var p=mc.player;boolean sight=ManaSightClient.enabled();
        if(sight){int color=toxicity>50?0x694488:0x7de7df;for(int i=0;i<3;i++){double a=now*.07+i*2.094;var at=p.position().add(Math.cos(a)*.35,.7+i*.23,Math.sin(a)*.35);ManaSightParticles.effect(at,at.add(0,.2,0),color,0,0,.035f,24);}
            if(bathing&&p.isInWater())for(int i=0;i<4;i++)ManaSightParticles.effect(p.position().add((Math.random()-.5)*.8,.2,(Math.random()-.5)*.8),p.position().add(0,2,0),0xffde8c,0,3,.07f,40);
            if(master&&now%4==0)for(int i=0;i<6;i++){double a=i*Math.PI/3+now*.02;var at=p.position().add(Math.cos(a)*.7,.15,Math.sin(a)*.7);ManaSightParticles.effect(at,at.add(0,.5,0),0xffe8a6,1,0,.06f,30);}
            if(sun)ManaSightParticles.effect(p.position().add(0,.65,0),p.position().add(0,.65,0),0xffd780,1,0,.28f,20);
            if(sanity>50&&now%180==0)p.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE,.2f,.5f+(float)Math.random()*.4f);
        }
        if(aurora){int count=sight?10:2;for(int i=0;i<count;i++){double a=Math.random()*Math.PI*2,r=12+Math.random()*75;var at=p.position().add(Math.cos(a)*r,22+Math.sin(a*3+now*.003)*8+Math.random()*12,Math.sin(a)*r);int[] colors={0x43e5c4,0x839dff,0xf58cc7,0xffd68d};ManaSightParticles.effect(at,at.add(0,-8,0),colors[(int)(Math.random()*4)],i%9==0?1:2,3,sight?.5f:.12f,100);}}
    }
    @SubscribeEvent public static void fov(net.minecraftforge.client.event.ComputeFovModifierEvent e){if(ManaSightClient.enabled()&&sanity>50){var mc=Minecraft.getInstance();if(mc.level!=null)e.setNewFovModifier((float)(e.getNewFovModifier()*(1+Math.sin(mc.level.getGameTime()*.035)*.012*(sanity-50)/50)));}}
    @SubscribeEvent public static void scale(net.minecraftforge.client.event.RenderLivingEvent.Pre<?,?> e){var a=e.getEntity();var t=a.getPersistentData();if(t.contains("ArcanaScale")){if(t.getLong("ClientScaleUntil")<a.level().getGameTime()){t.remove("ArcanaScale");a.refreshDimensions();}else{float scale=t.getFloat("ArcanaScale");e.getPoseStack().scale(scale,scale,scale);}}}
}
