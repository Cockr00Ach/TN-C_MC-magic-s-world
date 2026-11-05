package com.tnc.tnc.bronze;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tnc")
public final class BronzeSummons {
    public static long time(net.minecraft.server.MinecraftServer server){return server.overworld().getGameTime();}
    public static InteractionResult summon(ServerPlayer owner){
        if(!BronzeTwinWeapon.active(owner))return InteractionResult.FAIL;
        var data=PuppetBondData.get(owner.server);long now=time(owner.server);var bond=data.bond(owner.getUUID());
        if(!data.available(owner.getUUID(),now)){
            owner.displayClientMessage(bond.puppet()!=null?Component.translatable("message.tnc.puppet_exists"):Component.translatable("message.tnc.puppet_cooldown",(bond.readyAt()-now+19)/20),true);return InteractionResult.FAIL;
        }
        var puppet=new BronzePuppetEntity(BronzeWeapons.PUPPET.get(),owner.serverLevel());boolean placed=false;
        for(int i=0;i<8;i++){
            double angle=Math.toRadians(owner.getYRot()+i*45);puppet.setPos(owner.getX()-Math.sin(angle)*2.4,owner.getY()+.1,owner.getZ()+Math.cos(angle)*2.4);
            if(owner.level().noCollision(puppet)&&!owner.level().containsAnyLiquid(puppet.getBoundingBox())){placed=true;break;}
        }
        if(!placed){owner.displayClientMessage(Component.translatable("message.tnc.puppet_no_space"),true);return InteractionResult.FAIL;}
        puppet.bind(owner,now);
        if(!data.reserve(owner.getUUID(),puppet.getUUID(),now))return InteractionResult.FAIL;
        if(!owner.serverLevel().addFreshEntity(puppet)){data.failed(owner.getUUID(),puppet.getUUID());return InteractionResult.FAIL;}
        owner.serverLevel().playSound(null,puppet.blockPosition(),net.minecraft.sounds.SoundEvents.IRON_GOLEM_REPAIR,net.minecraft.sounds.SoundSource.PLAYERS,.8f,.7f);
        owner.displayClientMessage(Component.translatable("message.tnc.puppet_summoned"),true);return InteractionResult.SUCCESS;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        var data=PuppetBondData.get(server);long now=time(server);
        for(var row:data.expired(now)){
            BronzePuppetEntity found=null;
            for(var level:server.getAllLevels())if(level.getEntity(row.getValue().puppet()) instanceof BronzePuppetEntity puppet){found=puppet;break;}
            if(found!=null)found.discard();
            data.vanished(row.getKey(),row.getValue().puppet(),now);
        }
    }
}
