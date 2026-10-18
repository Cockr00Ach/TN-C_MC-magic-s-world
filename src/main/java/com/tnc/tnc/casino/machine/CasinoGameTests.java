package com.tnc.tnc.casino.machine;

import com.mojang.authlib.GameProfile;
import com.tnc.tnc.TNMod;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class CasinoGameTests {
    private static TNSlotMachineEntity slot(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(1,1,1));
        h.getLevel().setBlockAndUpdate(pos,TNCasinoRegistry.block("slot_machine").defaultBlockState());
        return (TNSlotMachineEntity)h.getLevel().getBlockEntity(pos);
    }
    private static ServerPlayer player(GameTestHelper h) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"CasinoTest"));
        p.getInventory().clearContent();return p;
    }
    private static void winning(TNSlotMachineEntity m,GameTestHelper h,ServerPlayer p) {
        m.stake=1;m.bettor=p.getUUID();m.result[0]=m.result[1]=m.result[2]=5;
        m.beginSpin(h.getLevel(),m.getBlockPos(),m.getBlockState());
    }
    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoLosingStakeIsConsumedAndDoesNotCarryToNextRound(GameTestHelper h) {
        var m=slot(h);m.stake=1;m.result[0]=0;m.result[1]=1;m.result[2]=2;
        m.settle(h.getLevel(),m.getBlockPos(),m.getBlockState());
        h.assertTrue(m.stake()==0&&m.owed()==0,"Loss must consume the entire stake");h.succeed();
    }
    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoPayoutBelongsToBettorAndCanOnlyBeCollectedOnce(GameTestHelper h) {
        var m=slot(h);var p=player(h);var other=player(h);winning(m,h,p);
        m.settle(h.getLevel(),m.getBlockPos(),m.getBlockState());m.interact(other);
        h.assertTrue(m.owed()==50&&other.getInventory().countItem(TNMod.COPPER_COIN.get())==0,"Other player cannot take payout");
        m.interact(p);m.interact(p);
        h.assertTrue(m.owed()==0&&p.getInventory().countItem(TNMod.COPPER_COIN.get())==50,"Exactly one payout");h.succeed();
    }
    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoPartialInventoryPayoutKeepsOnlyUnreceivedCoins(GameTestHelper h) {
        var m=slot(h);var p=player(h);winning(m,h,p);m.settle(h.getLevel(),m.getBlockPos(),m.getBlockState());
        for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        p.getInventory().setItem(0,new ItemStack(TNMod.COPPER_COIN.get(),60));m.interact(p);
        h.assertTrue(m.owed()==46&&p.getInventory().getItem(0).getCount()==64,"Only four coins fit; debt is 46");
        p.getInventory().setItem(1,ItemStack.EMPTY);m.interact(p);
        h.assertTrue(m.owed()==0&&p.getInventory().countItem(TNMod.COPPER_COIN.get())==110,"Total received exactly 50");h.succeed();
    }
    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoBettorAndDebtSurviveSaveAndReload(GameTestHelper h) {
        var m=slot(h);var p=player(h);winning(m,h,p);m.settle(h.getLevel(),m.getBlockPos(),m.getBlockState());
        CompoundTag saved=new CompoundTag();m.saveAdditional(saved);
        var loaded=new TNSlotMachineEntity(m.getBlockPos(),m.getBlockState());loaded.load(saved);
        h.assertTrue(loaded.owed()==50&&loaded.bettor.equals(p.getUUID())&&loaded.stake()==0,"Payout and claimant persist");h.succeed();
    }
    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoServerWaitsUntilAnimationEndsAndPreservesSpinClock(GameTestHelper h) {
        var m=slot(h);var p=player(h);winning(m,h,p);
        long now=h.getLevel().getGameTime();m.startedAt=now-m.spinTicks();
        m.serverTick(h.getLevel(),m.getBlockPos(),m.getBlockState());
        h.assertTrue(m.spinning(),"Server must wait for final reel to stop");
        m.startedAt=now-m.clientSpinTicks();long origin=m.startedAt;
        m.serverTick(h.getLevel(),m.getBlockPos(),m.getBlockState());
        h.assertTrue(m.phase()==TNMachineEntity.Phase.RESULT&&m.startedAt()==origin,"Settlement cannot reset animation clock");h.succeed();
    }
    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoNewEquipmentHasSeparateValidEntities(GameTestHelper h) {
        int i=1;
        for(String id:new String[]{"lucky_wheel","card_table","roulette_table"}) {
            var pos=h.absolutePos(new BlockPos(i++,1,3));var block=TNCasinoRegistry.block(id);
            h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());
            var entity=h.getLevel().getBlockEntity(pos);
            h.assertTrue(entity instanceof TNMachineEntity&&entity.getType().isValid(block.defaultBlockState()),"Entity registered for "+id);
            if(entity instanceof TNCasinoGameEntity game) {
                game.selectedBet=1;CompoundTag tag=new CompoundTag();game.saveAdditional(tag);game.load(tag);
                h.assertTrue(game.selectedBet()<game.betNames().length,"Bet is sanitized using correct machine rules");
            }
        }h.succeed();
    }
    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoClientSnapshotIncludesRoundCountBetAndActualReturn(GameTestHelper h) {
        var m=slot(h);var p=player(h);winning(m,h,p);m.settle(h.getLevel(),m.getBlockPos(),m.getBlockState());
        var tag=m.getUpdateTag();var client=new TNSlotMachineEntity(m.getBlockPos(),m.getBlockState());client.load(tag);
        h.assertTrue(client.spins()==1&&client.lastPayout()==50&&client.phase()==TNMachineEntity.Phase.RESULT,"Client can show the actual round and result");
        h.assertTrue(!tag.contains("Bettor")&&!tag.contains("Owed"),"Private debt does not belong in visual snapshot");h.succeed();
    }
    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoBreakingKnownLosingRoundCannotRefundStake(GameTestHelper h) {
        var m=slot(h);m.stake=1;m.result[0]=0;m.result[1]=1;m.result[2]=2;
        m.beginSpin(h.getLevel(),m.getBlockPos(),m.getBlockState());m.dropOwed(h.getLevel(),m.getBlockPos());
        h.assertTrue(m.stake()==0&&m.owed()==0,"Broken machine clears the resolved losing stake");h.succeed();
    }
    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoWideTableCollisionRotatesWithItsModel(GameTestHelper h) {
        var block=TNMod.GAMBLE_TABLE.get();var pos=h.absolutePos(new BlockPos(1,1,1));
        var state=block.defaultBlockState().setValue(TNCasinoMachineBlock.FACING,Direction.EAST);
        var bounds=state.getCollisionShape(h.getLevel(),pos).bounds();
        h.assertTrue(bounds.getXsize()==4&&bounds.getZsize()==8&&bounds.minX==2&&bounds.minZ==-2,"East table rotates about (4,2)");h.succeed();
    }

    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoColourButtonsChooseAllColoursAndLockWhileSpinning(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(1,1,1));var block=TNCasinoRegistry.block("roulette_table");
        h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());
        var m=(TNRouletteTableEntity)h.getLevel().getBlockEntity(pos);var p=player(h);
        for(int bet:new int[]{0,6,1}) {m.chooseColour(p,bet);h.assertTrue(m.selectedBet()==bet,"Visible colour changes actual bet");}
        m.stake=1;m.beginSpin(h.getLevel(),pos,m.getBlockState());m.chooseColour(p,6);
        h.assertTrue(m.selectedBet()==1,"Bet stays black once spinning");
        var bounds=m.getBlockState().getShape(h.getLevel(),pos).bounds();
        h.assertTrue(bounds.getXsize()==8&&bounds.getZsize()==4,"Expanded 8x4 surface");h.succeed();
    }

    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoCardSuitsRemainIndependentThroughSaveAndSync(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(1,1,1));var block=TNCasinoRegistry.block("card_table");
        h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());
        var m=(TNCasinoGameEntity)h.getLevel().getBlockEntity(pos);
        for(int suits=0;suits<16;suits++) {
            m.result[0]=12;m.result[1]=0;m.result[2]=suits;
            var copy=new TNCasinoGameEntity(TNCasinoRegistry.CARD_ENTITY,pos,m.getBlockState(),TNCasinoGameEntity.Game.CARDS);
            copy.load(m.getUpdateTag());
            h.assertTrue(copy.result(0)==12&&copy.cardSuit(0)==suits%4&&copy.cardSuit(1)==suits/4,"All 16 independent suit pairs survive sync");
        }h.succeed();
    }

    @GameTest(template="casino_test_empty",timeoutTicks=30)
    public static void casinoButtonRayRejectsWallAndOutOfReach(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(1,1,1));var block=TNCasinoRegistry.block("roulette_table");
        h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());var p=player(h);
        p.moveTo(pos.getX()+6.5,pos.getY()+2,pos.getZ()+1.25,0,90);
        var hit=RouletteControls.hit(p,pos,Direction.NORTH);
        h.assertTrue(hit!=null&&RouletteControls.button(hit.x,hit.z)==0,"Remote tabletop button is reachable");
        p.moveTo(pos.getX()+6.5,pos.getY()+3,pos.getZ()+1.25,0,90);
        h.getLevel().setBlockAndUpdate(pos.offset(6,2,1),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        h.assertTrue(RouletteControls.hit(p,pos,Direction.NORTH)==null,"Solid obstruction prevents click-through");
        p.moveTo(pos.getX()+6.5,pos.getY()+10,pos.getZ()+1.25,0,90);
        h.assertTrue(RouletteControls.hit(p,pos,Direction.NORTH)==null,"Reach limit is enforced");h.succeed();
    }
}
