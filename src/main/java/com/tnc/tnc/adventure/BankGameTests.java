package com.tnc.tnc.adventure;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.*;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class BankGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void ledgerSurvivesRealSavedDataReload(GameTestHelper h){
        var player=AdventureGameTests.player(h);var p=AdventureService.profile(player);
        p.registered=true;p.addXp(AdventureRules.xpAtLevel(20));p.credit(3000,"fixture");
        long now=System.currentTimeMillis();p.bank.settle(now,true,50,10);
        h.assertTrue(p.bank.borrow(p,500)&&p.bank.deposit(p,2000),"Borrow and save accepted");
        p.bank.settle(now+BankAccount.DAY/3,true,50,10);p.bank.startMortgage(2100);
        var store=AdventureSavedData.get(player.server);var loaded=AdventureSavedData.load(store.save(new CompoundTag()));
        var q=loaded.players.get(player.getUUID());
        h.assertTrue(q.bank.savings==2000&&q.bank.loan.remainingPrincipal()==500&&q.bank.mortgage.remaining()==2310,"Three balances survive reload");
        h.assertTrue(q.bank.interestNumerator==p.bank.interestNumerator&&q.bank.accruedInterest==p.bank.accruedInterest&&q.bank.nextSalaryPay==p.bank.nextSalaryPay,"Fraction and daily clocks survive reload");
        q.bank.settle(now+BankAccount.DAY,true,50,10);q.bank.flush(q,now+BankAccount.DAY);
        h.assertTrue(q.coins()==1700&&q.bank.onlineTicks==0,"Daily 150 interest and 50 salary; offline bills untouched");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void serverBankTransfersRejectMalformedAmountsAndSeparatePlayers(GameTestHelper h){
        var player=AdventureGameTests.player(h);var other=AdventureGameTests.player(h);var p=AdventureService.profile(player);p.credit(1000,"fixture");
        BankService.save(player,"500");BankService.save(player,"-10");BankService.save(player,"999999999999");BankService.take(player,"0");
        h.assertTrue(p.coins()==500&&p.bank.savings==500,"Invalid inputs cannot move money");
        BankService.take(player,"all");BankService.take(player,"all");
        h.assertTrue(p.coins()==1000&&p.bank.savings==0&&AdventureService.profile(other).coins()==0,"Take is conserved and duplicate safe, other player untouched");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void bankPacketCannotOperateAwayFromMilo(GameTestHelper h){
        var player=AdventureGameTests.player(h);player.setPos(900000,200,900000);var p=AdventureService.profile(player);p.credit(1000,"fixture");
        AdventureService.action(player,AdventurePackets.Action.BANK_SAVE,"500");
        AdventureService.action(player,AdventurePackets.Action.BANK_BORROW,"200");
        h.assertTrue(p.coins()==1000&&p.bank.savings==0&&!p.bank.loan.active(),"Remote packets cannot bank");h.succeed();
    }
}
