package com.tnc.tnc.dungeon;

import com.tnc.tnc.adventure.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import java.util.*;

public final class DungeonRewards {
    public static Item material(DungeonTheme t){return DungeonContent.ITEMS.get(switch(t){case DESERT->"sunwheel_shard";case CRYPT->"old_bell_bronze";case TIDE->"tidal_shell_crystal";case ASTRAL->"star_dream_shard";});}
    public static CompoundTag claimRecord(DungeonData.Floor f,int room){String key=Integer.toString(room);if(!f.claims.contains(key,10))f.claims.put(key,new CompoundTag());return f.claims.getCompound(key);}
    public static boolean claim(ServerPlayer p,DungeonData.Run r,DungeonGraph.Room room){
      var record=claimRecord(r.state(),room.id());String id=p.getUUID().toString();if(record.getBoolean(id)){DungeonService.message(p,"这一房的奖励已经领取");return false;}
      var inventory=new InventoryTransaction(p.getInventory());var rng=new Random(r.seed+r.floor*13L+room.id()*97L+p.getUUID().getLeastSignificantBits());boolean challenge=r.state().events.getBoolean("Challenge"+room.id());var mat=new ItemStack(material(r.theme),1+rng.nextInt(3)+(challenge?3:0));
      var food=new ItemStack(rng.nextBoolean()?Items.COOKED_BEEF:Items.BAKED_POTATO,2);if(!inventory.add(mat)||!inventory.add(food)){DungeonService.message(p,"请腾出背包空间；奖励仍保留");return false;}
      boolean memento=rng.nextInt(5)==0;if(memento){var trophy=new ItemStack(DungeonContent.ITEMS.get("dungeon_memento"));trophy.getOrCreateTag().putString("DungeonTheme",r.theme.id);trophy.setHoverName(net.minecraft.network.chat.Component.literal(r.theme.name+" · "+new String[]{"旅途石板","微型门环","古旧纹章","守关纪念"}[rng.nextInt(4)]));if(!inventory.add(trophy)){DungeonService.message(p,"请腾出背包空间");return false;}}
      record.putBoolean(id,true);inventory.commit();var d=DungeonData.get(p.server);var profile=d.profile(p.getUUID());profile.putLong("Pending",Math.min(1_000_000,profile.getLong("Pending")+6));d.setDirty();DungeonService.message(p,"宝箱已领取 · 主题材料、补给与6遗币");return true;
    }
    /** Persist entitlement for every participant, including players who disconnected during the fight. */
    public static void checkpoint(net.minecraft.server.MinecraftServer server,UUID id,DungeonData.Run r){
      var d=DungeonData.get(server);var profile=d.profile(id);String key="Paid_"+r.theme.id+"_"+r.floor;boolean first=!profile.getBoolean(key);profile.putBoolean(key,true);
      long copper=first?200+r.floor*10L:30+r.floor;if(!profile.contains("Reports",10))profile.put("Reports",new CompoundTag());var reports=profile.getCompound("Reports");String report=r.theme.id+"_"+r.floor;reports.putLong(report,Math.min(AdventureRules.MAX_COINS,reports.getLong(report)+copper));
      if(r.floor==30){String mail="Trophy_"+r.theme.id;profile.putInt(mail,Math.min(1000,profile.getInt(mail)+1));}profile.remove("Blessing");d.setDirty();
      var p=server.getPlayerList().getPlayer(id);if(p!=null){deliverMail(p);p.sendSystemMessage(net.minecraft.network.chat.Component.literal("勘察记录已保存，回酒馆委托栏潜行右键领取"+copper+"铜"));}
    }
    public static void deliverMail(ServerPlayer p){com.tnc.tnc.dungeon.leyline.LeylineRewards.deliver(p);var d=DungeonData.get(p.server);var profile=d.profile(p.getUUID());
      if(profile.getBoolean("Entered"))DungeonTavern.award(p,"entered");
      for(var theme:DungeonTheme.values()){for(int floor=5;floor<=30;floor+=5)if(profile.getBoolean("Paid_"+theme.id+"_"+floor))DungeonTavern.award(p,theme.id+"_"+floor);
        String key="Trophy_"+theme.id;int count=profile.getInt(key);while(count>0){var item=new ItemStack(DungeonContent.ITEMS.get("dungeon_memento"));item.setHoverName(net.minecraft.network.chat.Component.literal(theme.name+" · 三十层通关纪念"));var tx=new InventoryTransaction(p.getInventory());if(!tx.add(item))break;tx.commit();profile.putInt(key,--count);d.setDirty();}
      }
    }
    public static void bless(ServerPlayer p,int choice){var r=DungeonService.run(p);var room=DungeonService.room(p);if(r==null||room==null||choice<1||choice>3)return;var claim=claimRecord(r.state(),room.id());String key="bless:"+p.getUUID();if(claim.getBoolean(key))return;claim.putBoolean(key,true);var d=DungeonData.get(p.server);d.profile(p.getUUID()).putInt("Blessing",choice);d.setDirty();DungeonService.message(p,new String[]{"","回声甘露：清房后恢复5点个人法力","归途护灯：每个新战斗房抵挡一次伤害的50%","轻风足迹：本段获得轻微移速提升"}[choice]);}
    public static void buy(ServerPlayer p,int choice){var r=DungeonService.run(p);var room=DungeonService.room(p);if(r==null||room==null)return;var d=DungeonData.get(p.server);var t=d.profile(p.getUUID());int price=choice==0?8:choice==1?12:20;if(choice<0||choice>2||t.getLong("Pending")+t.getLong("Saved")<price){DungeonService.message(p,"遗币不足");return;}
      var claim=claimRecord(r.state(),room.id());String key="buy:"+p.getUUID()+":"+choice;if(claim.getBoolean(key)){DungeonService.message(p,"这件商品本次已售出");return;}var item=choice==0?new ItemStack(Items.COOKED_BEEF,4):choice==1?new ItemStack(material(r.theme),4):new ItemStack(DungeonContent.ITEMS.get("dungeon_memento"));var tx=new InventoryTransaction(p.getInventory());if(!tx.add(item)){DungeonService.message(p,"请腾出背包空间");return;}long use=Math.min(price,t.getLong("Pending"));t.putLong("Pending",t.getLong("Pending")-use);t.putLong("Saved",t.getLong("Saved")-(price-use));claim.putBoolean(key,true);tx.commit();d.setDirty();}
}
