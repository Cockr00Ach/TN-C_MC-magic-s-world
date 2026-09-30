package com.tnc.tnc.adventure;
import com.tnc.tnc.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class BankService {
    public static int wage(AdventureProfile p){return BankAccount.salary(p.level(),Config.bankDailyWages);}
    public static void settle(ServerPlayer p){settle(p,System.currentTimeMillis());}
    public static void settle(ServerPlayer p,long now){
        var a=AdventureService.profile(p);a.bank.settle(now,a.registered(),wage(a),Config.bankDailyInterestPercent);a.bank.flush(a,Math.max(now,a.bank.lastAt));
        var home=com.tnc.tnc.home.HousingService.home(p.server);
        if(home.hasUUID("Owner")&&home.getUUID("Owner").equals(p.getUUID())&&home.getBoolean("Mortgage")&&a.bank.mortgage.parts>0&&!a.bank.mortgage.active())home.putBoolean("Mortgage",false);
        AdventureSavedData.get(p.server).setDirty();
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)settle(p);}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.END&&e.player instanceof ServerPlayer p&&p.tickCount%20==0){
        var a=AdventureService.profile(p);a.bank.online(a,20);settle(p);
    }}
    private static long amount(String id,long all){
        if(id.equals("all"))return all;
        if(!id.matches("[0-9]{1,10}"))return -1;
        try{long n=Long.parseLong(id);return n>0&&n<=AdventureRules.MAX_COINS?n:-1;}catch(NumberFormatException e){return -1;}
    }
    public static String save(ServerPlayer p,String id){settle(p);var a=AdventureService.profile(p);long n=amount(id,a.coins());
        if(!a.bank.deposit(a,n))return "余额不足或金额无效，未扣钱。";
        AdventureService.milestone(p,"bank_deposit");AdventureSavedData.get(p.server).setDirty();return "已存入"+n+"铜；随时可取，每现实天"+Config.bankDailyInterestPercent+"%利息。";
    }
    public static String take(ServerPlayer p,String id){settle(p);var a=AdventureService.profile(p);long n=amount(id,a.bank.savings);
        if(!a.bank.withdraw(a,n))return "存款不足、钱袋已满或金额无效，未扣钱。";
        AdventureSavedData.get(p.server).setDirty();return "已取出"+n+"铜到钱袋，无手续费。";
    }
    public static String borrow(ServerPlayer p,String id){settle(p);var a=AdventureService.profile(p);long n=amount(id,0);
        if(n>1000||!a.bank.borrow(a,(int)n))return "需要对应冒险等级与协会登记；已有借款或欠付账单时不能新借。未增加欠款。";
        AdventureSavedData.get(p.server).setDirty();return "已借到"+n+"铜，总还"+a.bank.loan.total+"铜，每游玩40分钟自动还"+a.bank.loan.nextAmount()+"铜。离线不催款。";
    }
    public static String clear(ServerPlayer p,String id){settle(p);if(!id.equals("loan")&&!id.equals("home"))return "无效账单。";
        var a=AdventureService.profile(p);if(!a.bank.clear(a,id.equals("home")))return "没有待还借款，或钱袋和存款合计不足；未扣钱。";
        settle(p);return "已提前结清，未来费用已免除。";
    }
}
