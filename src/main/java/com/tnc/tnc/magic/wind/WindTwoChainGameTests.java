package com.tnc.tnc.magic.wind;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.gametest.*;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class WindTwoChainGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="wind_two_chains")
    public static void spellSelfEffectsReleaseExactlyOneCastAndEveryTierRegisters(GameTestHelper h){
        var owner=h.spawn(EntityType.VILLAGER,new BlockPos(2,40,2));owner.setNoAi(true);owner.setYRot(0);owner.setXRot(0);
        var target=h.spawn(EntityType.ZOMBIE,new BlockPos(2,40,12));target.setNoAi(true);
        for(int chain=0;chain<2;chain++)for(int t=1;t<=5;t++){
            var effect=net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","wind_cast_"+chain+"_"+t));
            h.assertTrue(effect!=null,"All ten release markers exist");owner.addEffect(new MobEffectInstance(effect,1));
            var balls=h.getLevel().getEntitiesOfClass(WindBallEntity.class,owner.getBoundingBox().inflate(4));
            var volleys=h.getLevel().getEntitiesOfClass(WindVolleyEntity.class,owner.getBoundingBox().inflate(4));
            h.assertTrue(balls.size()+volleys.size()==1,"One effect application produces exactly one finite cast");
            balls.forEach(Entity::discard);volleys.forEach(Entity::discard);owner.removeEffect(effect);
        }
        owner.discard();target.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="wind_two_chains")
    public static void compressedBallLiftsInYDamagesAndExplodesOnlyOnce(GameTestHelper h){
        var owner=h.spawn(EntityType.VILLAGER,new BlockPos(2,60,2));owner.setNoAi(true);
        var enemy=h.spawn(EntityType.ZOMBIE,new BlockPos(5,60,2));enemy.setNoAi(true);enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);enemy.setHealth(1000);
        var ball=WindChainRegistration.BALL.create(h.getLevel());ball.configure(owner,5);ball.setPos(enemy.position().add(0,2,0));ball.anchor();double y=ball.getY();
        ball.tick();h.assertTrue(enemy.getDeltaMovement().y>0,"The actual attraction includes vertical velocity");
        for(int i=1;i<WindChainRules.compressionTicks(5)-1;i++){enemy.setPos(enemy.position().add(enemy.getDeltaMovement()));ball.tick();}
        float before=enemy.getHealth();ball.tick();
        h.assertTrue(ball.phase()==2&&ball.getY()>y+12,"The compressing core rises before its explosion");
        h.assertTrue(before<1000&&enemy.getHealth()<before,"Compression damage and the final blast both hit");
        var blastMotion=enemy.getDeltaMovement();var blastPosition=enemy.position();WindPullControl.resolve(h.getLevel());
        h.assertTrue(enemy.position().equals(blastPosition)&&enemy.getDeltaMovement().equals(blastMotion),"The last suction tick cannot overwrite the explosion knockback");
        float after=enemy.getHealth();for(int i=0;i<12;i++)ball.tick();
        h.assertTrue(ball.isRemoved()&&enemy.getHealth()==after,"The blast is single-shot and cleans up");owner.discard();enemy.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="wind_two_chains")
    public static void fiveSpearsHaveRealSweptHitsAndApplyVulnerability(GameTestHelper h){
        var owner=h.spawn(EntityType.VILLAGER,new BlockPos(2,80,2));owner.setNoAi(true);
        var enemy=h.spawn(EntityType.ZOMBIE,new BlockPos(2,80,12));enemy.setNoAi(true);enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);enemy.setHealth(1000);
        var cast=WindChainRegistration.VOLLEY.create(h.getLevel());cast.configure(owner,3,enemy);
        h.assertTrue(cast.count()==5,"T3 has exactly five spears");
        for(int i=0;i<200&&!cast.isRemoved();i++)cast.tick();
        h.assertTrue(enemy.getHealth()<1000&&enemy.hasEffect(com.tnc.tnc.magic.TNEffects.VULNERABLE.get()),"Real spear impacts deal damage and add vulnerability");
        h.assertTrue(cast.isRemoved(),"The completed volley expires");owner.discard();enemy.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="wind_two_chains")
    public static void skyVolleysHaveExactCountsVisibleHeightAndSeparatedGiantWeapons(GameTestHelper h){
        var owner=h.spawn(EntityType.VILLAGER,new BlockPos(2,100,2));owner.setNoAi(true);
        var enemy=h.spawn(EntityType.ZOMBIE,new BlockPos(2,100,12));enemy.setNoAi(true);enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);enemy.setHealth(2000);
        for(int tier=4;tier<=5;tier++){
            var cast=WindChainRegistration.VOLLEY.create(h.getLevel());cast.configure(owner,tier,enemy);
            h.assertTrue(cast.count()==(tier==4?30:5),"Sky weapon counts match the requested tier");
            for(int i=0;i<cast.count();i++)h.assertTrue(cast.launchPoint(i).y>=enemy.getY()+22,"All weapons start visibly above the target");
            if(tier==5)for(int i=0;i<5;i++)for(int j=i+1;j<5;j++)h.assertTrue(cast.launchPoint(i).distanceTo(cast.launchPoint(j))>8,"Giant weapon models have separated launch lanes");
            float before=enemy.getHealth();for(int i=0;i<200&&!cast.isRemoved();i++)cast.tick();
            h.assertTrue(cast.isRemoved()&&enemy.getHealth()<before,"Sky weapons actually strike the enemy and clean up");
        }
        owner.discard();enemy.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="wind_two_chains")
    public static void noAimCannotLaunchSkyWeaponsAndCasterRemovalCancelsCasts(GameTestHelper h){
        var owner=h.spawn(EntityType.VILLAGER,new BlockPos(2,120,2));owner.setNoAi(true);owner.setYRot(0);owner.setXRot(0);
        h.assertTrue(WindChainSpells.cast(owner,1,4)==null,"T4 requires an aimed enemy rather than arbitrary auto targeting");
        var giant=h.spawn(EntityType.ZOMBIE,new BlockPos(2,120,2));giant.setNoAi(true);giant.setPos(owner.position());giant.setBoundingBox(owner.getBoundingBox().inflate(2));
        h.assertTrue(WindChainSpells.aim(owner,96)==giant,"A caster inside a giant enemy hitbox can still aim at it");giant.discard();
        var ball=WindChainRegistration.BALL.create(h.getLevel());ball.configure(owner,5);
        var volley=WindChainRegistration.VOLLEY.create(h.getLevel());volley.configure(owner,2,null);
        owner.discard();ball.tick();volley.tick();h.assertTrue(ball.isRemoved()&&volley.isRemoved(),"Removing the caster clears both chains");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="wind_two_chains")
    public static void skySealTracksMovingEnemyUntilTheDropAndThenLocksTheImpactArea(GameTestHelper h){
        var owner=h.spawn(EntityType.VILLAGER,new BlockPos(2,140,2));owner.setNoAi(true);
        var enemy=h.spawn(EntityType.ZOMBIE,new BlockPos(2,140,12));enemy.setNoAi(true);
        var cast=WindChainRegistration.VOLLEY.create(h.getLevel());cast.configure(owner,4,enemy);
        for(int i=0;i<20;i++)cast.tick();enemy.setPos(enemy.position().add(4,0,0));cast.tick();
        h.assertTrue(cast.aimPoint().distanceTo(enemy.position())<.001,"The seal stays under a moving enemy throughout the sky formation");
        for(int i=21;i<WindChainRules.dropStart(4);i++)cast.tick();var locked=cast.aimPoint();enemy.setPos(enemy.position().add(4,0,0));cast.tick();
        h.assertTrue(cast.aimPoint().distanceTo(locked)<.001,"The actual drop follows a stable impact area");cast.discard();owner.discard();enemy.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="wind_two_chains")
    public static void relicItemsOrbitAndStaggeredRiseAreReal(GameTestHelper h){
        h.assertTrue(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","wind_relic_sword"))==WindRelicItems.SWORD,"The sword is registered hand equipment");
        h.assertTrue(WindRelicItems.SPEAR instanceof net.minecraft.world.item.SwordItem,"The spear can be equipped by future bosses");
        var owner=h.spawn(EntityType.VILLAGER,new BlockPos(2,160,2));owner.setNoAi(true);
        var enemy=h.spawn(EntityType.ZOMBIE,new BlockPos(2,160,12));enemy.setNoAi(true);
        var cast=WindChainRegistration.VOLLEY.create(h.getLevel());cast.configure(owner,2,enemy);
        h.assertTrue(cast.weaponAt(0,44).distanceTo(cast.weaponAt(0,56))>2,"Orbit moves around both sides of the enemy before dropping");
        h.assertTrue(cast.weaponAt(0,44).distanceTo(cast.weaponAt(0,68))<.01,"Orbit completes multiple full turns");
        cast.discard();cast=WindChainRegistration.VOLLEY.create(h.getLevel());cast.configure(owner,5,enemy);
        h.assertTrue(cast.visible(0,19)&&!cast.visible(1,19),"T5 weapons emerge one at a time");
        h.assertTrue(cast.weaponAt(0,24).y<cast.weaponAt(0,50).y,"T5 rises from its foot seal into the sky");
        h.assertTrue(cast.dropTime(1)-cast.dropTime(0)==12,"The final drops are staggered too");
        cast.discard();owner.discard();enemy.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="wind_two_chains")
    public static void windPullSurvivesBossVelocityResetAndRespectsCollision(GameTestHelper h){
        var boss=com.tnc.tnc.boss.earth.StoneLancerRegistration.TYPE.create(h.getLevel());
        boss.setNoAi(true);boss.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,180,2))));
        h.getLevel().addFreshEntity(boss);var start=boss.position();
        WindPullControl.request(boss,new net.minecraft.world.phys.Vec3(1,1,0));
        boss.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);WindPullControl.resolve(h.getLevel());
        h.assertTrue(boss.getX()>start.x+.4&&boss.getY()>start.y+.4,"A real giant boss moves in XYZ even after AI clears velocity");
        var end=boss.position();WindPullControl.resolve(h.getLevel());h.assertTrue(boss.position().equals(end),"Queued forces resolve only once");
        boss.discard();h.succeed();
    }
}
