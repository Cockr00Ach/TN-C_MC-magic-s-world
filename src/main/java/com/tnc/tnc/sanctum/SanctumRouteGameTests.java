package com.tnc.tnc.sanctum;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tncsanctum")
@PrefixGameTestTemplate(false)
public final class SanctumRouteGameTests {
    @GameTest(template="empty",batch="sanctum_mob_flight",timeoutTicks=80)
    public static void gargoyleHitsAndClimbsFromContactWithoutSnappingBack(GameTestHelper h){
        var level=h.getLevel();var p=SanctumGameTests.participant(h);
        BlockPos floor=h.absolutePos(new BlockPos(2,4,2));var route=new SanctumRoute(level,floor.offset(0,0,-25));
        var perch=route.foot(-4,25);var mark=route.foot(3,28);p.setPos(mark.x,mark.y,mark.z);
        Mob carrier=route.spawn(net.minecraft.world.entity.EntityType.IRON_GOLEM,"gargoyle","檐狱石像鬼",perch,28);
        var gargoyle=new SanctumGalleryFoe(route,carrier,true,0);var moves=new java.util.HashSet<String>();
        double peak=perch.y;net.minecraft.world.phys.Vec3 impact=null,airborne=null;
        try{
            for(int tick=1;tick<=165;tick++){
                var before=carrier.position();gargoyle.tick(List.of(p),p);var after=carrier.position();
                h.assertTrue(before.distanceTo(after)<=1.57,"continuous movement, tick "+tick);
                moves.add(carrier.getPersistentData().getCompound("TncSanctumAppearance").getString("Action"));
                peak=Math.max(peak,after.y);
                if(tick==58){impact=after;h.assertTrue(after.distanceTo(mark)<1.3,"contact at the indicated landing tile");}
                if(tick==70)gargoyle.hurt(new LivingHurtEvent(carrier,level.damageSources().playerAttack(p),2));
                if(tick==108)h.assertTrue(after.distanceTo(impact)<1,"exposure and hit reaction stay at contact");
                if(tick==140)airborne=after;
            }
            h.assertTrue(peak>perch.y+3 && carrier.position().distanceTo(perch)>2.5,"climb starts locally and does not return to spawn");
            h.assertTrue(carrier.position().distanceTo(airborne)>1,"the gargoyle banks from its new position after climbing");
            h.assertTrue(moves.containsAll(Set.of("stone_windup","stone_dive","stone_land","stone_climb","stone_flight")),"all authored flight phases played");
        }finally{route.discard(carrier);route.close();}
        h.succeed();
    }
    @GameTest(template="empty",batch="sanctum_gate_pair",timeoutTicks=1150)
    public static void partnerCanBreakEitherWristAndARetryRestoresBothGatekeepers(GameTestHelper h) {
        var level=h.getLevel();var a=SanctumGameTests.participant(h);var b=SanctumGameTests.participant(h);
        // The template only clears its own footprint. The two aisles extend into untouched world terrain.
        var origin=h.absolutePos(new BlockPos(2,0,2)).atY(level.getMaxBuildHeight()-48).offset(0,0,-100);
        var deck=new java.util.HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(int x=-17;x<=17;x++)for(int z=97;z<=111;z++){
            var pos=origin.offset(x,-1,z);deck.put(pos,level.getBlockState(pos));
            level.setBlock(pos,net.minecraft.world.level.block.Blocks.DEEPSLATE_TILES.defaultBlockState(),2);
        }
        var pair=List.of(a,b);var approach=new SanctumRoute(level,origin);var gate=new SanctumGateCombat(approach);
        var left=origin.offset(-8,0,100);var right=origin.offset(8,0,100);
        a.setPos(left.getX()+.5,left.getY(),left.getZ()+.5);b.setPos(right.getX()+.5,right.getY(),right.getZ()+.5);
        approach.tick(pair);
        AABB court=new AABB(origin.offset(-21,-2,91),origin.offset(22,15,115));
        class Encounter implements Runnable {
            int tries=0,hits=0;UUID worn,first;
            boolean done;
            public void run(){
                if(done)return;
                a.setHealth(a.getMaxHealth());b.setHealth(b.getMaxHealth());
                boolean cleared=gate.tick(pair);
                var alive=level.getEntitiesOfClass(Mob.class,court,m->approach.owns(m.getUUID())&&m.isAlive());
                if(!cleared&&hits==0){
                    var sides=alive.stream().filter(m->m.getName().getString().contains("庭门神")).toList();
                    h.assertTrue(sides.size()==2,"two independently targetable wardens");
                    h.assertTrue(sides.stream().map(m->m.getPersistentData().getCompound("TncSanctumAppearance").getString("Model")).collect(java.util.stream.Collectors.toSet())
                            .equals(Set.of("gate_left","gate_right")),"the pair has distinct model roles");
                }
                for(Mob cuff:alive){
                    if(!cuff.getName().getString().equals("落地腕锁")||cuff.getUUID().equals(worn))continue;
                    worn=cuff.getUUID();var breaker=cuff.getX()<origin.getX()?b:a;
                    if(tries==0){
                        first=worn;gate.reset();tries++;
                        h.assertTrue(cuff.isRemoved(),"retry removes the active wrist");return;
                    }
                    h.assertTrue(!worn.equals(first),"new attempt owns new combat carriers");
                    h.assertTrue((hits==0)==(cuff.getX()<origin.getX()),"left unlocks before right");
                    var event=new LivingHurtEvent(cuff,level.damageSources().playerAttack(breaker),500);
                    h.assertTrue(gate.hurt(event)&&event.getAmount()<=1,"partner breaks the marked player's wrist");
                    hits++;
                }
                if(cleared){
                    done=true;approach.close();deck.forEach((pos,state)->level.setBlock(pos,state,2));
                    h.assertTrue(hits==2&&tries==1,"both wardens defeated on second attempt, hits="+hits+", retries="+tries);h.succeed();
                }
            }
        }
        h.onEachTick(new Encounter());
    }
}

