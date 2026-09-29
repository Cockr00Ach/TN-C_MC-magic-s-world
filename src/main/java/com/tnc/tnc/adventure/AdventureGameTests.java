package com.tnc.tnc.adventure;

import com.mojang.authlib.GameProfile;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class AdventureGameTests {
    public static ServerPlayer player(GameTestHelper h) {
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"AdvTest"));
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(p.server,new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){}
        };return p;
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void realDeliveryIsAtomicAndCannotBeRepeated(GameTestHelper h) {
        var a=player(h);var b=player(h);AdventureService.register(a);AdventureService.register(b);
        var p=AdventureService.profile(a);p.accept(ContractCatalog.find("welcome"),0);
        a.getInventory().setItem(0,new ItemStack(Items.OAK_LOG,4));a.getInventory().setItem(1,new ItemStack(Items.COBBLESTONE,7));
        AdventureService.deliver(a,"welcome");h.assertTrue(p.coins()==0&&a.getInventory().getItem(0).getCount()==4,"Incomplete inputs untouched");
        a.getInventory().getItem(1).grow(1);AdventureService.deliver(a,"welcome");
        h.assertTrue(p.coins()==30&&p.xp()==120&&a.getInventory().getItem(0).isEmpty(),"One real delivery pays and consumes once");
        AdventureService.deliver(a,"welcome");h.assertTrue(p.coins()==30&&p.xp()==120,"Duplicate click cannot pay twice");
        h.assertTrue(AdventureService.profile(b).coins()==0,"Other player's account remains independent");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void smithOrderSurvivesSaveAndFullInventoryClaim(GameTestHelper h) {
        var a=player(h);AdventureService.register(a);
        a.getInventory().setItem(0,new ItemStack(Items.STICK,4));a.getInventory().setItem(1,new ItemStack(Items.COPPER_INGOT,2));
        AdventureService.order(a);var store=AdventureSavedData.get(a.server);var p=AdventureService.profile(a);
        h.assertTrue(p.smithReady>=0&&p.coins()==0,"Free teaching accepted");
        var loaded=AdventureSavedData.load(store.save(new net.minecraft.nbt.CompoundTag()));
        h.assertTrue(loaded.players.get(a.getUUID()).smithReady==p.smithReady,"Pending order survives restart");
        store.activeTicks=p.smithReady;for(int i=0;i<36;i++)a.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        AdventureService.claim(a);h.assertTrue(p.smithReady>=0,"Full pack retains order");
        a.getInventory().setItem(0,ItemStack.EMPTY);AdventureService.claim(a);AdventureService.claim(a);
        h.assertTrue(p.crafted&&p.smithReady==-1&&a.getInventory().getItem(0).is(TNMod.WAND.get()),"Exactly one finished wand");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void legacyMigrationPreservesManaPointsAndForgetMarkers(GameTestHelper h) {
        var a=player(h);var magic=MagicStone.getOrNull(a);magic.assignDefaultAffinities(3);a.experienceLevel=39;
        magic.recomputeMaxMana(39,10,10,0);magic.addBonusPoints(50);
        var entry=SpellCatalog.all().stream().filter(e->e.tier()==1).findFirst().orElseThrow();
        MagicStoneLearning.unlock(magic,entry);magic.forget(entry.id());
        int available=magic.getPointsAvailable(com.tnc.tnc.Config.pointThresholds);int oldMana=magic.getMaxMana();
        var profile=AdventureService.profile(a);AdventureService.refreshGrowth(a);
        h.assertTrue(profile.level()>=40&&magic.getMaxMana()>=oldMana&&magic.getPointsAvailable(com.tnc.tnc.Config.pointThresholds)>=available,"Migration does not reduce entitlement");
        a.experienceLevel=0;AdventureService.refreshGrowth(a);h.assertTrue(magic.getMaxMana()>=oldMana,"Enchanting does not reduce growth");
        h.assertTrue(MagicStoneLearning.unlock(magic,entry)==MagicStoneLearning.Result.OK,"Forgotten spell can still be relearned");h.succeed();
    }
}
