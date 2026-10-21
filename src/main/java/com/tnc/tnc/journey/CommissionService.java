package com.tnc.tnc.journey;
import com.tnc.tnc.adventure.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
public final class CommissionService {
    private record Board(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,net.minecraft.core.BlockPos pos,long until){}
    private static final java.util.Map<java.util.UUID,Board> BOARDS=new java.util.HashMap<>();
    public static boolean openAt(ServerPlayer p,net.minecraft.core.BlockPos pos){if(!p.isAlive()||p.isSpectator()||com.tnc.tnc.combat.DownedCombat.isDowned(p)||!p.serverLevel().hasChunkAt(pos)||pos.distToCenterSqr(p.position())>64||!validBoard(p,pos))return false;BOARDS.put(p.getUUID(),new Board(p.serverLevel().dimension(),pos.immutable(),p.server.overworld().getGameTime()+2400));open(p);return true;}
    public static boolean atBoard(ServerPlayer p){var b=BOARDS.get(p.getUUID());if(b!=null&&b.until>=p.server.overworld().getGameTime()&&p.serverLevel().dimension()==b.dimension&&p.serverLevel().hasChunkAt(b.pos)&&b.pos.distToCenterSqr(p.position())<=64&&validBoard(p,b.pos)&&p.isAlive()&&!p.isSpectator()&&!com.tnc.tnc.combat.DownedCombat.isDowned(p))return true;return false;}
    private static boolean validBoard(ServerPlayer p,net.minecraft.core.BlockPos pos){return p.serverLevel().getBlockState(pos).is(com.tnc.tnc.alchemy.AlchemyContent.BOARD)||TownServices.isTownBoard(p.serverLevel(),pos);}
    public static CompoundTag ledger(ServerPlayer p){var t=JourneyData.get(p.server).player(p.getUUID());if(!t.contains("Commissions",10))t.put("Commissions",new CompoundTag());return t.getCompound("Commissions");}
    public static void open(ServerPlayer p){AdventureService.milestone(p,"visited_board");JourneyNetwork.open(p,"board",snapshot(p));}
    public static CompoundTag snapshot(ServerPlayer p){var result=new CompoundTag();var cards=new ListTag();var ledger=ledger(p);long now=System.currentTimeMillis()/1000;
        for(var o:CommissionCatalog.all()){
            var c=ledger.getCompound(o.id());var t=new CompoundTag();t.putString("Id",o.id());t.putString("Title",o.title());t.putString("Story",o.story());t.putString("Kind",o.kind());t.putString("Target",o.target());t.putString("Goal",(o.kind().equals("kill")?"讨伐 ":"通过楼层 · ")+o.target());t.putInt("Count",c.getBoolean("Active")?c.getInt("Goal"):o.count());t.putInt("Progress",c.getInt("Progress"));t.putInt("Level",o.level());t.putInt("Coins",o.coins());t.putInt("XP",o.xp());t.putBoolean("Active",c.getBoolean("Active"));t.putLong("Wait",Math.max(0,c.getLong("PaidAt")+o.cooldown()-now));cards.add(t);
        }for(String id:ledger.getAllKeys()){var old=ledger.getCompound(id);if(!old.getBoolean("Active")||CommissionCatalog.find(id)!=null)continue;var c=new CompoundTag();c.putString("Id",id);c.putString("Title",old.getString("Title").isBlank()?id:old.getString("Title"));c.putString("Story","此委托已下架。可撤销记录，释放接单名额。");c.putInt("Count",old.getInt("Goal"));c.putInt("Progress",old.getInt("Progress"));c.putBoolean("Active",true);c.putBoolean("Unavailable",true);cards.add(c);}result.put("Cards",cards);result.putInt("PlayerLevel",AdventureService.profile(p).level());result.putBoolean("Registered",AdventureService.profile(p).registered());result.putLong("Coins",AdventureService.profile(p).coins());return result;
    }
    public static void act(ServerPlayer p,String action,String id){
        if(!atBoard(p)){message(p,"请回到委托栏旁办理；离开或拆除委托栏后不能远程接单。");return;}
        if(action.equals("archive")){BossCommissions.visitBoard(p);com.tnc.tnc.dungeon.DungeonTavern.visit(p);return;}
        if(action.equals("abandon")){if(!ledger(p).contains(id,10)||!ledger(p).getCompound(id).getBoolean("Active"))return;var r=ledger(p).getCompound(id);r.putBoolean("Active",false);r.putInt("Progress",0);ledger(p).put(id,r);JourneyData.get(p.server).setDirty();open(p);return;}
        var o=CommissionCatalog.find(id);if(o==null)return;var profile=AdventureService.profile(p);if(!profile.registered()){message(p,"先与艾琳办理冒险者登记。");return;}var ledger=ledger(p);var record=ledger.getCompound(id);long now=System.currentTimeMillis()/1000;
        if(action.equals("accept")){
            long count=ledger.getAllKeys().stream().filter(k->ledger.getCompound(k).getBoolean("Active")).count();
            if(record.getBoolean("Active")||count>=3||profile.level()<o.level()||record.getLong("PaidAt")+o.cooldown()>now){message(p,"最多同时接3份委托；请检查等级和冷却。");return;}
            record.putBoolean("Active",true);record.putString("Title",o.title());record.putInt("Progress",0);record.putInt("Goal",o.count());record.putString("Kind",o.kind());record.putString("Target",o.target());ledger.put(id,record);AdventureService.milestone(p,"accepted");FamilyLife.notice(p,"depart");message(p,"已接取「"+o.title()+"」；进度自动记录，无需委托纸。");
        }else if(action.equals("claim")){
            if(!record.getBoolean("Active")||record.getInt("Progress")<record.getInt("Goal"))return;
            if(profile.coins()>AdventureRules.MAX_COINS-o.coins()){message(p,"钱袋已满，请先存入银行。");return;}
            record.putBoolean("Active",false);record.putLong("PaidAt",now);ledger.put(id,record);profile.credit(o.coins(),"酒馆委托 · "+o.title());profile.addXp(o.xp());profile.recordCommission();AdventureSavedData.get(p.server).setDirty();AdventureService.refreshGrowth(p);AdventureService.milestone(p,"first_contract");for(int n:new int[]{1,5,10,25,50,100})if(profile.commissions()>=n)AdventureService.milestone(p,"bounties_"+n);FamilyLife.notice(p,"success");message(p,"委托完成：+"+o.coins()+"铜、+"+o.xp()+"经验。");
        }else if(action.equals("abandon")){record.putBoolean("Active",false);record.putInt("Progress",0);ledger.put(id,record);}
        JourneyData.get(p.server).setDirty();open(p);
    }
    public static void progress(ServerPlayer p,String kind,String target){var ledger=ledger(p);boolean changed=false;for(String id:ledger.getAllKeys()){
        var r=ledger.getCompound(id);if(!r.getBoolean("Active")||!r.getString("Kind").equals(kind)||!(r.getString("Target").equals(target)||r.getString("Target").equals("*"))||r.getInt("Progress")>=r.getInt("Goal"))continue;
        r.putInt("Progress",r.getInt("Progress")+1);changed=true;if(r.getInt("Progress")==r.getInt("Goal")){message(p,"一份委托已达成，回酒馆委托栏领取报酬。");FamilyLife.notice(p,"success");}
    }if(changed)JourneyData.get(p.server).setDirty();}
    public static void message(ServerPlayer p,String text){p.sendSystemMessage(Component.literal(text));}
}
