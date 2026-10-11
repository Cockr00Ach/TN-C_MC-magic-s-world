package com.tnc.tnc.life.routes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.chat.Component;
public final class RouteMeterItem extends Item {
    public RouteMeterItem(){super(new Properties().stacksTo(1));}
    @Override public InteractionResult useOn(UseOnContext c){if(c.getLevel().getBlockEntity(c.getClickedPos()) instanceof RouteNodeEntity node){if(c.getPlayer()!=null&&!c.getLevel().isClientSide)c.getPlayer().displayClientMessage(Component.literal(node.kind().name+" · 储量 "+node.mana+"/"+node.kind().capacity+" · 进 "+node.inRate+" / 出 "+node.outRate+" M/s · 带宽 "+node.kind().rate+" · "+node.status),false);return InteractionResult.SUCCESS;}return InteractionResult.PASS;}
}
