package com.tnc.tnc.bosshud;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder("tncbosshud")
@PrefixGameTestTemplate(false)
public final class BossHudGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=40,batch="hud_roster")
    public static void everyRegisteredBossResolvesItsActualBarAndTheCorrectPhase(GameTestHelper h){
        var bars=new java.util.HashSet<java.util.UUID>();
        for(var theme:BossHudTheme.values()){
            var type=ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(theme.type()));h.assertTrue(type!=null,"Missing registered boss "+theme.type());
            var entity=(LivingEntity)type.create(h.getLevel());h.assertTrue(entity!=null,"Cannot construct "+theme.type());
            entity.setHealth(entity.getMaxHealth()*.4F);var snapshot=ServerBossHud.inspect(entity);
            h.assertTrue(snapshot!=null&&snapshot.type().equals(theme.type()),"Missing real boss-bar mapping "+theme.type());
            h.assertTrue(bars.add(snapshot.bar()),"Different boss instances share a bar identity");
            h.assertTrue(Math.abs(snapshot.health()/snapshot.maximum()-.4)<.001,"Wrong current/maximum health");
            h.assertTrue(snapshot.phase()==theme.initialPhase&&snapshot.phases()==theme.phases,"Wrong encounter phase for "+theme.type());
            h.assertTrue(ServerBossHud.ownsBar(entity)==(theme==BossHudTheme.WIND_GENERAL),"Duplicated an existing vanilla boss event");
            ServerBossHud.leave(new net.minecraftforge.event.entity.EntityLeaveLevelEvent(entity,h.getLevel()));
        }
        h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40,batch="hud_identity")
    public static void renamedDuplicateBossesKeepIndependentBarIdsAndHealth(GameTestHelper h){
        var type=ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse("tnc:apollo"));
        var a=(LivingEntity)type.create(h.getLevel());var b=(LivingEntity)type.create(h.getLevel());
        a.setCustomName(Component.literal("SAME CUSTOM NAME"));b.setCustomName(Component.literal("SAME CUSTOM NAME"));float first=a.getMaxHealth()*.2F,second=b.getMaxHealth()*.8F;a.setHealth(first);b.setHealth(second);
        var one=ServerBossHud.inspect(a);var two=ServerBossHud.inspect(b);
        h.assertTrue(!one.bar().equals(two.bar())&&!one.entity().equals(two.entity()),"Renaming merged independent bosses");
        h.assertTrue(Math.abs(one.health()-first)<.001&&Math.abs(two.health()-second)<.001,"Independent boss health was mixed up");
        ServerBossHud.leave(new net.minecraftforge.event.entity.EntityLeaveLevelEvent(a,h.getLevel()));ServerBossHud.leave(new net.minecraftforge.event.entity.EntityLeaveLevelEvent(b,h.getLevel()));h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40,batch="hud_network")
    public static void identityPacketRoundTripsWithoutDependingOnEntityVisibility(GameTestHelper h){
        var p=new BossHudNetwork.Snapshot(java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),"tnc:ink_celestial_dragon",321,1000,3,3);
        var buffer=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{BossHudNetwork.write(p,buffer);h.assertTrue(p.equals(BossHudNetwork.read(buffer)),"Boss HUD packet lost identity or phase");}finally{buffer.release();}h.succeed();
    }
}
