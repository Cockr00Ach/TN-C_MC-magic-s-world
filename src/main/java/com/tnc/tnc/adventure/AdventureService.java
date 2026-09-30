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
        return TownServices.near(player,"guild");
    }
    public static String register(ServerPlayer player) {
        var p=profile(player);if(p.registered)return "你已经登记过，无需再次登记。";
        p.registered=true;BankService.settle(player);milestone(player,"registered");return "登记成功：初级冒险者。右键酒馆Bountiful委托栏接单；法器找莉娅，基础装备找铎恩。";
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
        return order(player,"water_wand_1");
    }
    public static String order(ServerPlayer player,String designId) {
        var p=profile(player);if(!p.registered)return "请先登记冒险者。";
        if(p.smithReady>=0)return "已有制作中的订单，请先领取。";
        var design=ElementWands.find(designId);var equipment=EquipmentOrders.find(designId);if(design==null&&equipment==null)return "请选择真实品种，未扣材料。旧基础杖只保留存量与旧待领奖订单。";
        if(designId.equals("equipment_leggings")||designId.equals("equipment_boots"))return "装备栏已改为帽子与全身身甲，请选择新的装备。";
        int tier=design==null?1:design.tier();int[] levels={0,1,10,25,50,85};
        int required=equipment!=null?equipment.level():levels[tier];
        if(p.level()<required)return "这一档铸造需要冒险Lv"+required+"，未扣材料。";
        long fee=equipment!=null?(!p.armorCrafted&&required==1?0:equipment.fee()):(!p.crafted&&tier==1?0:design.fee());
        if(p.coins<fee)return "人工费不足：需要"+fee+"铜。首次一阶订单免人工费。";
        var tx=new InventoryTransaction(player.getInventory());if(!tx.take(equipment!=null?equipment.materials():design.materials(),false))return "材料不足，全部材料和金币保留。";
        tx.commit();if(fee>0)p.debit(fee,equipment!=null?"铁匠制作":"法杖打造");
        int seconds=equipment!=null?equipment.seconds():design.seconds();
        p.smithDesign=designId;p.smithFree=fee==0;p.smithReady=AdventureSavedData.get(player.server).activeTicks+seconds*20L;
        milestone(player,equipment!=null?"equipment_ordered":"ordered");return "材料已交给"+(equipment!=null?"铎恩":"莉娅")+"，"+seconds+"秒后可领取。退出不会丢失订单。";
    }
    public static String claim(ServerPlayer player) {
        var p=profile(player);long now=AdventureSavedData.get(player.server).activeTicks;
        if(p.smithReady<0)return "没有待领取订单。";
        if(now<p.smithReady)return "匠人仍在制作，请稍候。";
        var design=ElementWands.find(p.smithDesign);
        var equipment=EquipmentOrders.find(p.smithDesign);
        if(design==null&&equipment==null&&!p.smithDesign.isEmpty())return "订单品种缺失，成品记录保留，请检查版本。";
        var wand=equipment!=null?new ItemStack(equipment.item()):design==null?new ItemStack(TNMod.WAND.get()):ElementWands.stack(design);
        wand.getOrCreateTag().putBoolean("TncForged",true);
        var tx=new InventoryTransaction(player.getInventory());if(!tx.add(wand))return "背包已满。完成订单已保留，请腾出一个位置。";
        tx.commit();p.smithReady=-1;if(equipment!=null)p.armorCrafted=true;else p.crafted=true;p.smithFree=false;
        var magic=MagicStone.getOrNull(player);
        if(magic!=null)com.tnc.tnc.magic.compat.SpellEngineBridge.ensureWand(player,SpellCatalog.effectiveIds(magic));
        if(design!=null)milestone(player,"forged_"+design.id());p.smithDesign="";
        if(equipment!=null&&com.tnc.tnc.equipment.MageGear.find(equipment.id())!=null)milestone(player,"gear_"+equipment.id());
        milestone(player,equipment!=null?"armored":"forged");return equipment!=null?"铁匠订单已交付；魔法帽戴头部，完整身甲穿胸甲槽。":"已领取法器。按V学习对应法术，再在野外练习施放。";
    }
    public static String promote(ServerPlayer player) {
        var p=profile(player);if(!p.registered)return "请先登记。";
        if(p.rank>=1)return "精锐以上晋升和试炼尚未开放。";
        if(p.level()<10||p.reputation<100)return "精锐晋升需要Lv10与100声望。";
        p.rank=1;milestone(player,"promoted");return "晋升精锐成功。酒馆原生栏提供补给、食材与巡猎；更高职称试炼后续建设。";
    }
    public static void action(ServerPlayer player,AdventurePackets.Action action,String id) {
        if(action==AdventurePackets.Action.REQUEST){sync(player,false,"");return;}
        boolean allowed=switch(action){case REGISTER,PROMOTE->TownServices.near(player,"guild");case ORDER->TownServices.near(player,EquipmentOrders.find(id)!=null?"armorer":"smith");case CLAIM->TownServices.near(player,EquipmentOrders.find(profile(player).smithDesign)!=null?"armorer":"smith");case BUY_HOME,BUY_HOME_INSTALLMENT,BANK_DEPOSIT,BANK_WITHDRAW,BANK_SAVE,BANK_TAKE,BANK_BORROW,BANK_CLEAR->TownServices.near(player,"broker");case ACCEPT,DELIVER,OPEN_BOARD->TownServices.atBoard(player);default->false;};
        if(!allowed){sync(player,false,"请到对应岗位办理：艾琳登记，酒馆栏交委托，莉娅制杖，铎恩制作装备，米洛办理银行与购房。");return;}
        String message=switch(action) {
            case REGISTER -> register(player);
            case ACCEPT -> {
                var c=ContractCatalog.find(id);
                yield c!=null&&c.teaching()&&profile(player).accept(c,AdventureSavedData.get(player.server).activeTicks/AdventureRules.BOARD_PERIOD)?"已接取："+c.title():"常规新委托请使用Bountiful原生栏；旧已接单仍可归档交付。";
            }
            case DELIVER -> deliver(player,id);
            case ORDER -> order(player,id);
            case CLAIM -> claim(player);
            case PROMOTE -> promote(player);
            case BUY_HOME -> com.tnc.tnc.home.HousingService.buy(player);
            case BUY_HOME_INSTALLMENT -> com.tnc.tnc.home.HousingService.buy(player,true);
            case BANK_SAVE -> BankService.save(player,id);
            case BANK_TAKE -> BankService.take(player,id);
            case BANK_BORROW -> BankService.borrow(player,id);
            case BANK_CLEAR -> BankService.clear(player,id);
            case BANK_DEPOSIT -> BankCounter.deposit(player);
            case BANK_WITHDRAW -> BankCounter.withdraw(player,id);
            case OPEN_BOARD -> TownServices.openBoard(player)?"":"请在酒馆委托栏旁使用。";
            default -> "未知请求。";
        };
        if(action==AdventurePackets.Action.ACCEPT)milestoneIfPresent(player,"accepted");
        AdventureSavedData.get(player.server).setDirty();sync(player,false,message);
    }
    public static void milestoneIfPresent(ServerPlayer player,String id){if(profile(player).hasMilestone(id))milestone(player,id);}
    public static void sync(ServerPlayer player,boolean open,String message) {
        sync(player,open,message,ServicePanel.PROFILE.role());
    }
    public static void sync(ServerPlayer player,boolean open,String message,String role) {
        MagicStoneNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new AdventurePackets.Snapshot(snapshot(player,message,role),open));
    }
    static CompoundTag snapshot(ServerPlayer player,String message,String role) {
        BankService.settle(player);var store=AdventureSavedData.get(player.server);var p=profile(player);
        var tag=AdventureSavedData.writeProfile(p);tag.putLong("ActiveTicks",store.activeTicks);tag.putBoolean("AtService",atService(player));tag.putString("Message",message);
        tag.putBoolean("AtSmith",TownServices.near(player,"smith"));tag.putBoolean("AtBroker",TownServices.near(player,"broker"));tag.putBoolean("AtBoard",TownServices.atBoard(player));
        tag.putBoolean("AtArmorer",TownServices.near(player,"armorer"));
        var panel=ServicePanel.fromRole(role);tag.putString("ServiceRole",panel.role());tag.putInt("InitialTab",panel.tab());
        tag.putString("Directions",TownServices.directions(player.server.overworld()));
        tag.put("Housing",com.tnc.tnc.home.HousingService.snapshot(player));
        tag.putLong("BankNow",System.currentTimeMillis());tag.putInt("BankInterestPercent",Config.bankDailyInterestPercent);tag.putInt("BankWage",BankService.wage(p));
        tag.putBoolean("AtHomeSale",com.tnc.tnc.home.HousingService.atSale(player));
        var pos=tavern(player);if(pos!=null)tag.putString("Tavern",pos.getX()+" / "+pos.getY()+" / "+pos.getZ());
        var counts=new CompoundTag();for(var c:ContractCatalog.ALL) {
            int have=c.kills()>0?(p.contracts.containsKey(c.id())?p.contracts.get(c.id()).kills:0):c.materials().stream().mapToInt(m->Math.min(InventoryTransaction.count(player.getInventory(),m),m.count())).sum();
            counts.putInt(c.id(),have);
        }
        tag.put("Counts",counts);return tag;
    }
}
