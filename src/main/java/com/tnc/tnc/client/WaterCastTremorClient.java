package com.tnc.tnc.client;
import com.tnc.tnc.magic.water.TNWaterSpellEntity;
import com.tnc.tnc.magic.water.WaterSpellRules;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Short recoil at release, with gentle charge tension; no permanent camera offset. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class WaterCastTremorClient {
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        double strength=0;
        for(var cast:mc.level.getEntitiesOfClass(TNWaterSpellEntity.class,mc.player.getBoundingBox().inflate(32),c->c.tier()>=4)) {
            double age=cast.age()+e.getPartialTick(),distance=cast.position().distanceTo(mc.player.position());
            double amount=cast.firedAt()<0?Math.min(1,age/WaterSpellRules.charge(cast.tier()))*(cast.tier()==5?.25:.12):
                    Math.max(0,1-(age-cast.firedAt())/12)*(cast.tier()==5?2.6:1.2);
            strength=Math.max(strength,amount*Math.max(0,1-distance/32));
        }
        double time=mc.level.getGameTime()+e.getPartialTick();
        e.setPitch(e.getPitch()+(float)(Math.sin(time*2.3)*strength));
        e.setRoll(e.getRoll()+(float)(Math.sin(time*1.7)*strength*.32));
    }
}
