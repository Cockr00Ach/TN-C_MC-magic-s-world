package com.tnc.tnc.combat;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import java.util.*;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class CombatGameTests {
    private static List<ServerPlayer> online(GameTestHelper h) {return ObfuscationReflectionHelper.getPrivateValue(net.minecraft.server.players.PlayerList.class,h.getLevel().getServer().getPlayerList(),"f_11196_");}
    private static Map<UUID,ServerPlayer> lookup(GameTestHelper h) {return ObfuscationReflectionHelper.getPrivateValue(net.minecraft.server.players.PlayerList.class,h.getLevel().getServer().getPlayerList(),"f_11197_");}
    private static ServerPlayer player(GameTestHelper h,String team,int x) {
        var board=h.getLevel().getScoreboard();var t=board.getPlayerTeam(team);if(t==null)t=board.addPlayerTeam(team);
        // Forge FakePlayer deliberately rejects all damage and overrides die(). Use a real
        // ServerPlayer with a no-op network sink so the actual lethal lifecycle is testable.
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),UUID.randomUUID().toString().substring(0,12)));
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(p.server,
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener) {}
        };
        ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class,p,0,"f_8921_");
        p.setGameMode(GameType.SURVIVAL);p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(x,3,2))));
        board.addPlayerToTeam(p.getScoreboardName(),t);online(h).add(p);lookup(h).put(p.getUUID(),p);
        DownedCombat.ensureHealth(p);return p;
    }
    private static void cleanup(GameTestHelper h,ServerPlayer... players) {
        for(var p:players) {
            online(h).remove(p);lookup(h).remove(p.getUUID());
            if(DownedCombat.isDowned(p))DownedCombat.revive(p);
            DownedCombat.hold(p,false);h.getLevel().getScoreboard().removePlayerFromTeam(p.getScoreboardName());
        }
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void soloDownedBlocksDamageAndRestoresSixtyPercentWithCooldown(GameTestHelper h) {
        var p=player(h,"s"+UUID.randomUUID().toString().substring(0,10),2);
        try {
            h.assertTrue(p.getMaxHealth()==40,"Base max health is forty");
            h.assertTrue(DownedCombat.enter(p)&&DownedCombat.isDowned(p)&&p.getHealth()==1,"Solo enters downed at one health");
            var event=new LivingAttackEvent(p,p.damageSources().generic(),100);DownedCombat.attack(event);
            h.assertTrue(event.isCanceled(),"Downed must be immune to attacks");
            var heal=new LivingHealEvent(p,100);DownedCombat.heal(heal);h.assertTrue(heal.isCanceled(),"Ordinary healing cannot replace rescue");
            h.assertTrue(DownedCombat.canRescue(p,p),"Solo self rescue allowed");
            DownedCombat.revive(p);h.assertTrue(p.getHealth()==24&&!DownedCombat.isDowned(p),"Revival is sixty percent of max health");
            h.assertTrue(!DownedCombat.enter(p),"Solo passive cannot retrigger during five-minute cooldown");
            h.assertTrue(DownedCombat.data(p).getLong("soloUntil")-p.server.overworld().getGameTime()==6000,"Cooldown is persisted");
        } finally {cleanup(h,p);}h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=100)
    public static void cooperativeRescueRequiresRealThreeSeconds(GameTestHelper h) {
        String team="m"+UUID.randomUUID().toString().substring(0,10);var a=player(h,team,2);var b=player(h,team,3);
        DownedCombat.enter(a);
        h.assertTrue(!DownedCombat.canRescue(a,a)&&DownedCombat.canRescue(b,a),"Multiplayer denies self-rescue and accepts teammate");
        h.onEachTick(()->{if(DownedCombat.isDowned(a))DownedCombat.hold(b,true);});
        h.runAfterDelay(30,()->{
            try {h.assertTrue(DownedCombat.isDowned(a),"Half duration is not enough");}
            catch(Throwable t){cleanup(h,a,b);throw t;}
        });
        h.runAfterDelay(65,()->{
            try {h.assertTrue(!DownedCombat.isDowned(a)&&a.getHealth()==24,"Natural server ticks complete three-second rescue");h.succeed();}
            finally{cleanup(h,a,b);}
        });
        h.runAfterDelay(95,()->cleanup(h,a,b));
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void multiplayerWholePartyDownedProducesRealDeaths(GameTestHelper h) {
        String team="w"+UUID.randomUUID().toString().substring(0,10);var a=player(h,team,2);var b=player(h,team,3);
        try {
            h.assertTrue(DownedCombat.enter(a),"First teammate downs");
            // Death events arrive while the current victim is already at zero health.
            b.setHealth(0);h.assertTrue(DownedCombat.enter(b),"Lethal second teammate also downs");
            h.assertTrue(!DownedCombat.data(b).getBoolean("solo"),"Dying victim is included in party size");
            DownedCombat.wipe(List.of(a,b));
            h.assertTrue(!a.isAlive()&&!b.isAlive()&&!DownedCombat.isDowned(a)&&!DownedCombat.isDowned(b),"Party wipe cannot turn into solo immunity");
        }finally{cleanup(h,a,b);}h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void rescueRejectsDistanceObstructionAndOtherTeam(GameTestHelper h) {
        String team="r"+UUID.randomUUID().toString().substring(0,10);var a=player(h,team,2);var b=player(h,team,3);var c=player(h,"outsider"+UUID.randomUUID().toString().substring(0,5),4);
        try {
            DownedCombat.enter(a);h.assertTrue(!DownedCombat.canRescue(c,a),"Other teams cannot rescue");
            b.setPos(a.position().add(4,0,0));h.assertTrue(!DownedCombat.canRescue(b,a),"Range over three denied");
            b.setPos(a.position().add(0,0,2));
            h.getLevel().setBlockAndUpdate(a.blockPosition().offset(0,1,1),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            h.getLevel().setBlockAndUpdate(a.blockPosition().offset(0,0,1),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            h.assertTrue(!DownedCombat.canRescue(b,a),"Wall blocks rescue");
        }finally{cleanup(h,a,b,c);}h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void cloneAndReconnectDoNotResetDownedStateOrCooldown(GameTestHelper h) {
        var p=player(h,"c"+UUID.randomUUID().toString().substring(0,10),2);
        try {
            DownedCombat.enter(p);long until=DownedCombat.data(p).getLong("soloUntil");
            var clone=new ServerPlayer(p.server,h.getLevel(),p.getGameProfile());
            DownedCombat.clone(new net.minecraftforge.event.entity.player.PlayerEvent.Clone(clone,p,false));
            h.assertTrue(DownedCombat.isDowned(clone)&&DownedCombat.data(clone).getLong("soloUntil")==until,"Non-death End clone preserves downed and cooldown");
            // Simulate the same persisted NBT restored on login, without adding a duplicate UUID online.
            var restored=new ServerPlayer(p.server,h.getLevel(),p.getGameProfile());
            restored.getPersistentData().merge(p.getPersistentData().copy());
            DownedCombat.login(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(restored));
            h.assertTrue(DownedCombat.isDowned(restored)&&DownedCombat.data(restored).getLong("soloUntil")==until,"Reconnect preserves pending self rescue and cooldown");
            var deadClone=new ServerPlayer(p.server,h.getLevel(),p.getGameProfile());
            DownedCombat.clone(new net.minecraftforge.event.entity.player.PlayerEvent.Clone(deadClone,p,true));
            h.assertTrue(!DownedCombat.isDowned(deadClone)&&DownedCombat.data(deadClone).getLong("soloUntil")==until,"Actual death clears downed but not passive cooldown");
        }finally{cleanup(h,p);}h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void fireRevivalTakesPrecedenceInRealEventBusOrder(GameTestHelper h) {
        var p=player(h,"f"+UUID.randomUUID().toString().substring(0,10),2);
        try {
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.tnc.tnc.magic.TNFireMechanics.BLAZE_BURN.get(),300));
            p.setHealth(0);
            var event=new LivingDeathEvent(p,p.damageSources().generic());
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
            h.assertTrue(event.isCanceled()&&!DownedCombat.isDowned(p)&&p.getHealth()==p.getMaxHealth(),"Fire death prevention runs before downed fallback");
            h.assertTrue(DownedCombat.data(p).getLong("soloUntil")==0,"Fire revival does not consume the solo passive");
        }finally{cleanup(h,p);}h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void totemPreventsDeathBeforeSoloPassive(GameTestHelper h) {
        var p=player(h,"t"+UUID.randomUUID().toString().substring(0,10),2);
        try {
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
            p.hurt(p.damageSources().generic(),10000);
            h.assertTrue(p.isAlive()&&!DownedCombat.isDowned(p)&&p.getMainHandItem().isEmpty(),"Real totem consumes before downed death fallback");
            h.assertTrue(DownedCombat.data(p).getLong("soloUntil")==0,"Totem cannot consume solo cooldown");
        }finally{cleanup(h,p);}h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void lethalDamageTriggersDownedAndAutomaticPartyWipe(GameTestHelper h) {
        String team="d"+UUID.randomUUID().toString().substring(0,10);var a=player(h,team,2);var b=player(h,team,3);
        try {
            a.hurt(a.damageSources().genericKill(),10000);
            h.assertTrue(DownedCombat.isDowned(a)&&a.isAlive()&&a.getHealth()==1,"Real lethal damage produces invulnerable downed state");
            b.hurt(b.damageSources().genericKill(),10000);
            h.assertTrue(DownedCombat.isDowned(b),"Second real lethal damage must remain multiplayer");
        }catch(Throwable t){cleanup(h,a,b);throw t;}
        h.runAfterDelay(3,()->{
            try {h.assertTrue(!a.isAlive()&&!b.isAlive()&&!DownedCombat.isDowned(a)&&!DownedCombat.isDowned(b),"Normal server tick automatically defeats the whole downed party");h.succeed();}
            finally{cleanup(h,a,b);}
        });
        h.runAfterDelay(35,()->cleanup(h,a,b));
    }
    @GameTest(template="building_test_empty",timeoutTicks=130)
    public static void interruptedHoldDoesNotCarryItsPreviousProgress(GameTestHelper h) {
        String team="i"+UUID.randomUUID().toString().substring(0,10);var a=player(h,team,2);var b=player(h,team,3);
        DownedCombat.enter(a);
        h.onEachTick(()->{
            long age=h.getTick();
            if(age<30||age>=40)DownedCombat.hold(b,true);else DownedCombat.hold(b,false);
        });
        h.runAfterDelay(80,()->{
            try {h.assertTrue(DownedCombat.isDowned(a),"Interrupted first thirty ticks must not count toward the second hold");}
            catch(Throwable t){cleanup(h,a,b);throw t;}
        });
        h.runAfterDelay(105,()->{
            try {h.assertTrue(!DownedCombat.isDowned(a)&&a.getHealth()==24,"Fresh sixty consecutive ticks rescue teammate");h.succeed();}
            finally{cleanup(h,a,b);}
        });
        h.runAfterDelay(125,()->cleanup(h,a,b));
    }
}
