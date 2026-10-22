package com.tnc.tnc.life.routes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.chat.Component;
public final class RouteMeterItem extends Item {
    public RouteMeterItem(){super(new Properties().stacksTo(1));}
    @Override public InteractionResult useOn(UseOnContext c){if(c.getLevel().getBlockEntity(c.getClickedPos()) instanceof RouteNodeEntity node){if(c.getPlayer() instanceof net.minecraft.server.level.ServerPlayer p&&p.isShiftKeyDown()&&RouteMirrorLinks.mirror(node)){
            if(!node.mayUse(p))return InteractionResult.FAIL;var tag=c.getItemInHand().getOrCreateTag();String dimension=p.level().dimension().location().toString();
            if(!tag.contains("MirrorBindPos")||dimension.equals(tag.getString("MirrorBindDimension"))&&node.getBlockPos().asLong()==tag.getLong("MirrorBindPos")){tag.putLong("MirrorBindPos",node.getBlockPos().asLong());tag.putString("MirrorBindDimension",dimension);com.tnc.tnc.arcana.ArcanaRealms.pin(p.serverLevel(),node);p.displayClientMessage(Component.literal("已选择第一面镜子；潜行右键第二面完成配对"),true);}
            else{String error=dimension.equals(tag.getString("MirrorBindDimension"))?RouteMirrorLinks.link(p.serverLevel(),net.minecraft.core.BlockPos.of(tag.getLong("MirrorBindPos")),node.getBlockPos(),p.getUUID()):com.tnc.tnc.arcana.ArcanaRealms.link(p,net.minecraft.core.GlobalPos.of(com.tnc.tnc.arcana.ArcanaRealms.dimension(tag.getString("MirrorBindDimension")),net.minecraft.core.BlockPos.of(tag.getLong("MirrorBindPos"))),net.minecraft.core.GlobalPos.of(p.level().dimension(),node.getBlockPos()));p.displayClientMessage(Component.literal(error.isEmpty()?"两面镜子已配对，镜面自动朝向目标":error),true);if(error.isEmpty()){tag.remove("MirrorBindPos");tag.remove("MirrorBindDimension");}}
            return InteractionResult.SUCCESS;
        }if(c.getPlayer()!=null&&!c.getLevel().isClientSide)c.getPlayer().displayClientMessage(Component.literal(node.kind().name+" · 储量 "+node.mana+"/"+node.kind().capacity+" · 进 "+node.inRate+" / 出 "+node.outRate+" M/s · 带宽 "+node.kind().rate+" · "+node.status),false);return InteractionResult.SUCCESS;}return InteractionResult.PASS;}
}
