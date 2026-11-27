package com.tnc.tnc.sanctum;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.tnc.tnc.sanctum.FalseGodFight.flat;
import static com.tnc.tnc.sanctum.FalseGodRules.Move;
import static com.tnc.tnc.sanctum.SanctumTimings.*;

@GameTestHolder("tncsanctum")
@PrefixGameTestTemplate(false)
public final class SanctumFlightGameTests {
    private static FalseGodFight arena(GameTestHelper h) {
        return new FalseGodFight(h.getLevel(),h.absolutePos(new BlockPos(3,3,3)).atY(128));
    }
    private static ServerPlayer player(GameTestHelper h,Vec3 feet) {
        var p=SanctumGameTests.participant(h);
        p.setGameMode(GameType.SURVIVAL);
        for(int i=0;i<61;i++)p.tick();
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);p.setHealth(200);
        p.setPos(feet);return p;
    }
    private static String action(FalseGodFight f) {
        return f.body.getPersistentData().getCompound("TncSanctumAppearance").getString("Action");
    }
    @GameTest(template="empty",batch="sanctum_flight",timeoutTicks=80)
    public static void viewingDistanceStaysClearExceptDuringTheRealWingContact(GameTestHelper h) {
        var f=arena(h);var viewer=player(h,f.center);var party=List.of(viewer);
        var bridge=new FalseGodFightGameTests.TestDamageBridge(f);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(bridge);
        try {
            f.tick(party);f.phase=3;f.place(f.center.add(0,0,-DEMON_REACH));f.face(viewer.position());f.tick(party);
            h.assertTrue(flat(f.feet,f.wound.position())==15 && flat(viewer.position(),f.wound.position())==12
                    && !f.wound.getBoundingBox().inflate(2).contains(viewer.getEyePosition()),
                    "at 27 blocks the idle claw is 12 blocks away from the viewer and clear of their camera");
            f.begin(Move.WINGCUT);
            for(int t=1;t<=90;t++) {
                f.tick(party);
                if(t==16 || t==90)h.assertTrue(flat(f.wound.position(),viewer.position())>=9
                        && !f.wound.getBoundingBox().inflate(2).contains(viewer.getEyePosition()),
                        "the resting claw clears the shot both during the wind-up and on landing: "+t);
                if(t==WING_IMPACT) {
                    h.assertTrue(viewer.distanceToSqr(f.wound)<2.5*2.5,"a player on the real wing mark can reach the exposed tip");
                    float before=f.body.getHealth();viewer.attack(f.wound);
                    h.assertTrue(f.body.getHealth()<before,"the actual contact window forwards an ordinary melee hit to the boss");
                }
            }
            h.succeed();
        } finally {net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(bridge);f.close();viewer.discard();}
    }
    @GameTest(template="empty",batch="sanctum_flight",timeoutTicks=80)
    public static void airborneWingActuallyCrossesTheMarkedLaneAndMissesASidestep(GameTestHelper h) {
        var f=arena(h);var aim=f.center;
        var target=player(h,aim);var onLane=player(h,aim);var witness=player(h,aim.add(0,0,15));
        var party=List.of(target,onLane,witness);
        try {
            f.tick(party);f.phase=3;f.place(aim.add(0,0,-DEMON_REACH));
            f.begin(Move.WINGCUT);
            h.assertTrue(action(f).equals("wingcut_l"),"first side is bound to the animation packet");
            Vec3 wingIn=Vec3.ZERO;int contact=-1;int hits=0;double peak=0;
            for(int t=1;t<=WINGCUT;t++) {
                if(t==WING_LOCK+1)target.setPos(aim.add(0,0,6));
                float before=onLane.getHealth();onLane.invulnerableTime=0;
                Vec3 old=f.feet;f.tick(party);
                h.assertTrue(old.distanceTo(f.feet)<2.2,"the flying carrier moves continuously at tick "+t);
                if(before>onLane.getHealth()){hits++;contact=t;}
                if(t==WING_IN)wingIn=f.feet;
                if(t==WING_IMPACT)h.assertTrue(f.wound.position().distanceTo(aim)<.1,"real low wingtip reaches the marked centre");
                if(t>=WING_IN && t<=WING_OUT) {
                    double distance=flat(f.feet,aim);
                    h.assertTrue(distance>=24 && distance<=31,"visible distance during low flight: "+distance);
                }
                if(t==WING_OUT)h.assertTrue(flat(wingIn,f.feet)>=23.5,"carrier flies laterally across a full 24-block run");
                peak=Math.max(peak,f.feet.y-aim.y);
                h.assertTrue(witness.position().equals(aim.add(0,0,15)),"a bystander is never dragged along the flight");
            }
            h.assertTrue(peak>=8.9 && f.feet.y==aim.y && flat(f.feet,target.position())>=20,"ground takeoff, airborne stroke and offset landing");
            h.assertTrue(target.getHealth()==200 && witness.getHealth()==200 && hits==1 && contact>=WING_IMPACT-2 && contact<=WING_IMPACT,
                    "only the standing player receives a single timed wingtip hit, at "+contact);
            f.begin(Move.WINGCUT);h.assertTrue(action(f).equals("wingcut_r"),"the next pass banks to the opposite side");
            h.succeed();
        } finally {f.close();party.forEach(ServerPlayer::discard);}
    }
    @GameTest(template="empty",batch="sanctum_flight",timeoutTicks=80)
    public static void farHookCanBeDodgedThenPunishedWithAnOrdinaryMeleeAttack(GameTestHelper h) {
        var f=arena(h);var aim=f.center;
        var target=player(h,aim);var onMark=player(h,aim);var party=List.of(target,onMark);
        var bridge=new FalseGodFightGameTests.TestDamageBridge(f);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(bridge);
        try {
            f.tick(party);f.phase=3;f.place(aim.add(0,0,-DEMON_REACH));f.begin(Move.HOOK);
            h.assertTrue(action(f).equals("hook_l"),"left arm action starts on the server tick");
            for(int t=1;t<=HOOK_IMPACT;t++) {
                if(t==HOOK_LOCK+1)target.setPos(aim.add(4,0,0));
                f.tick(party);
                h.assertTrue(t==HOOK_IMPACT || onMark.getHealth()==200,"hook respects all warning frames");
            }
            h.assertTrue(target.getHealth()==200 && onMark.getHealth()<200 && onMark.getDeltaMovement().horizontalDistance()>.1,
                    "only players inside the palm warning take damage and the grounded shove");
            target.setPos(aim.add(1.5,0,0));
            h.assertTrue(flat(f.feet,aim)>=PALM_REACH-.05 && flat(f.feet,aim)<=PALM_REACH+.05
                    && target.distanceToSqr(f.wound)<9,"the body closes to palm reach and stops while the hand is in ordinary melee range");
            float before=f.body.getHealth();target.attack(f.wound);
            h.assertTrue(f.body.getHealth()<before,"vanilla player.attack on the extended claw really hurts the boss");
            for(int t=HOOK_IMPACT+1;t<HOOK_RELEASE;t++){f.tick(party);h.assertTrue(f.wound.position().distanceTo(aim)<.1,"hold open until retraction");}
            f.begin(Move.HOOK);h.assertTrue(action(f).equals("hook_r"),"next hook changes hands");
            h.succeed();
        } finally {net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(bridge);f.close();party.forEach(ServerPlayer::discard);}
    }
    @GameTest(template="empty",batch="sanctum_flight",timeoutTicks=100)
    public static void fourCornersKeepTheirLandingAndOnlyTheGrabTargetIsLifted(GameTestHelper h) {
        var f=arena(h);var target=player(h,f.center);var ally=player(h,f.center);
        var party=List.of(target,ally);
        try {
            f.tick(party);f.phase=3;
            for(int x:new int[]{-1,1})for(int z:new int[]{-1,1}) {
                var at=f.center.add(x*68,0,z*68);target.setPos(at);ally.setPos(at);
                var start=f.stance(at,f.center);f.place(start);
                ally.setPos(start.add(3,0,0));
                f.begin(Move.WINGCUT); f.target=target;
                for(int t=1;t<=WINGCUT;t++) {
                    f.tick(party);
                    h.assertTrue(Math.abs(f.feet.x-f.center.x)<=57.001 && Math.abs(f.feet.z-f.center.z)<=65.001,"root stays inside the piers and walls");
                }
                h.assertTrue(f.feet.y==at.y && flat(f.feet,at)>=22 && flat(f.feet,ally.position())>=14,
                        "corner landing has depth for the target and the other player's camera: "+x+","+z);
            }
            var at=f.center.add(42,0,-15);target.setPos(at);ally.setPos(at);f.place(at.add(0,0,-DEMON_REACH));
            f.body.setHealth(70);ally.invulnerableTime=target.invulnerableTime=0;f.begin(Move.GRAB);
            var held=f.target;var watching=held==target?ally:target;
            final float hp=watching.getHealth();var watchingFrom=watching.position();Vec3 wind=Vec3.ZERO;
            double highest=0;
            for(int t=1;t<=50;t++) {
                if(held.invulnerableTime>0)held.invulnerableTime--;
                f.tick(party);highest=Math.max(highest,held.getY()-at.y);
                if(t==22)wind=f.feet;
                if(t==30)h.assertTrue(flat(wind,f.feet)>5 && flat(f.feet,at)>=24,"real body lunges as the hand closes at range");
                h.assertTrue(watching.position().equals(watchingFrom) && watching.getHealth()==hp,"the bystander inside the same grip is never moved or drained");
            }
            h.assertTrue(highest>=3 && held.position().equals(at) && held.getHealth()<200 && f.body.getHealth()>70,
                    "captured player is lifted and released over their own tile while the demon really heals");
            f.begin(Move.GRAB);held=f.target;watching=held==target?ally:target;
            float untouched=watching.getHealth(),before=f.body.getHealth();
            var escaped=at.add(4,0,4);
            for(int t=1;t<=50;t++) {
                if(t==12)held.setPos(escaped);
                f.tick(party);
                h.assertTrue(watching.position().equals(at) && (t<12 || held.position().equals(escaped)),"dodged grab never switches to another player");
            }
            h.assertTrue(watching.getHealth()==untouched && before==f.body.getHealth(),"empty grab cannot heal or clip a bystander");
            target.setPos(f.center);ally.setPos(f.center.add(0,0,30));f.begin(Move.FUNERAL);
            for(int t=1;t<=130;t++){target.invulnerableTime=0;f.tick(party);}
            h.assertTrue(flat(f.feet,target.position())>=12 && flat(f.feet,ally.position())>=12 && f.feet.y==f.center.y,
                    "meteor impact keeps its central blast but banks away from occupied cameras on landing");
            h.succeed();
        } finally {f.close();party.forEach(ServerPlayer::discard);}
    }
}
