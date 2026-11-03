package com.tnc.tnc.warrior;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tnc")
public final class TestDummyService {
    public static void spawn(ServerPlayer p){
        var arena=PracticeArena.arenaOf(p);
        if(arena==null||!TrainingGround.qualified(p)){p.sendSystemMessage(Component.literal("请先在演武场内以生存模式开始测试。"));return;}
        TrainingGround.dismissAll(p);
        int pad=TrainingGroundData.get(p.server).claim(p.getUUID(),arena.key());
        if(pad<0){p.sendSystemMessage(Component.literal("演武位已满。"));return;}
        var dummy=WarriorEntities.TEST_DUMMY.get().create(p.serverLevel());if(dummy==null)return;
        int x=TrainingGroundRules.padX(pad,arena.x()),z=TrainingGroundRules.padZ(pad,arena.z());
        dummy.moveTo(x+.5,arena.y()+1,z+2.5,180,0);dummy.getPersistentData().putUUID("DummyOwner",p.getUUID());dummy.resetStats();
        p.serverLevel().addFreshEntity(dummy);p.teleportTo(p.serverLevel(),x+.5,arena.y()+1,z-1.5,0,0);PracticeArena.settle(p);
        p.sendSystemMessage(Component.literal("测试木桩已就位：真实伤害统计，不发奖励。右键清零，潜行右键收起。"));TemperingEvents.sync(p);
    }
    public static void dismiss(ServerPlayer p){
        var remove=new java.util.ArrayList<TestDummyEntity>();
        for(var world:p.server.getAllLevels())for(var e:world.getAllEntities())if(e instanceof TestDummyEntity d&&d.getPersistentData().hasUUID("DummyOwner")&&d.mayUse(p))remove.add(d);
        remove.forEach(TestDummyEntity::discard);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent e){if(e.getEntity() instanceof TestDummyEntity dummy&&e.getAmount()>0){dummy.record(e.getAmount());if(e.getSource().getEntity() instanceof ServerPlayer p)p.displayClientMessage(Component.literal(String.format(java.util.Locale.ROOT,"木桩 · 本次 %.1f · %d次 · 合计 %.1f · DPS %.1f",e.getAmount(),dummy.hitCount(),dummy.totalDamage(),dummy.dps())),true);e.setAmount(Math.min(e.getAmount(),Math.max(0,dummy.getHealth()-1)));}}
}
