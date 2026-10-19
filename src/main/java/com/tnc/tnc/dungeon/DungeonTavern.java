package com.tnc.tnc.dungeon;
import com.tnc.tnc.adventure.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
public final class DungeonTavern {
    public static void award(ServerPlayer p,String key){var a=p.server.getAdvancements().getAdvancement(DungeonContent.id("dungeon/"+key));if(a!=null)p.getAdvancements().award(a,"done");}
    public static void visit(ServerPlayer p){if(!TownServices.atBoard(p)||!AdventureService.profile(p).registered())return;DungeonRewards.deliverMail(p);var d=DungeonData.get(p.server);var profile=d.profile(p.getUUID());
      p.sendSystemMessage(Component.literal("◆ 门后的三十层 · 四座野外界门勘察"));for(var theme:DungeonTheme.values())p.sendSystemMessage(Component.literal("[寻找"+theme.name+"大门] ").withStyle(s->s.withColor(net.minecraft.ChatFormatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/dungeon locate "+theme.id))));
      if(!profile.getBoolean("Briefed")){var tx=new InventoryTransaction(p.getInventory());if(tx.add(new ItemStack(DungeonContent.ITEMS.get("dungeon_handbook")))){tx.commit();profile.putBoolean("Briefed",true);}}
      var reports=profile.getCompound("Reports");long sum=0;for(String key:reports.getAllKeys())sum=Math.min(AdventureRules.MAX_COINS,sum+Math.max(0,reports.getLong(key)));if(sum>0){var account=AdventureService.profile(p);long paid=Math.min(sum,AdventureRules.MAX_COINS-account.coins());if(paid>0){account.credit(paid,"地下城勘察报告");long remaining=paid;for(String key:new java.util.TreeSet<>(reports.getAllKeys())){long take=Math.min(reports.getLong(key),remaining);reports.putLong(key,reports.getLong(key)-take);remaining-=take;if(remaining==0)break;}AdventureSavedData.get(p.server).setDirty();p.sendSystemMessage(Component.literal("✓ 勘察记录已交付：+"+paid+"铜。遗币是地下城兑换币，不与银行混用。"));}}
      d.setDirty();
    }
}
