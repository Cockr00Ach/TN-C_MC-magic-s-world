package com.tnc.tnc.client;

import com.tnc.tnc.boss.TNDarkGiantEntity;
import com.tnc.tnc.magic.TNDarkDrainEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Suppress voluntary movement while keeping camera and attack input available. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class DarkGiantSiphonInput {
    @SubscribeEvent public static void movement(MovementInputUpdateEvent event){
        // Minimal Forge dev clients do not include the optional Fabric spell engine.
        // Do not resolve spell-spawned entity classes before confirming it is present.
        if(!net.minecraftforge.fml.ModList.get().isLoaded("spell_engine"))return;
        var player=event.getEntity();
        if(com.tnc.tnc.equipment.PurityAmulet.equipped(player))return;
        boolean bound=player.level().getEntitiesOfClass(TNDarkDrainEntity.class,player.getBoundingBox().inflate(4),
                drain->drain.isAlive()&&drain.victimId()==player.getId()
                        &&player.level().getEntity(drain.casterId()) instanceof TNDarkGiantEntity boss&&boss.isAlive()).size()>0;
        if(!bound)return;
        var input=event.getInput();input.leftImpulse=0;input.forwardImpulse=0;
        input.up=false;input.down=false;input.left=false;input.right=false;input.jumping=false;input.shiftKeyDown=false;
        player.setSprinting(false);
    }
}
