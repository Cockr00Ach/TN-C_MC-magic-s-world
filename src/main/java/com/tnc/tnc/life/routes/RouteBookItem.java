package com.tnc.tnc.life.routes;
import net.minecraft.world.item.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
public final class RouteBookItem extends Item {
    private final String id;public RouteBookItem(String id){super(new Properties().stacksTo(1));this.id=id;}
    @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand hand){if(!l.isClientSide){var book=new ItemStack(Items.WRITTEN_BOOK);var tag=book.getOrCreateTag();tag.putString("title",switch(id){case "season_handbook"->"四时厨房与农事手册";case "magic_garden_book"->"魔植花园图鉴";case "beast_ranch_book"->"异兽牧养册";default->"魔导工坊手册";});tag.putString("author","RouchNao 小镇");var pages=RouteBooks.pages(id);for(int from=0;from<pages.size();from+=90){var volume=book.copy();var list=new net.minecraft.nbt.ListTag();for(String page:pages.subList(from,Math.min(pages.size(),from+90)))list.add(net.minecraft.nbt.StringTag.valueOf(net.minecraft.network.chat.Component.Serializer.toJson(net.minecraft.network.chat.Component.literal(page))));volume.getOrCreateTag().put("pages",list);if(pages.size()>90)volume.getOrCreateTag().putString("title",tag.getString("title")+" · "+(from/90+1));if(from==0){p.setItemInHand(hand,volume);if(p instanceof net.minecraft.server.level.ServerPlayer server)server.openItemGui(volume,hand);}else if(!p.getInventory().add(volume))p.drop(volume,false);}}return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand),l.isClientSide);}
}
