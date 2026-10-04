package com.tnc.tnc.life.ecology;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.home.TownProtection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class EcologyEvents {
    private EcologyEvents(){}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void sampleFern(PlayerInteractEvent.RightClickBlock event){
        if(event.isCanceled()||!(event.getEntity() instanceof ServerPlayer p)||!event.getItemStack().is(Items.SHEARS))return;
        var level=p.serverLevel();var pos=event.getPos();
        if(!level.getBlockState(pos).is(Blocks.FERN)||!level.getBiome(pos).is(BiomeTags.IS_FOREST)||!VerdantVeinBlockEntity.wet(level,pos)||TownProtection.denied(p,pos))return;
        var data=p.getPersistentData();long now=p.server.overworld().getGameTime();
        if(now<data.getLong("TncGreenSampleNext"))return;
        data.putLong("TncGreenSampleNext",now+40);
        int samples=data.getInt("TncGreenSamples")+1;data.putInt("TncGreenSamples",samples);
        if(samples>=10||level.random.nextInt(10)==0){
            Block.popResource(level,pos,new ItemStack(EcologyContent.SEED,2));data.putInt("TncGreenSamples",0);
            p.displayClientMessage(Component.literal("湿林剪样发现两粒绿脉种。普通野外土壤可种；成熟后留株发电，剪枝则取材料。"),true);
        }else p.displayClientMessage(Component.literal("湿林采样 "+samples+"/10：叶脉仍在聚拢。"),true);
        event.getItemStack().hurtAndBreak(1,p,who->who.broadcastBreakEvent(event.getHand()));
        event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
