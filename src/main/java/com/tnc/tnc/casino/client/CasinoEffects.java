package com.tnc.tnc.casino.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.casino.machine.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT)
public final class CasinoEffects {
    @SubscribeEvent public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        var mc=Minecraft.getInstance();
        if(!event.isUseItem()||event.getHand()!=InteractionHand.MAIN_HAND||mc.level==null||mc.player==null||mc.screen!=null) return;
        BlockPos best=null;double nearest=Double.POSITIVE_INFINITY;BlockPos eye=mc.player.blockPosition();
        // Large table surfaces can be clicked from every edge, beyond the single anchor cell.
        for(int cx=(eye.getX()-16)>>4;cx<=(eye.getX()+16)>>4;cx++) for(int cz=(eye.getZ()-16)>>4;cz<=(eye.getZ()+16)>>4;cz++) {
            if(!mc.level.hasChunk(cx,cz)) continue;
            for(var entity:mc.level.getChunk(cx,cz).getBlockEntities().values()) {
                if(!(entity instanceof TNMachineEntity table)) continue;
                BlockPos pos=table.getBlockPos();
                Vec3 hit=MachineInteraction.hit(mc.player,table);
                if(hit==null) continue;
                double distance=hit.distanceTo(mc.player.getEyePosition());
                if(distance<nearest) {nearest=distance;best=pos;}
            }
        }
        if(best!=null&&!(mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult e&&e.getLocation().distanceTo(mc.player.getEyePosition())<nearest)) {
            event.setCanceled(true);event.setSwingHand(false);
            CasinoPackets.CHANNEL.sendToServer(new CasinoPackets.TableClick(best));
        }
    }
    public static void burst(CasinoPackets.Celebration effect) {
        var level=Minecraft.getInstance().level;if(level==null) return;
        CompoundTag fireworks=new CompoundTag();ListTag explosions=new ListTag();
        CompoundTag bloom=new CompoundTag();bloom.putByte("Type",(byte)1);
        bloom.putIntArray("Colors",new int[]{CasinoPalette.BRASS,CasinoPalette.CHIP_RED,CasinoPalette.FELT});
        bloom.putIntArray("FadeColors",new int[]{CasinoPalette.CARD_FACE});bloom.putBoolean("Trail",true);bloom.putBoolean("Flicker",true);
        explosions.add(bloom);fireworks.put("Explosions",explosions);
        level.createFireworks(effect.x(),effect.y(),effect.z(),0,0.02,0,fireworks);
    }
}
