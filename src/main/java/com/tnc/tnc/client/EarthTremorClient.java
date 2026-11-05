package com.tnc.tnc.client;
import com.tnc.tnc.magic.WindEarthVisualEntity;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class EarthTremorClient {
    /** 1 on the frame the giant column lands, then decays: drives the FOV punch and dust wash. */
    private static float launchKick;
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event) {
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        launchKick*=0.86F;
        float strength=0;
        for(var field:mc.level.getEntitiesOfClass(WindEarthVisualEntity.class,mc.player.getBoundingBox().inflate(34),e->e.kind()==5)) {
            double dist=mc.player.position().distanceTo(field.position());
            if(dist<field.radius())strength=Math.max(strength,(float)(field.tier()*.2*(1-dist/field.radius())));
        }
        // Fresh ruptures: the columns hitting their final height kick the camera.
        for(var rupture:mc.level.getEntitiesOfClass(WindEarthVisualEntity.class,mc.player.getBoundingBox().inflate(40),
                e->e.kind()==4||e.kind()==6)) {
            int age=rupture.tickCount;
            if(age>20)continue;
            double reach=rupture.radius()+12;
            double distance=mc.player.position().distanceTo(rupture.position());
            if(distance>reach)continue;
            float peak=rupture.kind()==4?rupture.tier()*.38F:rupture.tier()*.16F;
            strength=Math.max(strength,(float)(peak*(1-age/20.0)*(1-distance/reach)));
        }
        for(var blast:mc.level.getEntitiesOfClass(com.tnc.tnc.magic.EarthAttackEntity.class,mc.player.getBoundingBox().inflate(40),
                e->e.done()&&(e.mode()==1||e.mode()==2||e.mode()==3||e.mode()==4||e.mode()==6||e.mode()==7))) {
            double elapsed=blast.age((float)event.getPartialTick())-blast.burstAge();
            double distance=mc.player.position().distanceTo(blast.position());
            float peak=switch(blast.mode()) {
                case 2->6.5F;
                case 3->3.2F;
                case 7->2.6F;
                default->1.7F;
            };
            double duration=blast.mode()==2?18:blast.mode()==3?10:8;
            if(elapsed>=0&&elapsed<duration&&distance<48)strength=Math.max(strength,(float)(peak*(1-elapsed/duration)*(1-distance/48)));
            // 巨型土核爆炸也给一记短促的尘幕冲击，普通尖刺不抢镜头。
            if(blast.mode()==2&&elapsed>=0&&elapsed<6&&distance<64)
                launchKick=Math.max(launchKick,(float)((1-elapsed/6)*(1-distance/64)*.7));
        }
        // Giant stone weapons erupting in place of rock spikes hit much harder than a spike.
        for(var spike:mc.level.getEntitiesOfClass(com.tnc.tnc.magic.EarthAttackEntity.class,
                mc.player.getBoundingBox().inflate(44),e->e.mode()==1&&!e.done())) {
            double a=spike.age((float)event.getPartialTick())-spike.waitTicks();
            if(a<0||a>16)continue;
            double distance=mc.player.position().distanceTo(spike.position());
            if(distance>40)continue;
            float big=spike.height()>=7?1F:.4F;
            strength=Math.max(strength,(float)(3.4F*big*(1-a/16)*(1-distance/40)));
            if(a<5)launchKick=Math.max(launchKick,(float)(.55F*big*(1-a/5)*(1-distance/40)));
        }
        // 土神的枪：巨柱升起时持续低频震动，起步瞬间再补一记 FOV 冲击。
        for(var launch:mc.level.getEntitiesOfClass(com.tnc.tnc.magic.EarthAttackEntity.class,mc.player.getBoundingBox().inflate(90),
                e->e.mode()==8&&!e.done())) {
            double distance=mc.player.position().distanceTo(launch.position());
            if(distance>80)continue;
            double age=launch.age((float)event.getPartialTick());
            float reach=(float)(1-distance/80);
            if(age<55)strength=Math.max(strength,4.2F*reach*(age<45?1:.5F));
            if(age<8)launchKick=Math.max(launchKick,(float)((1-age/8)*(.4+.6*reach)));
        }
        double time=mc.level.getGameTime()+event.getPartialTick();
        event.setPitch(event.getPitch()+(float)Math.sin(time*1.17)*strength);
        event.setRoll(event.getRoll()+(float)Math.sin(time*.83)*strength*.65F);
    }
    /** Short zoom-in punch: the ground hits before the camera settles. */
    @SubscribeEvent public static void fov(ComputeFovModifierEvent event) {
        if(launchKick>.01F)event.setNewFovModifier(event.getFovModifier()*(1-.07F*launchKick));
    }
    /** Dust wash across the screen while the column tears past the camera. */
    @SubscribeEvent public static void dustWash(RenderGuiEvent.Post event) {
        int a=(int)(launchKick*70);
        if(a<=2)return;
        var graphics=event.getGuiGraphics();
        var window=Minecraft.getInstance().getWindow();
        graphics.fill(0,0,window.getGuiScaledWidth(),window.getGuiScaledHeight(),(a<<24)|0x6B4A2E);
    }
}
