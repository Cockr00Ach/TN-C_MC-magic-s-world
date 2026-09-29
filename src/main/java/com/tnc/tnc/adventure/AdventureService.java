package com.tnc.tnc.adventure;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.Config;
import com.tnc.tnc.magic.*;
import com.tnc.tnc.network.MagicStoneNetwork;
import com.tnc.tnc.npc.SkyIslandAnchors;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

public final class AdventureService {
    public static final List<ContractCatalog.Material> WAND_MATERIALS=List.of(
            new ContractCatalog.Material("minecraft:stick",false,4),new ContractCatalog.Material("minecraft:copper_ingot",false,2));
    private AdventureService() {}
    public static AdventureProfile profile(ServerPlayer player) {
        var store=AdventureSavedData.get(player.server);
        var p=store.players.get(player.getUUID());
        if(p!=null)return p;
        p=new AdventureProfile();
        var magic=MagicStone.getOrNull(player);
        if(magic!=null&&magic.isInitialized()) {
            int manaLevel=Config.manaPerVanillaLevel>0?(int)Math.ceil(Math.max(0,magic.getMaxMana()-magic.affinitySum()*Config.manaPerAffinity-Config.flatManaBonus)/(double)Config.manaPerVanillaLevel)+1:1;
            int level=Math.min(100,Math.max(player.experienceLevel+1,manaLevel));
            p.xp=AdventureRules.xpAtLevel(level);
            p.learningBase=Math.max(4,magic.getPointsTotal(Config.pointThresholds)-level/10*5);
            p.milestones.add("legacy_migrated");
        }
        store.players.put(player.getUUID(),p);store.setDirty();return p;
    }
    public static void refreshGrowth(ServerPlayer player) {
        var p=profile(player);var magic=MagicStone.getOrNull(player);
        if(magic!=null) {magic.setAdventurePoints(p.learningPoints());MagicStone.refreshMaxMana(player,magic);MagicStoneNetwork.syncTo(player);}
    }
    public static void milestone(ServerPlayer player,String id) {
        var p=profile(player);
        if(p.milestones.add(id))AdventureSavedData.get(player.server).setDirty();
        var advancement=player.server.getAdvancements().getAdvancement(ResourceLocation.fromNamespaceAndPath("tnc","onboarding/"+id));
        if(advancement!=null)for(String criterion:player.getAdvancements().getOrStartProgress(advancement).getRemainingCriteria())player.getAdvancements().award(advancement,criterion);
    }
    public static BlockPos tavern(ServerPlayer player) {
        var origin=SkyIslandAnchors.resolve(player.server.overworld(),SkyIslandAnchors.Anchor.ORIGIN);
        return origin==null?null:origin.offset(419,90,291);
    }
    public static boolean atService(ServerPlayer player) {
        if(player.isSpectator()||!player.isAlive()||com.tnc.tnc.combat.DownedCombat.isDowned(player))return false;
        if(player.level()==player.server.overworld()) {
            var pos=tavern(player);
            if(pos!=null&&SkyIslandAnchors.isComplete(player.server.overworld())&&pos.closerToCenterThan(player.position(),20))return true;
        }
        return !player.serverLevel().getEntitiesOfClass(com.tnc.tnc.npc.SelfNpcEntity.class,new AABB(player.blockPosition()).inflate(12),e->e.isAlive()).isEmpty();
    }
    public static String register(ServerPlayer player) {
        var p=profile(player);if(p.registered)return "你已经登记过，无需再次登记。";
        p.registered=true;milestone(player,"registered");return "登记成功：初级冒险者。先接教学备料单，再请匠人打造法杖。";
    }
    public static String deliver(ServerPlayer player,String id) {
        var p=profile(player);var progress=p.contracts.get(id);var c=ContractCatalog.find(id);
        if(progress==null||c==null)return "没有这份进行中的委托。";
        if(progress.kills<c.kills())return "讨伐目标尚未完成。";
        if(!p.canCredit(c.coins()))return "余额已达上限，请先支出再结算。";
        var tx=new InventoryTransaction(player.getInventory());
        if(!tx.take(c.materials(),true))return "材料不足，或返还容器没有空位。物品和委托均已保留。";
        tx.commit();p.contracts.remove(id);
        long epoch=AdventureSavedData.get(player.server).activeTicks/AdventureRules.BOARD_PERIOD;
        p.epoch(epoch); // Pay an accepted old offer once; block its current equivalent as well.
        p.paidOffers.add(id);p.credit(c.coins(),c.title());p.addXp(c.xp());
        p.reputation=Math.min(1_000_000,p.reputation+AdventureRules.reputation(c.reputation(),p.rank,c.grade()));
        milestone(player,"first_contract");if(c.teaching())milestone(player,"teaching_paid");refreshGrowth(player);
        if(id.equals("wheat"))com.tnc.tnc.life.FarmMagic.learn(player);
        return "委托完成：+"+c.xp()+"冒险经验，+"+AdventureRules.reputation(c.reputation(),p.rank,c.grade())+"声望，+"+c.coins()+"铜。";
    }
    public static String order(ServerPlayer player) {
        var p=profile(player);if(!p.registered)return "请先登记冒险者。";
        if(p.smithReady>=0)return "已有制作中的订单，请先领取。";
        long fee=p.crafted?30:0;
        if(p.coins<fee)return "人工费不足：补造30铜，第一次教学免人工费。";
        var tx=new InventoryTransaction(player.getInventory());if(!tx.take(WAND_MATERIALS,false))return "需要4木棍与2铜锭（初版材料表）。";
        tx.commit();if(fee>0)p.debit(fee,"法杖打造");
        p.smithFree=!p.crafted;p.smithReady=AdventureSavedData.get(player.server).activeTicks+400;
        milestone(player,"ordered");return "材料已交给驻馆匠人，20秒后可领取。退出不会丢失订单。";
    }
    public static String claim(ServerPlayer player) {
        var p=profile(player);long now=AdventureSavedData.get(player.server).activeTicks;
        if(p.smithReady<0)return "没有待领取订单。";
        if(now<p.smithReady)return "匠人仍在制作，请稍候。";
        var wand=new ItemStack(TNMod.WAND.get());
        wand.getOrCreateTag().putBoolean("TncForged",true);
        var tx=new InventoryTransaction(player.getInventory());if(!tx.add(wand))return "背包已满。完成订单已保留，请腾出一个位置。";
        tx.commit();p.smithReady=-1;p.crafted=true;p.smithFree=false;
        var magic=MagicStone.getOrNull(player);
        if(magic!=null)com.tnc.tnc.magic.compat.SpellEngineBridge.ensureWand(player,SpellCatalog.effectiveIds(magic));
        milestone(player,"forged");return "已领取法杖。按V学习一级法术，再在野外练习施放。";
    }
    public static String promote(ServerPlayer player) {
        var p=profile(player);if(!p.registered)return "请先登记。";
        if(p.rank>=1)return "精锐以上晋升和试炼尚未开放。";
        if(p.level()<10||p.reputation<100)return "精锐晋升需要Lv10与100声望。";
        p.rank=1;milestone(player,"promoted");return "晋升精锐成功。首版开放E/D委托，C以上远征后续扩展。";
    }
    public static void action(ServerPlayer player,AdventurePackets.Action action,String id) {
        if(action==AdventurePackets.Action.REQUEST){sync(player,false,"");return;}
        if(!atService(player)&&!(action==AdventurePackets.Action.BUY_HOME&&com.tnc.tnc.home.HousingService.atSale(player))){sync(player,false,"请到酒馆驻馆服务处办理；买房也可在南街空屋入口确认。");return;}
        String message=switch(action) {
            case REGISTER -> register(player);
            case ACCEPT -> {
                var c=ContractCatalog.find(id);
                yield c!=null&&profile(player).accept(c,AdventureSavedData.get(player.server).activeTicks/AdventureRules.BOARD_PERIOD)?"已接取："+c.title():"当前不能接取（未登记、已结算、已有同单、达到3单/越阶上限）。";
            }
            case DELIVER -> deliver(player,id);
            case ORDER -> order(player);
            case CLAIM -> claim(player);
            case PROMOTE -> promote(player);
            case BUY_HOME -> com.tnc.tnc.home.HousingService.buy(player);
            default -> "未知请求。";
        };
        if(action==AdventurePackets.Action.ACCEPT)milestoneIfPresent(player,"accepted");
        AdventureSavedData.get(player.server).setDirty();sync(player,false,message);
    }
    public static void milestoneIfPresent(ServerPlayer player,String id){if(profile(player).hasMilestone(id))milestone(player,id);}
    public static void sync(ServerPlayer player,boolean open,String message) {
        var store=AdventureSavedData.get(player.server);var p=profile(player);
        var tag=AdventureSavedData.writeProfile(p);tag.putLong("ActiveTicks",store.activeTicks);tag.putBoolean("AtService",atService(player));tag.putString("Message",message);
        tag.put("Housing",com.tnc.tnc.home.HousingService.snapshot(player));
        tag.putBoolean("AtHomeSale",com.tnc.tnc.home.HousingService.atSale(player));
        var pos=tavern(player);if(pos!=null)tag.putString("Tavern",pos.getX()+" / "+pos.getY()+" / "+pos.getZ());
        var counts=new CompoundTag();for(var c:ContractCatalog.ALL) {
            int have=c.kills()>0?(p.contracts.containsKey(c.id())?p.contracts.get(c.id()).kills:0):c.materials().stream().mapToInt(m->Math.min(InventoryTransaction.count(player.getInventory(),m),m.count())).sum();
            counts.putInt(c.id(),have);
        }
        tag.put("Counts",counts);MagicStoneNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new AdventurePackets.Snapshot(tag,open));
    }
}
