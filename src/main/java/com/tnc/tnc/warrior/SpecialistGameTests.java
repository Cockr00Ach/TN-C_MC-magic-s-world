package com.tnc.tnc.warrior;
import com.tnc.tnc.adventure.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
@GameTestHolder("tncwarrior") @PrefixGameTestTemplate(false)
public final class SpecialistGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=750) public static void shieldInstructorCountsFiveActualBlocks(GameTestHelper h){exercise(h,"guard");}
    @GameTest(template="building_test_empty",timeoutTicks=750) public static void bladeInstructorRequiresChargedMeleeHits(GameTestHelper h){exercise(h,"blade");}
    @GameTest(template="building_test_empty",timeoutTicks=1000) public static void windInstructorAcceptsThreeActualRangedCuts(GameTestHelper h){exercise(h,"wind");}
    private static void exercise(GameTestHelper h,String role){
        var p=AdventureGameTests.player(h);var source=h.absolutePos(new BlockPos(3,3,3));h.getLevel().setBlock(source.below(),Blocks.STONE.defaultBlockState(),3);p.setPos(Vec3.atBottomCenterOf(source));p.setOnGround(true);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));
        var data=TemperingData.get(p.server);data.setTalent(p.getUUID(),2);if(role.equals("wind")){data.set(p.getUUID(),200);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,WindInfusion.infuse(p.getMainHandItem()));}
        var guild=new TownServiceNpc(EntityType.VILLAGER,h.getLevel());guild.role("guild");guild.moveTo(p.position().add(1,0,0));h.getLevel().addFreshEntity(guild);
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(net.minecraft.server.level.ServerPlayer.class,p,0,"f_8921_");
        h.assertTrue(TrainingGround.enter(p),"Entry requested");boolean[] started={false},done={false},cast={false};int[] ticks={0};int original=data.points(p.getUUID());float health=p.getHealth();
        h.onEachTick(()->{
            if(done[0]||!TrainingGround.inside(p))return;
            var teachers=SpecialistTrainers.trainers(p);h.assertTrue(teachers.size()==3,"Three distinct specialist instructors spawned");
            var t=teachers.stream().filter(e->role.equals(e.getPersistentData().getString(SpecialistTrainers.ROLE))).findFirst().orElseThrow();
            p.setPos(t.getX(),2,t.getZ()-(started[0]&&role.equals("wind")?4:2));p.setYRot(0);p.setXRot(0);p.setDeltaMovement(Vec3.ZERO);
            if(!started[0]){
                h.assertTrue(SpecialistTrainers.begin(p,t),"Owner starts specific lesson");started[0]=true;
                if(role.equals("guard"))p.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
            }
            // Fake players do not keep their private plot entity-ticking. Advance the
            // instructor too, including vanilla hurt immunity, as a live player's chunk does.
            p.tick();p.doTick();t.tick();ticks[0]++;
            if(role.equals("blade")&&ticks[0]%30==0)p.attack(t);
            if(role.equals("wind")&&(ticks[0]==2||ticks[0]%170==0))cast[0]=WindInfusion.cast(p);
            if(ticks[0]==600&&SpecialistTrainingData.get(p.server).count(p.getUUID(),role)==0)h.fail("Stalled "+SpecialistTrainers.diagnostic(p)+" attack="+p.getAttackStrengthScale(.5f)+" cast="+cast[0]+" clock="+p.server.overworld().getGameTime()+" position="+p.position()+" target="+t.position()+" targetHealth="+t.getHealth()+" look="+p.getLookAngle());
            if(SpecialistTrainingData.get(p.server).count(p.getUUID(),role)==0)return;
            done[0]=true;
            try{
                h.assertTrue(data.points(p.getUUID())==original&&p.getHealth()==health,"Specialist completion adds no growth and preserves life");
                h.assertTrue(!data.guardGraduate(p.getUUID()),"Specialist cannot consume main drill's first reward");
                var saved=SpecialistTrainingData.load(SpecialistTrainingData.get(p.server).save(new net.minecraft.nbt.CompoundTag()));h.assertTrue(saved.count(p.getUUID(),role)==1,"Specialist record survives reload");
                h.assertTrue(TrainingGround.leave(p),"Can leave after lesson");h.assertTrue(teachers.stream().allMatch(Entity::isRemoved),"All owned specialists removed on exit");h.succeed();
            }finally{guild.discard();p.discard();}
        });
    }
}
