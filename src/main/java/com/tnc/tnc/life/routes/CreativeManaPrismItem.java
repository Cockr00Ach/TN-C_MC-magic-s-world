package com.tnc.tnc.life.routes;

import com.tnc.tnc.life.pasture.*;
import com.tnc.tnc.production.MagicForgeBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Explicit creative testing source. Normal bottles and networks still conserve mana. */
public final class CreativeManaPrismItem extends Item {
    public CreativeManaPrismItem(){super(new Properties().stacksTo(1).rarity(Rarity.EPIC));}
    public static int fillItem(ServerPlayer p,ItemStack stack){
        if(!p.isCreative())return 0;
        if(stack.getItem() instanceof ManaCharged charged&&charged.manaCapacity()>0)
            return RouteChargeLedger.get(p.serverLevel()).deposit(stack,charged.manaCapacity(),charged.manaCapacity());
        if(stack.getItem() instanceof ManaBottleItem){int cap=stack.is(PastureRegistry.item("refined_mana_bottle"))?400:100;return PastureBottleLedger.get(p.serverLevel()).deposit(stack,cap,cap);}
        return 0;
    }
    @Override public InteractionResult useOn(UseOnContext c){
        if(c.getLevel().isClientSide)return InteractionResult.SUCCESS;
        if(!(c.getPlayer() instanceof ServerPlayer p))return InteractionResult.FAIL;
        if(!p.isCreative()){p.displayClientMessage(Component.literal("测试棱晶仅限创造模式使用"),true);return InteractionResult.FAIL;}
        var l=p.serverLevel();var at=c.getClickedPos();int amount=0;
        if(l.getBlockEntity(at) instanceof RouteNodeEntity node){
            if(!node.mayUse(p))return InteractionResult.FAIL;
            amount=node.receive(node.kind().capacity);
            if(node.kind()==RouteKind.CHARGER)amount+=fillItem(p,node.inventory.getItem(0));
            node.setChanged();l.sendBlockUpdated(at,node.getBlockState(),node.getBlockState(),2);
        }else if(l.getBlockEntity(at) instanceof MagicForgeBlockEntity forge){
            if(!forge.canOperate(p,true)||!forge.formedForTransfer()){p.displayClientMessage(Component.literal("先按3×3×3搭好自己的魔力炉"),true);return InteractionResult.FAIL;}
            amount=MagicForgeBlockEntity.MAX_CHARGE-forge.charge();forge.addCharge(amount);
        }else return InteractionResult.PASS;
        p.displayClientMessage(Component.literal("创造测试：补入 "+amount+"M；充能台内装备已补满"),true);
        return InteractionResult.CONSUME;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player who,InteractionHand hand){
        var held=who.getItemInHand(hand);if(level.isClientSide)return InteractionResultHolder.success(held);
        if(!(who instanceof ServerPlayer p)||!p.isCreative())return InteractionResultHolder.fail(held);
        if(!p.isShiftKeyDown()){p.displayClientMessage(Component.literal("右键自己的机器；潜行对空气右键，补满随身饰品、机铠及单个魔力瓶"),true);return InteractionResultHolder.success(held);}
        int amount=0;for(int i=0;i<p.getInventory().getContainerSize();i++)amount+=fillItem(p,p.getInventory().getItem(i));
        p.inventoryMenu.broadcastChanges();p.displayClientMessage(Component.literal("创造测试：随身物品补入 "+amount+"M"),true);return InteractionResultHolder.success(held);
    }
    @Override public boolean isFoil(ItemStack stack){return true;}
    @Override public void appendHoverText(ItemStack stack,Level level,java.util.List<Component> out,TooltipFlag flag){out.add(Component.literal("仅限创造测试 · 右键自己的机器充满缓冲"));out.add(Component.literal("点充能台：同时充满左一格装备；潜行对空气右键：充满随身物品"));out.add(Component.literal("无生存配方；魔力瓶须单个分开，机炮模块无需单独充能"));}
}
