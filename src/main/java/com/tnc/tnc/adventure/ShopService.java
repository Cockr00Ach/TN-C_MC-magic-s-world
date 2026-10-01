package com.tnc.tnc.adventure;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/** Shop plans inventory and money changes before either side is committed. */
public final class ShopService {
    public static final long PERIOD=48000;
    private static CompoundTag account(ServerPlayer p){var store=AdventureSavedData.get(p.server);if(!store.housing.contains("Market",10))store.housing.put("Market",new CompoundTag());var market=store.housing.getCompound("Market");String key=p.getUUID().toString();if(!market.contains(key,10))market.put(key,new CompoundTag());var t=market.getCompound(key);long period=store.activeTicks/PERIOD;if(t.getLong("Period")!=period){t.putLong("Period",period);t.putLong("Sold",0);store.setDirty();}return t;}
    public static boolean plain(ItemStack s){return !s.isEmpty()&&!s.hasTag()&&!s.isDamaged();}
    public static String buy(ServerPlayer p,String id){
        var goods=ShopCatalog.find(ShopCatalog.FURNITURE,id);if(goods==null)return "没有这件商品。";BankService.settle(p);var a=AdventureService.profile(p);
        var offered=FurnitureCompat.stack(goods);if(offered.isEmpty())return "商品模组未加载，未扣款。";var tx=new InventoryTransaction(p.getInventory());if(!tx.add(offered))return "背包没有空间，未扣款。";
        if(!a.bank.spend(a,goods.price(),"朝夕商行·"+goods.name()))return "钱袋和存款合计不足，未扣款。";tx.commit();AdventureSavedData.get(p.server).setDirty();AdventureService.milestone(p,"furniture_bought");return "已购买"+goods.name()+"，支付"+goods.price()+"铜。";
    }
    public static String sell(ServerPlayer p,String id){
        var goods=ShopCatalog.find(ShopCatalog.PRODUCE,id);if(goods==null)return "没有这项收购。";BankService.settle(p);var a=AdventureService.profile(p);var t=account(p);
        if(t.getLong("Sold")+goods.price()>ShopCatalog.quota(a.level()))return "本轮收购额度已用完；40分钟世界有效在线时间后恢复。";
        if(!a.canCredit(goods.price()))return "余额已达上限，物品保留。";
        var item=ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(goods.item()));if(item==null||item==Items.AIR)return "这项作物模组未加载。";
        var tx=new InventoryTransaction(p.getInventory());int left=goods.count();for(int i=0;i<36&&left>0;i++){var s=p.getInventory().getItem(i);if(!s.is(item)||!plain(s))continue;int n=Math.min(left,s.getCount());if(!tx.takeFromSlot(i,n))return "物品变化，未收购。";left-=n;}
        if(left>0)return "需要"+goods.count()+"个普通"+goods.name().split(" ×")[0]+"；命名、附魔或带特殊数据的物品不会收走。";
        if(id.equals("milk")&&!tx.add(new ItemStack(Items.BUCKET)))return "请为返还空桶腾出位置，未收购。";
        if(id.equals("honey")&&!tx.add(new ItemStack(Items.GLASS_BOTTLE,4)))return "请为返还玻璃瓶腾出位置，未收购。";
        tx.commit();a.credit(goods.price(),"朝夕收购·"+goods.name());t.putLong("Sold",t.getLong("Sold")+goods.price());AdventureSavedData.get(p.server).setDirty();AdventureService.milestone(p,"produce_sold");return "已售出"+goods.name()+"，收入"+goods.price()+"铜。";
    }
    public static CompoundTag snapshot(ServerPlayer p){var t=account(p).copy();t.putInt("Quota",ShopCatalog.quota(AdventureService.profile(p).level()));t.putLong("Next",PERIOD-AdventureSavedData.get(p.server).activeTicks%PERIOD);var available=new ListTag();for(var g:ShopCatalog.FURNITURE){var item=g.item().startsWith("model:")?"immersive_furniture:furniture":g.item();var found=ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(item));if(found!=null&&found!=Items.AIR)available.add(StringTag.valueOf(g.id()));}t.put("Available",available);return t;}
}
