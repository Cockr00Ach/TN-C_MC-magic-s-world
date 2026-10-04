package com.tnc.tnc.life.wonders;

import com.tnc.tnc.adventure.AdventureService;
import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** A repeatable world source for the unique vine, with one first study per player. */
public final class SkySurveyItem extends Item {
    public SkySurveyItem(){super(new Properties().stacksTo(1));}
    @Override public InteractionResultHolder<ItemStack> use(Level l,Player who,InteractionHand hand){
        ItemStack stack=who.getItemInHand(hand);
        if(who instanceof ServerPlayer p){
            BlockPos at=p.blockPosition();if(TownProtection.hazard(p.serverLevel(),at))return InteractionResultHolder.fail(stack);
            var data=stack.getOrCreateTag();if(data.hasUUID("Observer")&&!data.getUUID("Observer").equals(p.getUUID())){p.displayClientMessage(Component.literal("这本观测册已有署名，请制作自己的册子。"),true);return InteractionResultHolder.fail(stack);}data.putUUID("Observer",p.getUUID());
            String kind=l.getBiome(at).is(BiomeTags.IS_FOREST)?"林地":at.getY()>=l.getSeaLevel()+40?"高地":at.getY()<=l.getSeaLevel()+8?"谷地":"";
            if(kind.isEmpty()){p.displayClientMessage(Component.literal("寻找高地、谷地、林地各一处，三点相隔至少256格。"),true);return InteractionResultHolder.consume(stack);}
            ListTag samples=data.getList("Samples",Tag.TAG_COMPOUND);
            for(Tag raw:samples){var t=(CompoundTag)raw;if(t.getString("Kind").equals(kind)||BlockPos.of(t.getLong("Position")).distSqr(at)<65536){p.displayClientMessage(Component.literal("已有同类观察，或离上一处不足256格。"),true);return InteractionResultHolder.consume(stack);}}
            CompoundTag sample=new CompoundTag();sample.putString("Kind",kind);sample.putLong("Position",at.asLong());sample.putString("Dimension",l.dimension().location().toString());samples.add(sample);data.put("Samples",samples);
            if(samples.size()==3&&!AdventureService.profile(p).hasMilestone("sky_vine_survey")){
                AdventureService.milestone(p,"sky_vine_survey");var seed=new ItemStack(WonderContent.SKY_VINE_SEED,2);if(!p.getInventory().add(seed))p.drop(seed,false);p.displayClientMessage(Component.literal("三处观测完成，得到望天蔓种荚2；定植需9×9空地及向上64格空间。"),false);
            }else p.displayClientMessage(Component.literal("记录 "+kind+"，已完成 "+samples.size()+"/3。"),true);
        }
        return InteractionResultHolder.sidedSuccess(stack,l.isClientSide);
    }
}
