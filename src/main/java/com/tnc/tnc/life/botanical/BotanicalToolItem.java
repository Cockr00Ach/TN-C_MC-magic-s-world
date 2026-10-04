package com.tnc.tnc.life.botanical;

import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import java.util.List;

public final class BotanicalToolItem extends Item {
    public final String tool;
    public BotanicalToolItem(String tool){super(new Properties().stacksTo(1).durability(tool.equals("pollination_brush")?3:128));this.tool=tool;}
    @Override public InteractionResult useOn(UseOnContext c){var l=c.getLevel();var p=c.getPlayer();if(p==null)return InteractionResult.PASS;if(l.isClientSide)return InteractionResult.SUCCESS;if(!(p instanceof ServerPlayer sp)||TownProtection.denied(sp,c.getClickedPos()))return InteractionResult.FAIL;
        var held=c.getItemInHand();var data=held.getOrCreateTag();if(tool.equals("rain_watering_flask")&&l.getFluidState(c.getClickedPos()).isSource()&&l.getFluidState(c.getClickedPos()).is(net.minecraft.tags.FluidTags.WATER)){data.putInt("Water",8);p.displayClientMessage(Component.literal("雨水壶装满8份水；每株一份，保持湿润10分钟。"),true);return InteractionResult.CONSUME;}
        if(!(l.getBlockEntity(c.getClickedPos()) instanceof BotanicalPlantEntity be))return InteractionResult.PASS;
        if(tool.equals("plant_sample_clip")){p.displayClientMessage(Component.literal(be.status()),true);return InteractionResult.CONSUME;}
        if(tool.equals("rain_watering_flask")){int water=data.getInt("Water");if(water<=0){p.displayClientMessage(Component.literal("壶空了，去实际水源取水。"),true);return InteractionResult.CONSUME;}be.water(p.getUUID(),p.isShiftKeyDown());data.putInt("Water",water-1);return InteractionResult.CONSUME;}
        if(tool.equals("pollination_brush")&&be.species()==BotanicalSpecies.HONEY_CLUSTER){if(be.pollinateOnce())held.hurtAndBreak(1,p,e->e.broadcastBreakEvent(c.getHand()));return InteractionResult.CONSUME;}
        if(tool.equals("field_tuning_bell")&&be.species()==BotanicalSpecies.ECHO_BEAN){be.note(data.getInt("Pitch"));data.putInt("Pitch",(data.getInt("Pitch")+4)%25);held.hurtAndBreak(1,p,e->e.broadcastBreakEvent(c.getHand()));l.playSound(null,c.getClickedPos(),net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.get(),net.minecraft.sounds.SoundSource.BLOCKS,.7F,1F);return InteractionResult.CONSUME;}
        return InteractionResult.PASS;
    }
    @Override public void appendHoverText(ItemStack stack,Level l,List<Component> text,TooltipFlag f){text.add(Component.literal(switch(tool){case "rain_watering_flask"->"实际取水8份；植物每次一份，湿润10分钟；脉舞铃花首次浇水认主，潜行浇水转让。";case "pollination_brush"->"共生蜜簇每次扣一耐久；相隔至少一秒；总共三次。";case "field_tuning_bell"->"回声豆三音认调；旧图对音符盒敲 C—E—G 研究首种。";default->"右键植株显示环境/成熟原因；右键天然样株非破坏采样，首种十次有效采样必得。";}));if(tool.equals("rain_watering_flask"))text.add(Component.literal("储水："+(stack.hasTag()?stack.getTag().getInt("Water"):0)+"/8"));}
}
