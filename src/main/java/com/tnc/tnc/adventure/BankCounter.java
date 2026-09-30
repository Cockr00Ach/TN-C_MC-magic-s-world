package com.tnc.tnc.adventure;
import com.tnc.tnc.TNMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** Physical coin exchange uses the wallet; interest-bearing savings live in BankAccount. */
public final class BankCounter {
    public static String deposit(ServerPlayer p){
        var mats=new ArrayList<ContractCatalog.Material>();long total=0;String[] ids={"copper_coin","silver_coin","gold_coin"};int[] values={1,100,10000};
        for(int i=0;i<3;i++){int n=InventoryTransaction.count(p.getInventory(),new ContractCatalog.Material("tnc:"+ids[i],false,1));mats.add(new ContractCatalog.Material("tnc:"+ids[i],false,n));total+=(long)n*values[i];}
        var a=AdventureService.profile(p);if(total==0)return "背包里没有可存的实体币。";if(!a.canCredit(total))return "账户余额上限不足，实体币保留。";
        var tx=new InventoryTransaction(p.getInventory());if(!tx.take(mats,false))return "物品已变化，请重新办理。";
        tx.commit();a.credit(total,"归航银行兑币");AdventureService.milestone(p,"bank_deposit");return "已兑换"+total+"铜到钱袋；再点击存入，开始存款计息。";
    }
    public static String withdraw(ServerPlayer p,String denomination){
        int value=switch(denomination){case "copper"->1;case "silver"->100;case "gold"->10000;default->0;};if(value==0)return "无效币种。";
        var item=value==1?TNMod.COPPER_COIN.get():value==100?TNMod.SILVER_COIN.get():TNMod.GOLD_COIN.get();var a=AdventureService.profile(p);var tx=new InventoryTransaction(p.getInventory());
        if(a.coins()<value||!tx.add(new ItemStack(item)))return "余额不足或背包已满，未扣款。";
        tx.commit();a.debit(value,"归航银行取币");AdventureSavedData.get(p.server).setDirty();return "已取出一枚实体币。";
    }
}
