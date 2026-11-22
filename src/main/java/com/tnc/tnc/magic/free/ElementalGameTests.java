package com.tnc.tnc.magic.free;

import com.tnc.tnc.adventure.*;
import com.tnc.tnc.magic.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncacademy")
@PrefixGameTestTemplate(false)
public final class ElementalGameTests {
    @GameTest(template="building_test_empty",batch="elemental_fire_charge")
    public static void fireballWaitsThenSpendsAndHits(GameTestHelper h){
        var p=player(h,100);var t=new Target(h,p);cast(p,701,"火球");
        h.runAfterDelay(20,()->h.assertTrue(mana(p)==100&&t.hits==0,"Fire dealt damage or charged during formation"));
        h.runAfterDelay(46,()->{int hits=t.hits,left=mana(p);clean(p,t);h.assertTrue(hits>=1&&left==88,"Expected fireball hit and one 12-mana debit, got "+hits+"/"+left);h.succeed();});
    }
    @GameTest(template="building_test_empty",batch="elemental_fire_channel",timeoutTicks=140)
    public static void stopEndsChannelAndFurtherDebits(GameTestHelper h){
        var p=player(h,100);var t=new Target(h,p);cast(p,702,"持续喷火");int[] remaining={0};
        h.runAfterDelay(55,()->{FreeMagicServer.input(p,new FreeMagicNetwork.Input(FreeMagicNetwork.STOP,702,""));remaining[0]=mana(p);h.assertTrue(remaining[0]<86,"Channel failed to charge maintenance");});
        h.runAfterDelay(110,()->{int left=mana(p),active=FireMagicEngine.count(p);clean(p,t);h.assertTrue(left==remaining[0]&&active==0,"Stopped channel continued charging or remained active");h.succeed();});
    }
    @GameTest(template="building_test_empty",batch="elemental_fire_point_blank")
    public static void formationDoesNotSkipANearbyEnemy(GameTestHelper h){
        var p=player(h,100);var t=new Target(h,p);t.setPos(p.position().add(0,1,1.9));cast(p,709,"火球");
        h.runAfterDelay(44,()->{int hits=t.hits,left=mana(p);clean(p,t);h.assertTrue(hits>=1&&left==88,"Fireball formation spawned past a point-blank target");h.succeed();});
    }
    @GameTest(template="building_test_empty",batch="elemental_fire_refresh",timeoutTicks=240)
    public static void durableIgnitionExtendsExistingBurn(GameTestHelper h){
        var p=player(h,100);var t=new Target(h,p);cast(p,703,"点燃");
        h.runAfterDelay(30,()->cast(p,704,"持久点燃"));
        h.runAfterDelay(190,()->{boolean burning=FireMagicEngine.burning(p,t);int left=mana(p);clean(p,t);h.assertTrue(burning&&left==77,"Durable refresh expired early or debited incorrectly: "+left);h.succeed();});
    }
    @GameTest(template="building_test_empty",batch="elemental_fire_volley")
    public static void targetedVolleyOnlyChargesDistinctAvailableEnemies(GameTestHelper h){
        var p=player(h,100);var t=new Target(h,p);cast(p,705,"齐发点燃");
        h.runAfterDelay(35,()->{int left=mana(p);boolean burning=FireMagicEngine.burning(p,t);clean(p,t);h.assertTrue(left==90&&burning,"Single enemy was charged for duplicate debuff targets: "+left);h.succeed();});
    }
    @GameTest(template="building_test_empty",batch="elemental_fire_detonate",timeoutTicks=140)
    public static void detonationConsumesExistingBurn(GameTestHelper h){
        var p=player(h,100);var t=new Target(h,p);cast(p,706,"点燃");
        h.runAfterDelay(30,()->cast(p,707,"燃爆"));
        h.runAfterDelay(80,()->{boolean burning=FireMagicEngine.burning(p,t);int hits=t.hits,left=mana(p);clean(p,t);h.assertTrue(!burning&&hits>=2&&left==72,"Detonation failed to consume its mark or debit exactly once");h.succeed();});
    }
    @GameTest(template="building_test_empty",batch="elemental_fire_wall")
    public static void blockedSideOfGiantVolleyRejectsBeforeDebit(GameTestHelper h){
        var p=player(h,100);Vec3 center=ElementalTargets.origin(p,3.2),side=center.add(WaterCastGeometry.right(p.getLookAngle()).scale(1.6*3.2));
        BlockPos blocked=BlockPos.containing(side);h.getLevel().setBlockAndUpdate(blocked,Blocks.STONE.defaultBlockState());
        String failure=ElementalRuntime.prepare(p,708,ElementalIntent.parse("巨大齐发火球",Element.FIRE).orElseThrow());
        h.getLevel().setBlockAndUpdate(blocked,Blocks.AIR.defaultBlockState());clean(p);
        h.assertTrue(!failure.isEmpty()&&mana(p)==100,"Blocked side projectile was allowed or charged");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="elemental_fire_wide_wall")
    public static void wideProjectileCannotHitAcrossSideWall(GameTestHelper h){
        var p=player(h,100);var t=new Target(h,p);t.setPos(p.position().add(2,1,6));
        int wallX=(int)Math.floor(p.getX())+1,y=(int)p.getY(),z=(int)p.getZ();
        for(int j=0;j<4;j++)for(int k=0;k<12;k++)h.getLevel().setBlockAndUpdate(new BlockPos(wallX,y+j,z+k),Blocks.STONE.defaultBlockState());
        var hits=ElementalTargets.segment(p,p.getEyePosition(),p.getEyePosition().add(0,0,10),2.5,java.util.Set.of());
        for(int j=0;j<4;j++)for(int k=0;k<12;k++)h.getLevel().setBlockAndUpdate(new BlockPos(wallX,y+j,z+k),Blocks.AIR.defaultBlockState());
        clean(p,t);h.assertTrue(hits.isEmpty(),"Expanded projectile bounds struck an enemy across a solid side wall");h.succeed();
    }
    private static ServerPlayer player(GameTestHelper h,int mana){
        var p=AdventureGameTests.player(h);Vec3 base=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,3,2)));
        p.setPos(base.x,h.getLevel().getMaxBuildHeight()-16,base.z);p.setYRot(0);p.setXRot(0);
        p.setItemInHand(InteractionHand.MAIN_HAND,ElementWands.stack(ElementWands.find("fire_wand_3")));
        var data=MagicStone.getOrNull(p);data.setAffinity(Element.FIRE,3);data.setMaxMana(100);data.setMana(mana);return p;
    }
    private static int mana(ServerPlayer p){return MagicStone.getOrNull(p).getMana();}
    private static void cast(ServerPlayer p,long token,String text){FreeMagicServer.input(p,new FreeMagicNetwork.Input(FreeMagicNetwork.BEGIN,token,""));FreeMagicServer.input(p,new FreeMagicNetwork.Input(FreeMagicNetwork.FINAL,token,text));}
    private static void clean(ServerPlayer p,Target... targets){FreeMagicServer.logout(new PlayerEvent.PlayerLoggedOutEvent(p));ElementalRuntime.logout(new PlayerEvent.PlayerLoggedOutEvent(p));for(var t:targets)t.discard();}
    private static final class Target extends Pig {
        final ServerPlayer owner;int hits;
        Target(GameTestHelper h,ServerPlayer owner){super(EntityType.PIG,h.getLevel());this.owner=owner;setNoAi(true);setNoGravity(true);setPos(owner.position().add(0,1,6));h.getLevel().addFreshEntity(this);}
        @Override public boolean hurt(DamageSource source,float damage){if(source.getEntity()==owner)hits++;return false;}
    }
}
