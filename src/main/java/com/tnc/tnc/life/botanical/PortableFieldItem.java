package com.tnc.tnc.life.botanical;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;
/** A real held-item inventory; archived samples keep their complete signed NBT. */
public final class PortableFieldItem extends Item {
    public final boolean archive;
    public PortableFieldItem(boolean archive){super(new Properties().stacksTo(1));this.archive=archive;}
    @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand hand){var held=p.getItemInHand(hand);if(p instanceof ServerPlayer server){var tag=held.getOrCreateTag();if(!tag.hasUUID("FieldContainer"))tag.putUUID("FieldContainer",UUID.randomUUID());int slot=hand==InteractionHand.MAIN_HAND?p.getInventory().selected:40;NetworkHooks.openScreen(server,new SimpleMenuProvider((id,inv,player)->new PortableFieldMenu(id,inv,archive,slot),Component.literal(archive?"调查档案夹 · 十二份署名页":"防雨种匣 · 四种种源")),buf->{buf.writeBoolean(archive);buf.writeVarInt(slot);});}return InteractionResultHolder.sidedSuccess(held,l.isClientSide);}
    @Override public void appendHoverText(ItemStack stack,Level l,List<Component> text,TooltipFlag f){text.add(Component.literal(archive?"12个真实槽，每槽一张署名调查页；纹样、坐标和采样人完整保留。":"4个真实槽，每种最多16粒；防雨荚壳做的轻便野外种匣。"));text.add(Component.literal("右键打开。容器打开时持有槽锁定；不能放入另一只容器。"));}
}
