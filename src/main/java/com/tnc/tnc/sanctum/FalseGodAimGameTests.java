package com.tnc.tnc.sanctum;

import com.tnc.tnc.magic.water.WaterSpellRules;
import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder("tncsanctum")
@PrefixGameTestTemplate(false)
public final class FalseGodAimGameTests {
    private static LivingEntity pick(ServerPlayer viewer,Vec3 aim,double distance) {
        Vec3 eye=viewer.getEyePosition(),look=aim.subtract(eye).normalize(),end=eye.add(look.scale(distance));
        viewer.setYRot((float)Math.toDegrees(Math.atan2(-look.x,look.z)));
        viewer.setXRot((float)-Math.toDegrees(Math.asin(look.y)));
        var hit=ProjectileUtil.getEntityHitResult(viewer,eye,end,viewer.getBoundingBox().expandTowards(look.scale(distance)).inflate(1),
                entity->entity.isPickable() && !entity.isSpectator(),distance*distance);
        return hit!=null && hit.getEntity() instanceof LivingEntity selected ? selected:null;
    }
    private static boolean hit(ServerPlayer player,Entity target) {
        if(target==null)return false;
        player.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(target,false));
        return true;
    }
    private static void shootWaterBall(FalseGodFight f,ServerPlayer caster,Vec3 aim) {
        var bolt=TNOrbEntities.WATER_BOLT.get().create(f.level);
        Vec3 origin=caster.getEyePosition(),direction=aim.subtract(origin).normalize();
        bolt.configure(caster,1,origin,direction.scale(1.7));
        f.level.addFreshEntity(bolt);
        for(int tick=0;tick<40 && !bolt.isRemoved();tick++)bolt.tick();
        bolt.discard();
    }
    @GameTest(template="empty",batch="sanctum_aim",timeoutTicks=80)
    public static void firstPhaseCanBePickedAndStruckAtTheVisibleFrontOfThePedestal(GameTestHelper h) {
        var level=h.getLevel();
        // X=16n deliberately puts the base across two entity sections. Y=128 keeps the reference rays clear.
        var a=h.absolutePos(new BlockPos(2,2,2));
        var f=new FalseGodFight(level,new BlockPos((a.getX()>>4)*16,128,(a.getZ()>>4)*16));
        var p=SanctumGameTests.participant(h);
        var bridge=new FalseGodFightGameTests.TestDamageBridge(f);
        MinecraftForge.EVENT_BUS.register(bridge);
        try {
            p.setGameMode(GameType.SURVIVAL);
            p.setPos(f.center.add(6,0,-12.6));f.tick(List.of(p));
            var stone=pick(p,f.center.add(6,1.5,-9.5),3);
            h.assertTrue(stone!=null,"survival crosshair missed the front face of the 22-block plinth at normal sword reach");
            float hp=f.body.getHealth();
            hit(p,stone);
            h.assertTrue(f.body.getHealth()<hp,"the selected plinth face receives the real serverbound attack packet");
            p.setPos(f.center.add(0,51,-17));
            Vec3 eye=f.center.add(0,52,-2);
            var head=pick(p,eye,23);
            h.assertTrue(head instanceof FalseGodPart && head.getTags().contains("tnc_false_god_part_head")
                    && WaterSpellRules.enemy(p,head),"a shot at eye height finds the actual hostile head");
            f.body.invulnerableTime=0;hp=f.body.getHealth();
            shootWaterBall(f,p,eye);
            h.assertTrue(f.body.getHealth()<hp,"a real tier-one water ball aimed at the head reduces the same health bar");
            var mirror=new FalseGodPart(FalseGodPart.TYPE,level);
            mirror.getEntityData().assignValues(head.getEntityData().getNonDefaultValues());
            mirror.setPos(head.position());
            h.assertTrue(mirror.isPickable() && mirror.getBoundingBox().equals(head.getBoundingBox()),
                    "fresh network tracker reconstructs exact XYZ hit dimensions");
            ((FalseGodPart)head).hide();
            h.assertTrue(!head.isAlive() && !head.isPickable() && !WaterSpellRules.enemy(p,head),
                    "dormant slots cannot intercept a water bolt");
            com.mojang.logging.LogUtils.getLogger().info("Sanctum aim tested plinth {} head {} phase-one active boxes {}",
                    stone.getTags(),head.getTags(),f.contacts.activeCount());
            h.succeed();
        }finally{MinecraftForge.EVENT_BUS.unregister(bridge);f.close();p.discard();}
    }
    @GameTest(template="empty",batch="sanctum_phase_aim",timeoutTicks=80)
    public static void threePhasesShareDamageWithOrdinarySurvivalAndCreativeAim(GameTestHelper h) {
        var anchor=h.absolutePos(new BlockPos(3,2,2));
        var f=new FalseGodFight(h.getLevel(),new BlockPos(anchor.getX(),128,anchor.getZ()));
        var p=SanctumGameTests.participant(h);
        var bridge=new FalseGodFightGameTests.TestDamageBridge(f);
        MinecraftForge.EVENT_BUS.register(bridge);
        try {
            p.setPos(f.center.add(0,0,-22));f.tick(List.of(p));
            for(int phase=1;phase<=3;phase++){
                if(phase>1){
                    h.assertTrue(f.advanceRehearsal(),"stage starts its actual reveal");
                    float full=f.body.getHealth();
                    f.body.hurt(f.level.damageSources().playerAttack(p),20);
                    h.assertTrue(f.body.getHealth()==full && f.contacts.activeCount()==0,"transition is protected");
                    while(f.transformation>0)f.tick(List.of(p));
                    f.tick(List.of(p));
                }
                Vec3 at=f.wound.position().add(0,1.1,0);
                for(GameType mode:List.of(GameType.SURVIVAL,GameType.CREATIVE)){
                    p.setGameMode(mode);
                    Vec3 away=at.subtract(f.feet).multiply(1,0,1).normalize();
                    p.setPos(f.wound.position().add(away.scale(2.3)));
                    var selected=pick(p,at,3);
                    h.assertTrue(selected!=null && WaterSpellRules.enemy(p,selected),"unassisted hostile target at melee range, phase="+phase+" mode="+mode);
                    f.body.invulnerableTime=0;
                    float hp=f.body.getHealth();
                    hit(p,selected);
                    h.assertTrue(f.body.getHealth()<hp,"normal attack packet damages phase="+phase+" mode="+mode+" target="+selected.getTags());
                }
                // A second selection of the same area during one splash must share the root's damage cooldown.
                f.body.invulnerableTime=0;
                float hp=f.body.getHealth();
                var magic=f.level.damageSources().indirectMagic(p,p);
                f.wound.hurt(magic,3);
                float after=f.body.getHealth();
                f.body.hurt(magic,3);
                h.assertTrue(after<hp && after==f.body.getHealth(),"splash overlapping proxy and root does not multiply");
                com.mojang.logging.LogUtils.getLogger().info("Sanctum aim phase {} ground seal {} active regions {}",
                        phase,f.wound.blockPosition(),f.contacts.activeCount());
            }
            h.succeed();
        }finally{MinecraftForge.EVENT_BUS.unregister(bridge);f.close();p.discard();}
    }
    private FalseGodAimGameTests(){}
}
