package com.tnc.tnc.warrior;

import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.adventure.TownServiceNpc;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncwarrior") @PrefixGameTestTemplate(false)
public final class TrainingGroundGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void guildEntryCreatesOwnedTrainerAndSafeReturn(GameTestHelper h){
        var p=AdventureGameTests.player(h);var source=h.absolutePos(new BlockPos(2,3,2));
        h.getLevel().setBlock(source.below(),Blocks.STONE.defaultBlockState(),3);p.setPos(Vec3.atBottomCenterOf(source));p.setOnGround(true);
        h.assertTrue(!TrainingGround.enter(p),"Zero aptitude cannot enter practice");
        TemperingData.get(p.server).setTalent(p.getUUID(),2);
        h.assertTrue(!TrainingGround.enter(p),"Field shortcut cannot bypass association location");
        var guild=new TownServiceNpc(EntityType.VILLAGER,h.getLevel());guild.role("guild");guild.moveTo(p.position().add(1,0,0));h.getLevel().addFreshEntity(guild);
        h.assertTrue(TrainingGround.enter(p),"Qualified player beside real guild worker can enter");
        boolean[] checked={false};
        h.onEachTick(()->{
            if(checked[0]||!TrainingGround.inside(p))return;checked[0]=true;
            try{
                var teacher=TrainingGround.teacher(p);h.assertTrue(teacher!=null&&TrainingGround.trainer(teacher),"Personal teacher really spawned");
                h.assertTrue(TrainingGroundData.get(p.server).origin(p.getUUID())!=null,"Return point recorded before entry");
                h.assertTrue(p.serverLevel().getBlockState(p.blockPosition().below()).is(Blocks.SMOOTH_STONE),"Arrival has a real floor");
                net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(net.minecraft.server.level.ServerPlayer.class,p,0,"f_8921_");
                float health=p.getHealth();p.invulnerableTime=0;p.hurt(p.damageSources().mobAttack(teacher),4);
                h.assertTrue(p.getHealth()==health,"Unblocked practice hit does not remove life");
                var block=new BlockEvent.BreakEvent(p.serverLevel(),p.blockPosition().below(),p.serverLevel().getBlockState(p.blockPosition().below()),p);
                MinecraftForge.EVENT_BUS.post(block);h.assertTrue(block.isCanceled(),"Practice floor is protected");
                var q=AdventureGameTests.player(h);var attack=new LivingAttackEvent(teacher,p.damageSources().playerAttack(q),4);
                MinecraftForge.EVENT_BUS.post(attack);h.assertTrue(attack.isCanceled(),"Other players cannot damage this teacher");
                int before=TemperingData.get(p.server).points(p.getUUID());
                TemperingEvents.defeated(new LivingDeathEvent(teacher,p.damageSources().playerAttack(p)));
                h.assertTrue(TemperingData.get(p.server).points(p.getUUID())==before,"Training teacher cannot yield kill grinding rewards");
                TrainingGround.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p));
                h.assertTrue(teacher.isRemoved()&&TrainingGroundData.get(p.server).origin(p.getUUID())!=null,"Logout removes teacher but preserves recovery anchor");
                TrainingGround.login(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(p));
                h.assertTrue(!TrainingGround.inside(p)&&p.position().distanceTo(Vec3.atBottomCenterOf(source))<.1,"Return preserves original landing");
                h.assertTrue(TrainingGroundData.get(p.server).origin(p.getUUID())==null&&teacher.isRemoved(),"Exit clears session and teacher");
                h.succeed();
            }finally{guild.discard();p.discard();}
        });
    }
    @GameTest(template="building_test_empty",timeoutTicks=500)
    public static void realTeacherAttacksCanBeBlockedAndCounteredForFirstReward(GameTestHelper h){
        realPractice(h,0,false);
    }
    @GameTest(template="building_test_empty",timeoutTicks=500)
    public static void expertTeacherRecordsRealCounterWithoutRepeatingGrowthReward(GameTestHelper h){
        realPractice(h,2,true);
    }
    private static void realPractice(GameTestHelper h,int mode,boolean graduated){
        var p=AdventureGameTests.player(h);var source=h.absolutePos(new BlockPos(3,3,3));
        h.getLevel().setBlock(source.below(),Blocks.STONE.defaultBlockState(),3);p.setPos(Vec3.atBottomCenterOf(source));p.setOnGround(true);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        var shield=new ItemStack(Items.SHIELD);p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,shield);
        var guild=new TownServiceNpc(EntityType.VILLAGER,h.getLevel());guild.role("guild");guild.moveTo(p.position().add(1,0,0));h.getLevel().addFreshEntity(guild);
        var data=TemperingData.get(p.server);data.setTalent(p.getUUID(),2);
        if(graduated)data.completeGuardDrill(p.getUUID());
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(net.minecraft.server.level.ServerPlayer.class,p,0,"f_8921_");
        h.assertTrue(TrainingGround.enter(p),"Real practice session requested");
        boolean[] started={false},done={false};int[] ticks={0};float health=p.getHealth();
        h.onEachTick(()->{
            if(done[0]||!TrainingGround.inside(p))return;
            if(!started[0]){
                for(int m=0;m<mode;m++)h.assertTrue(TrainingGround.cyclePractice(p),"Idle player can select difficulty on the server");
                TrainingGround.resetRhythm(p);
                started[0]=true;p.setYRot(0);p.setXRot(0);p.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
                h.assertTrue(TemperingTraining.start(p),"Protected field drill starts");
                h.assertTrue(!TrainingGround.cyclePractice(p)&&TemperingTraining.mode(p)==mode,"Active drill locks difficulty");
                if(graduated){
                    p.stopUsingItem();p.invulnerableTime=0;
                    p.hurt(p.damageSources().mobAttack(TrainingGround.teacher(p)),4);
                    h.assertTrue(p.getHealth()==health,"A missed strike is safe");
                    p.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
                }
            }
            // A headless player has no movement packets: maintain the close-range position
            // a human keeps by stepping forward after shield knockback.
            int plot=TrainingGroundData.get(p.server).plot(p.getUUID());
            p.setPos(TrainingGroundRules.centerX(plot)+.5,2,TrainingGroundRules.centerZ(plot)+1.5);p.setDeltaMovement(Vec3.ZERO);
            p.tick();p.doTick();TrainingGround.practiceTick(p);
            if(++ticks[0]==300&&TemperingTraining.blockCount(p)<3){
                var teacher=TrainingGround.teacher(p);
                h.fail("Real blocking stalled: using="+p.isUsingItem()+", blocking="+p.isBlocking()+", blocks="+TemperingTraining.blockCount(p)
                    +", swings="+(teacher==null?-1:teacher.getPersistentData().getInt("Swings"))+", position="+p.position());return;
            }
            if(TemperingTraining.blockCount(p)<3)return;
            done[0]=true;
            try{
                var teacher=TrainingGround.teacher(p);h.assertTrue(teacher!=null&&teacher.getPersistentData().getInt("Swings")>=3,"Instructor performed real timed strikes");
                h.assertTrue(p.getHealth()==health&&shield.getDamageValue()==0,"Three actual blocks preserve life and shield durability");
                p.stopUsingItem();p.attack(teacher);
                h.assertTrue(data.points(p.getUUID())==(graduated?0:120)&&data.guardGraduate(p.getUUID()),"Only first graduation yields growth, even on expert tier");
                var record=TrainingGroundData.get(p.server).record(p.getUUID());
                h.assertTrue(record.mode()==mode&&record.completions()==1&&record.score()==(graduated?85:100)&&record.reaction()==0,"Real misses and counter update selected tier's personal record: "+record);
                h.assertTrue(TrainingGround.leave(p),"Completed drill can return normally");h.succeed();
            }finally{guild.discard();p.discard();}
        });
    }
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void tierRecordsPersistSeparatelyAndDisplayPacketRoundTrips(GameTestHelper h){
        var ledger=new TrainingGroundData();var id=UUID.randomUUID();ledger.allocate(id);
        ledger.complete(id,0,90,12);ledger.complete(id,0,80,6);ledger.cycle(id);
        h.assertTrue(ledger.record(id).completions()==0,"A new tier has its own records");
        ledger.complete(id,1,75,20);ledger=TrainingGroundData.load(ledger.save(new CompoundTag()));
        h.assertTrue(ledger.record(id).mode()==1&&ledger.record(id).score()==75,"Selected tier survives save/load");
        ledger.cycle(id);ledger.cycle(id);var record=ledger.record(id);
        h.assertTrue(record.completions()==2&&record.score()==90&&record.reaction()==6,"Best score and fastest counter remain separate personal records");
        var packet=new TemperingPacket(true,200,2,true,false,0,0,0,true,true,2,5,95,10);
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{packet.encode(buffer);h.assertTrue(packet.equals(TemperingPacket.decode(buffer)),"Practice record network state round-trips");}finally{buffer.release();}
        h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void plotAndReturnLedgerSurvivesReloadAndRejectsCorruptAnchors(GameTestHelper h){
        var d=new TrainingGroundData();var p=AdventureGameTests.player(h);var q=UUID.randomUUID();p.setPos(30.5,70,-20.5);
        int a=d.allocate(p.getUUID()),b=d.allocate(q);d.begin(p);
        var reloaded=TrainingGroundData.load(d.save(new CompoundTag()));
        h.assertTrue(a!=b&&reloaded.allocate(p.getUUID())==a&&reloaded.allocate(q)==b,"Plot ownership survives reload without overlap");
        h.assertTrue(reloaded.origin(p.getUUID()).x()==30.5,"Return coordinates persist independently of drill progress");
        h.assertTrue(reloaded.allocate(UUID.randomUUID())>Math.max(a,b),"New player cannot reuse occupied plot");
        var saved=d.save(new CompoundTag());var players=saved.getList("Players",Tag.TAG_COMPOUND);
        for(Tag raw:players){var t=(CompoundTag)raw;if(t.getUUID("Id").equals(p.getUUID()))t.getCompound("Return").putDouble("X",Double.NaN);}
        h.assertTrue(TrainingGroundData.load(saved).origin(p.getUUID())==null,"Malformed anchor is rejected instead of teleporting NaN");
        h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void trainingDimensionContainsSeparatedPlayableAndReturnAreas(GameTestHelper h){
        var level=h.getLevel().getServer().getLevel(TrainingGround.DIMENSION);h.assertTrue(level!=null,"Actual training dimension loaded");
        for(int plot:new int[]{60000,60001}){
            int x=TrainingGroundRules.centerX(plot),z=TrainingGroundRules.centerZ(plot);
            for(int dx:new int[]{-12,12})for(int dz:new int[]{-12,12})level.getChunk((x+dx)>>4,(z+dz)>>4);
            TrainingGround.build(level,plot);
            h.assertTrue(level.getBlockState(new BlockPos(x,1,z)).is(Blocks.SMOOTH_STONE)&&level.getBlockState(new BlockPos(x,2,z)).isAir(),"Center is safe and walkable");
            h.assertTrue(level.getBlockState(new BlockPos(x,1,z-8)).is(Blocks.EMERALD_BLOCK),"Return platform exists");
            h.assertTrue(level.getBlockState(new BlockPos(x+12,3,z)).is(Blocks.STONE_BRICKS),"Boundary walls exist");
            h.assertTrue(level.getBlockState(new BlockPos(x+10,6,z+10)).is(Blocks.LANTERN),"Practice yard has real lighting");
        }
        h.succeed();
    }
}
