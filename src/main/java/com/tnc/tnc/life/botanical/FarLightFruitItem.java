package com.tnc.tnc.life.botanical;

import com.tnc.tnc.life.wonders.FarLightProjectile;
import com.tnc.tnc.life.wonders.LightBloomData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The fruit's light is world light, shared by players; it is not night vision. */
public final class FarLightFruitItem extends Item {
    @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext c){return com.tnc.tnc.life.routes.RoutePlanting.plantFruit("farlight_fruit",c);}
    public FarLightFruitItem(){super(new Properties().stacksTo(16));}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(player instanceof ServerPlayer server){
            if(!LightBloomData.get(server.serverLevel()).canBegin(server.getUUID(),level.getGameTime())){
                player.displayClientMessage(Component.literal("远照果同时最多两片光域；这一维度最多八片。"),true);
                return InteractionResultHolder.fail(stack);
            }
            FarLightProjectile projectile=new FarLightProjectile(level,player);
            projectile.setItem(new ItemStack(this));
            projectile.shootFromRotation(player,player.getXRot(),player.getYRot(),0,1.0F,0.6F);
            level.addFreshEntity(projectile);
            if(!player.isCreative())stack.shrink(1);
            player.getCooldowns().addCooldown(this,20);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,Level level,java.util.List<Component> text,net.minecraft.world.item.TooltipFlag flags){
        text.add(Component.literal("投出后照亮落点可见空间，水平半径50格，持续20秒。"));
        text.add(Component.literal("光不穿墙；仅占空气，不覆盖植物、建筑和容器。"));
    }
}
