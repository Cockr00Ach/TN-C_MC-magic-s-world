package com.tnc.tnc.client;
import com.tnc.tnc.magic.WindEarthVisualEntity;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class EarthTremorClient {
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event) {
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
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
        }
        double time=mc.level.getGameTime()+event.getPartialTick();
        event.setPitch(event.getPitch()+(float)Math.sin(time*1.17)*strength);
        event.setRoll(event.getRoll()+(float)Math.sin(time*.83)*strength*.65F);
    }
}
