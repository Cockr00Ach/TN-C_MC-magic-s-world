package com.tnc.tnc.life.pasture;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;

/** Vanilla readable one-page UI, regenerated from the actual inspected animal. */
public final class PastureBookItem extends WrittenBookItem {
    public PastureBookItem(){super(new Item.Properties().stacksTo(1));}
    public void show(ServerPlayer player,ItemStack held,PastureAnimal animal,InteractionHand hand){
        String owner=animal.owner()==null?"尚未认养":animal.owner().equals(player.getUUID())?"你":animal.owner().toString();
        String consumer=switch(animal.species().role()){case MEAT->"成年屠宰获得专属肉和配料，可烹饪、出售或供远征。";case LIVE->"留养周期取产。夜蛾实际助长作物，螳螂夜间预警，邮鹭可托盘运输。";case ELEMENT->"各有真实元素资源。浮鲸用魔力瓶采魔；雷蜥充电栖架消耗100FE存80FE。";case HELPER->"手持牧务杖选中后右键工作地块。负兽/邮鹭选两个托盘；犀选田地；筑狸记已有栅栏位置。";};
        String text=animal.species().name()+"\n主人："+(owner.length()>16?owner.substring(0,8):owner)+"\n喜食："+new ItemStack(animal.species().food()).getHoverName().getString()+"\n"+animal.status()+"\n\n空手：取产／骑乘\n潜行空手：取货／跟随\n牧务杖：设窝／派工\n搬迁笼：保留库存运输";
        ListTag pages=new ListTag();pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(text))));pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(consumer+"\n\n野外即可开始，不用先买房。\n照料三次，每次隔60秒。\n\n食槽放最多四类、共64份饲料；桶装水另计16份。\n\n日程按实际运行刻，不因睡觉跳夜无限催熟。"))));
        var tag=held.getOrCreateTag();tag.put("pages",pages);tag.putString("title","牧养册 · "+animal.species().name());tag.putString("author","RouchNao 牧务研究会");tag.putBoolean("resolved",true);
        player.openItemGui(held,hand);
    }
    public void showBellwool(ServerPlayer player,ItemStack held,com.tnc.tnc.life.fauna.BellwoolSheepEntity sheep,InteractionHand hand){
        var data=new net.minecraft.nbt.CompoundTag();sheep.addAdditionalSaveData(data);
        String text="响铃羊\n\n主人："+(sheep.caretaker()==null?"未认养":sheep.caretaker().equals(player.getUUID())?"你":"其他牧养人")+"\n喜食：铃穗饲捆\n"+(sheep.isBaby()?"幼体自然成长两日":"成年")+"\n响绒："+(sheep.readyForHarvest()?"可用剪刀取2份":"剩余 "+Math.max(0,(data.getLong("BellwoolNextHarvest")-player.level().getGameTime())/20)+"秒")+"\n\n每两日活剪一次。\n食槽可供繁殖饲捆。\n响绒可做防冻旅毡。";
        ListTag pages=new ListTag();pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(text))));var tag=held.getOrCreateTag();tag.put("pages",pages);tag.putString("title","牧养册 · 响铃羊");tag.putString("author","RouchNao 牧务研究会");tag.putBoolean("resolved",true);player.openItemGui(held,hand);
    }
}
