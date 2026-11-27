package com.tnc.tnc.sanctum;

import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.tnc.tnc.sanctum.FalseGodRules.Move;

@GameTestHolder("tncsanctum")
@PrefixGameTestTemplate(false)
public final class FalseGodFightGameTests {
    @GameTest(template="empty",batch="sanctum_skill_probes",timeoutTicks=40)
    public static void geometryGazeBloodAndExhaustionHaveRealCounterplay(GameTestHelper h) {
        var at=h.absolutePos(new BlockPos(2,3,2));
        var fight=new FalseGodFight(h.getLevel(),at);
        var near=SanctumGameTests.participant(h);
        var safe=SanctumGameTests.participant(h);
        var center=fight.center;
        try {
            for(var p:List.of(near,safe)) {
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                p.getAbilities().invulnerable=false;
                // These fixtures are not in the player list. Advance vanilla's 60-tick spawn protection.
                for(int t=0;t<61;t++)p.tick();
                p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
                p.setHealth(200);
            }
            place(near,center.add(0,0,-9));
            place(safe,near.position().add(3,0,3));
            fight.tick(List.of(near,safe));
            lock(fight,Move.PILLARS,near);
            for(int t=1;t<30;t++)fight.skills.run(Move.PILLARS,t);
            h.assertTrue(near.getHealth()==200,"gold markers never hit before 30 ticks");
            fight.skills.run(Move.PILLARS,30);
            h.assertTrue(near.getHealth()<200 && safe.getHealth()==200,
                    "cross hits only the marked footprint: marked="+near.getHealth()+", clear="+safe.getHealth());

            place(near,center.add(0,0,-8));place(safe,center.add(0,1,-8));
            lock(fight,Move.PRAYER,near);
            float before=near.getHealth();near.invulnerableTime=0;
            for(int t=14;t<44;t++)fight.skills.run(Move.PRAYER,t);
            h.assertTrue(near.getHealth()<before && safe.getHealth()==200,"grounded target hit, jumping player clears the same wave");

            place(safe,center.add(1,0,-9));safe.setYRot(180);near.setYRot(0);
            near.invulnerableTime=0;before=near.getHealth();
            lock(fight,Move.GAZE,near);fight.skills.run(Move.GAZE,30);
            h.assertTrue(near.getHealth()<before && near.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                            && safe.getHealth()==200 && !safe.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "only the player facing the eyes takes fear");
            lock(fight,Move.TEARS,near);fight.skills.run(Move.TEARS,28);
            h.assertTrue(!fight.pools.isEmpty(),"rain leaves persistent footprints");
            fight.phase=2;fight.recovery=90;fight.move=null;
            for(int i=0;i<40;i++){near.invulnerableTime=0;fight.tick(List.of(near,safe));}
            before=near.getHealth();near.invulnerableTime=0;
            lock(fight,Move.SCREAM,near);fight.skills.run(Move.SCREAM,30);
            h.assertTrue(fight.pools.isEmpty() && near.getHealth()<before,"detonation clears the pool after damaging its footprint");

            place(near,center.add(22,0,4));place(safe,center.add(46,0,4));fight.place(center);
            lock(fight,Move.COMBO,near);
            for(int t=1;t<=SanctumTimings.COMBO;t++) {
                if(t==2)place(near,center.add(22,0,-4));
                if(t==SanctumTimings.LOCK_TWO+1)place(near,center.add(20,0,4));
                if(t==SanctumTimings.HEAVY_IMPACT-1) {
                    place(safe,near.position());place(near,center.add(20,0,-8));safe.invulnerableTime=0;
                }
                float hp=near.getHealth(),avoided=safe.getHealth();
                if(t==SanctumTimings.CUT_ONE || t==SanctumTimings.CUT_TWO)near.invulnerableTime=20;
                fight.skills.run(Move.COMBO,t);
                boolean fast=t==SanctumTimings.CUT_ONE || t==SanctumTimings.CUT_TWO;
                h.assertTrue((near.getHealth()<hp)==fast && (safe.getHealth()<avoided)==(t==SanctumTimings.HEAVY_IMPACT),
                        "only the marked combatant takes the fast cuts after moving with roll frames; heavy hits its actual footprint at tick "+t);
                h.assertTrue(fight.feet.equals(center),"the angel never shifts its seat during the melee combo");
            }
            place(near,center.add(0,0,-9));
            place(safe,near.position());safe.invulnerableTime=near.invulnerableTime=0;
            fight.phase=3;fight.place(center);lock(fight,Move.DANCE,near);fight.skills.run(Move.DANCE,5);
            float nearHp=near.getHealth(),safeHp=safe.getHealth();
            place(near,center.add(8,0,-9));
            fight.skills.run(Move.DANCE,16);
            h.assertTrue(near.getHealth()==nearHp&&safe.getHealth()<safeHp,"ordinary demon claw can be sidestepped while someone on the trail is struck");
            place(near,center.add(0,0,-9));
            fight.phase=3;fight.place(center);lock(fight,Move.DANCE,near);
            fight.skills.run(Move.DANCE,70);
            place(near,center.add(12,0,-9));place(safe,center.add(12,0,-9));
            fight.skills.run(Move.DANCE,112);
            h.assertTrue(fight.skills.exhausted && fight.recovery==80 && fight.move==null,"dodged last slam gives exactly four seconds");
            for(int i=0;i<80;i++)fight.tick(List.of(near,safe));
            h.assertTrue(fight.skills.exhausted && fight.recovery==0,"the entire 80-tick damage window is available");
            fight.tick(List.of(near,safe));
            h.assertTrue(!fight.skills.exhausted && fight.move!=null,"boss resumes on the following tick");

            fight.body.setHealth(50);fight.place(center);
            place(near,center.add(0,0,-8));place(safe,center.add(13,0,0));
            near.invulnerableTime=0;before=near.getHealth();
            lock(fight,Move.GRAB,near);
            double peak=near.getY();
            for(int t=1;t<=50;t++) {
                if(near.invulnerableTime>0)near.invulnerableTime--;
                fight.skills.run(Move.GRAB,t);
                peak=Math.max(peak,near.getY());
            }
            h.assertTrue(peak>center.y+3 && near.getY()==center.y && fight.body.getHealth()>50
                    && near.getHealth()<before,"a connected grab lifts, drains actual health and slams");
            var training=com.tnc.tnc.warrior.TemperingData.get(near.server);
            training.setTalent(near.getUUID(),6);training.enroll(near.getUUID());
            near.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            com.tnc.tnc.warrior.WarriorComboService.step(near,false);
            h.assertTrue(fight.counter(near) && !fight.counter(near),"real quick-step input gives one precision evade");

            var outsider=AdventureGameTests.player(h);
            try {
                var trespass=new LivingHurtEvent(fight.body,h.getLevel().damageSources().playerAttack(outsider),5000);
                fight.hurt(trespass);h.assertTrue(trespass.isCanceled()&&!fight.defeated(),"unregistered attackers cannot finish the encounter");
            } finally {outsider.discard();}
            h.succeed();
        } finally {fight.close();near.discard();safe.discard();}
    }
    @GameTest(template="empty",batch="sanctum_gaze_eyes",timeoutTicks=40)
    public static void failingGazeReceivesThreeBleedsWithoutDarkness(GameTestHelper h) {
        var f=new FalseGodFight(h.getLevel(),h.absolutePos(new BlockPos(3,3,3)).atY(128));
        var eyes=SanctumGameTests.participant(h);
        var back=SanctumGameTests.participant(h);
        var side=SanctumGameTests.participant(h);
        var party=List.of(eyes,back,side);
        try {
            for(var p:party) {
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                for(int i=0;i<61;i++)p.tick();
                p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);p.setHealth(200);
                place(p,f.center.add(0,0,-16));
            }
            f.tick(party);f.phase=2;f.recovery=140;
            f.face(f.center.add(0,0,-1));
            eyes.setYRot(0);eyes.setXRot(0);back.setYRot(180);side.setYRot(90);
            lock(f,Move.GAZE,eyes);
            for(int i=1;i<=30;i++)f.skills.run(Move.GAZE,i);
            h.assertTrue(back.getHealth()==200 && !back.hasEffect(MobEffects.DARKNESS)
                    && eyes.getHealth()<200 && !eyes.hasEffect(MobEffects.DARKNESS)
                    && eyes.hasEffect(MobEffects.WEAKNESS) && eyes.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                    && side.getHealth()<200 && !side.hasEffect(MobEffects.DARKNESS),
                    "the player who turns away is safe and neither failed look applies darkness");
            f.move=null;
            for(int tick=1;tick<=72;tick++) {
                float hp=eyes.getHealth();for(var p:party)p.invulnerableTime=0;
                f.tick(party);
                boolean pulse=tick%SanctumTimings.BLEED_INTERVAL==0 && tick<=SanctumTimings.BLEED_INTERVAL*3;
                h.assertTrue((eyes.getHealth()<hp)==pulse && back.getHealth()==200,"three finite bleed ticks, only on failed players: "+tick);
            }
            lock(f,Move.GAZE,eyes);f.skills.run(Move.GAZE,30);f.move=null;
            float left=eyes.getHealth();
            for(int tick=1;tick<=20;tick++)f.tick(List.of(back));
            for(int tick=1;tick<=60;tick++)f.tick(party);
            h.assertTrue(eyes.getHealth()==left,"leaving the affected group clears the pending bleed before re-entry");
            h.succeed();
        }finally{f.close();party.forEach(ServerPlayer::discard);}
    }
    @GameTest(template="empty",batch="sanctum_skill_probes",timeoutTicks=40)
    public static void burstAcrossBothThresholdsCannotSkipTheFirstFrenzy(GameTestHelper h) {
        var fight=new FalseGodFight(h.getLevel(),h.absolutePos(new BlockPos(2,2,2)));
        var p=SanctumGameTests.participant(h);
        var bridge=new TestDamageBridge(fight);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(bridge);
        try {
            place(p,fight.center.add(0,0,-8));
            var party=List.of(p);
            fight.tick(party);
            strike(fight,p);
            h.assertTrue(fight.phase==2,"real first-stage lethal switches to blood");
            for(int phase=2;phase<=3;phase++) {
                if(phase==3) {
                    var label=fight.bar.getName();
                    h.assertTrue(label.getString().equals("厄瑞玻斯·萨拉托斯·维尔米翁")
                                    && label.getStyle().isBold() && label.getStyle().getColor()!=null
                                    && label.getStyle().getColor().getValue()==SanctumAppearance.TRUE_NAME_RED,
                            "server publishes the full blood-red, bold true name");
                }
                int film=phase==2?SanctumTimings.AWAKEN:SanctumTimings.RUPTURE;
                if(phase==3)place(p,fight.center.add(0,0,-1));
                Vec3 viewer=p.position();
                h.assertTrue(fight.body.getPersistentData().getCompound("TncSanctumAppearance").getInt("Duration")==film,
                        "remote animation and server protection use the same transition length");
                for(int t=0;t<film;t++){
                    strike(fight,p);fight.tick(party);
                    h.assertTrue(fight.body.getHealth()==fight.body.getMaxHealth(),"entire close-up is protected");
                    if(phase==3) {
                        h.assertTrue(p.position().equals(viewer),"the reveal never relocates its viewer");
                        if(t<SanctumTimings.SHELL_SPLIT+5)h.assertTrue(fight.feet.equals(fight.center),
                                "the old shell stays rooted through the hand and tear shots");
                        else h.assertTrue(FalseGodFight.flat(fight.feet,viewer)>=14,
                                "after the blackout the demon begins outside the player's camera");
                    }
                }
                float full=fight.body.getHealth();
                fight.body.setAbsorptionAmount(10_000);strike(fight,p);fight.tick(party);
                h.assertTrue(fight.phase==phase && fight.body.getHealth()==full && fight.move==null,
                        "completely absorbed hit cannot trigger a phase action");
                fight.body.setAbsorptionAmount(0);
                strike(fight,p);
                h.assertTrue(Math.abs(fight.body.getHealth()-full*.3f)<.01,"lethal hit first stops at 30 percent");
                strike(fight,p);strike(fight,p);
                h.assertTrue(fight.phase==phase && fight.body.getHealth()>=1 && !fight.defeated(),
                        "same tick follow-ups cannot skip the first move");
                int impact=phase==2?SanctumTimings.HEAVY_IMPACT:SanctumTimings.DANCE_IMPACT;
                fight.tick(party);
                for(int t=1;t<impact;t++) {
                    fight.tick(party);strike(fight,p);
                    h.assertTrue(fight.move==(phase==2?Move.COMBO:Move.DANCE) && !fight.defeated(),
                            "first frenzy lives through telegraphs: stage "+phase+" tick "+t);
                    if(phase==2)h.assertTrue(fight.feet.equals(fight.center),"seated rig keeps its foot and pedestal through both cuts and the delayed stamp");
                    if(phase==3 && t>=5 && t<=64)h.assertTrue(FalseGodFight.flat(fight.feet,p.position())>=14,
                            "claw attack crosses the target without planting the demon on the camera");
                }
                if(phase==3)place(p,fight.center.add(18,0,8));
                fight.tick(party);
                if(phase==3)h.assertTrue(fight.skills.exhausted && fight.recovery==80,"punish opens on resolved final slam");
                h.assertTrue(fight.wound!=null && fight.wound.getY()==fight.center.y,
                        "exposed impact is a ground-level target");
                fight.body.invulnerableTime=0;
                fight.wound.hurt(fight.level.damageSources().playerAttack(p),4000);
            }
            h.assertTrue(fight.defeated(),"finisher damage during exhaustion can win");
            h.succeed();
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(bridge);
            fight.close();p.discard();
        }
    }
    private static void strike(FalseGodFight f,ServerPlayer p) {
        f.body.invulnerableTime=0;
        f.body.hurt(f.level.damageSources().playerAttack(p),4000);
    }
    /** The fixture's miniature arena is in the GameTest dimension, outside the live session registry. */
    public static final class TestDamageBridge {
        final FalseGodFight fight;
        TestDamageBridge(FalseGodFight fight) {this.fight=fight;}
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void hurt(LivingHurtEvent e){if(fight.owns(e.getEntity().getUUID()))fight.hurt(e);}
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void damaged(net.minecraftforge.event.entity.living.LivingDamageEvent e){
            if(fight.owns(e.getEntity().getUUID()))fight.damaged(e);
        }
    }
    private static void lock(FalseGodFight fight,Move move,ServerPlayer target) {
        fight.move=move;fight.target=target;fight.skills.begin(move);
    }
    private static void place(ServerPlayer player,Vec3 at){player.setPos(at.x,at.y,at.z);}
}
