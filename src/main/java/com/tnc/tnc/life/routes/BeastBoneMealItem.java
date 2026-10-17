package com.tnc.tnc.life.routes;

import com.tnc.tnc.life.botanical.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Growth accelerator: advances the plant, never its stored mana or harvest balance. */
public final class BeastBoneMealItem extends Item {
    public BeastBoneMealItem(){super(new Properties());}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return useOn(context);}
    @Override public InteractionResult useOn(UseOnContext context){
        var level=context.getLevel();var pos=context.getClickedPos();var state=level.getBlockState(pos);
        if(!RoutePlanting.magic(state))return InteractionResult.PASS;
        if(level.isClientSide)return InteractionResult.SUCCESS;
        if(!(context.getPlayer() instanceof ServerPlayer player)||!RouteAccess.allowed(player.serverLevel(),pos,player.getUUID()))return InteractionResult.FAIL;
        if(!RoutePlanting.wild(state,level,pos)&&!(MagicSoilBlock.isSoil(level.getBlockState(RoutePlanting.soil(state,pos)))&&MagicSoilBlock.irrigated(level,RoutePlanting.soil(state,pos)))){
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("先把植株种在魔法土上，并在八格内放置流动魔力。"),true);return InteractionResult.FAIL;
        }
        boolean full=player.isCreative()&&player.isShiftKeyDown();
        var entity=level.getBlockEntity(pos);boolean changed=false;
        if(entity instanceof BotanicalPlantEntity plant){
            int target=Math.min(plant.species().firstTicks,full?plant.species().firstTicks:plant.growth+(plant.species().firstTicks+2)/3);
            changed=target>plant.growth;plant.growth=target;
            if(changed){level.setBlock(pos,state.setValue(BotanicalBlock.AGE,Math.min(3,target*3/plant.species().firstTicks)),3);plant.changed();}
        }else if(entity instanceof RoutePlantEntity plant){
            int target=Math.min(18000,full?18000:plant.growth+6000);changed=target>plant.growth;plant.growth=target;
            if(changed){level.setBlock(pos,state.setValue(RoutePlantBlock.AGE,target/6000),3);plant.setChanged();}
        }else if(entity instanceof com.tnc.tnc.life.ecology.VerdantVeinBlockEntity plant){changed=plant.fertilize(full);
        }else if(entity instanceof com.tnc.tnc.life.wonders.SkyVineRootEntity plant){changed=plant.fertilize(full);
        }else{
            for(var property:state.getProperties())if(property.getName().equals("age")&&property instanceof IntegerProperty age){
                int max=age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0),current=state.getValue(age);
                if(current<max){level.setBlock(pos,state.setValue(age,full?max:Math.min(max,current+Math.max(1,(max+2)/3))),3);changed=true;}
                break;
            }
            if(!changed&&state.hasProperty(com.tnc.tnc.life.HomewardFlower.FlowerBlock.BLOOMING)&&!state.getValue(com.tnc.tnc.life.HomewardFlower.FlowerBlock.BLOOMING)){level.setBlock(pos,state.setValue(com.tnc.tnc.life.HomewardFlower.FlowerBlock.BLOOMING,true),3);changed=true;}
        }
        if(!changed)return InteractionResult.SUCCESS;
        if(!player.isCreative())context.getItemInHand().shrink(1);
        player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,pos.getX()+.5,pos.getY()+.45,pos.getZ()+.5,8,.3,.3,.3,.01);
        level.playSound(null,pos,net.minecraft.sounds.SoundEvents.BONE_MEAL_USE,net.minecraft.sounds.SoundSource.BLOCKS,.6f,1.2f);
        return InteractionResult.CONSUME;
    }
    @Override public void appendHoverText(ItemStack stack,net.minecraft.world.level.Level level,java.util.List<net.minecraft.network.chat.Component> lines,TooltipFlag flag){
        lines.add(net.minecraft.network.chat.Component.literal("右键魔植推进一阶段；创造模式潜行右键直接成株"));
        lines.add(net.minecraft.network.chat.Component.literal("需要魔法土与灌溉；只催生长，不补充魔力或复制产物"));
    }
}
