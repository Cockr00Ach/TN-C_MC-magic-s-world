package com.tnc.tnc.boss;

import com.tnc.tnc.magic.water.*;
import com.tnc.tnc.npc.TNNpcs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncleviathan")
@PrefixGameTestTemplate(false)
public final class LeviathanSpellGameTests {
    private static TNWaterLeviathanEntity boss(GameTestHelper h) {
        var boss=h.spawn(TNNpcs.WATER_LEVIATHAN.get(),new BlockPos(20,4,8));
        boss.setNoAi(true); return boss;
    }

    private static LivingEntity target(GameTestHelper h) {
        var target=h.spawn(EntityType.VILLAGER,new BlockPos(20,4,28));
        target.setNoAi(true); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.setHealth(1000); return target;
    }

    @GameTest(template="leviathan_empty",timeoutTicks=65)
    public static void realTsunamiHitsEnemiesButNotTheCaster(GameTestHelper h) {
        var boss=boss(h); var enemy=target(h); float hp=boss.getHealth();
        var player=h.makeMockSurvivalPlayer();
        h.assertTrue(WaterSpellRules.enemy(boss,player),"Boss water spells must hit survival players");
        h.assertTrue(!WaterSpellRules.enemy(boss,boss),"Boss cannot hit itself");
        h.assertTrue(!WaterSpellRules.enemy(player,enemy),"Player water cannot start harming friendly villagers");
        h.assertTrue(LeviathanSpells.cast(boss,enemy,"tsunami"),"T4 tsunami entity created");
        h.runAtTickTime(40,()->{
            h.assertTrue(enemy.getHealth()<1000 && boss.getHealth()==hp,
                    "Real boss-owned wave must persist and damage enemies without hitting its owner");
            boss.discard(); enemy.discard(); h.succeed();
        });
    }

    @GameTest(template="leviathan_empty",timeoutTicks=130)
    public static void realBarrageAndDragonRuinUseMobOwnership(GameTestHelper h) {
        var boss=boss(h); var enemy=target(h);
        h.assertTrue(LeviathanSpells.cast(boss,enemy,"dragon_roar"),"T3 barrage created");
        final float[] afterBarrage={1000};
        final BlockPos[] marker={null};
        h.runAtTickTime(45,()->{
            h.assertTrue(enemy.getHealth()<1000,"Actual barrage bolts must hit with a mob owner");
            afterBarrage[0]=enemy.getHealth();
            h.assertTrue(LeviathanSpells.cast(boss,enemy,"dragon_ruin"),"T5 beam created");
            var beam=h.getLevel().getEntitiesOfClass(TNWaterSpellEntity.class,boss.getBoundingBox().inflate(80))
                    .stream().filter(e->e.tier()==5).findFirst().orElseThrow();
            marker[0]=BlockPos.containing(beam.position().add(beam.direction().scale(8))
                    .add(WaterSpellRules.right(beam.direction()).scale(10)));
            h.getLevel().setBlockAndUpdate(marker[0],Blocks.STONE.defaultBlockState());
        });
        h.runAtTickTime(100,()->{
            h.assertTrue(enemy.getHealth()<afterBarrage[0],"T5 beam deals real damage after charge");
            h.assertTrue(h.getLevel().getEntitiesOfClass(TNWaterSpellEntity.class,
                    boss.getBoundingBox().inflate(80)).stream()
                    .anyMatch(e->e.tier()==5 && e.firedAt()>=0 && e.length()>0),
                    "The actual T5 beam must finish charging and acquire a real length");
            h.assertTrue(h.getLevel().getBlockState(marker[0]).is(Blocks.STONE),
                    "Mob beam cannot borrow a player's drilling permissions");
            boss.discard(); enemy.discard();
        });
        h.runAtTickTime(105,()->{
            h.assertTrue(h.getLevel().getEntitiesOfClass(TNWaterSpellEntity.class,
                    boss.getBoundingBox().inflate(45)).isEmpty(),"Large spells end when their boss disappears");
            h.succeed();
        });
    }

    @GameTest(template="leviathan_empty",timeoutTicks=185)
    public static void cryptKeepsItsRealSwordTimelineWithAMobCaster(GameTestHelper h) {
        var boss=boss(h); var enemy=target(h); float hp=boss.getHealth();
        h.assertTrue(LeviathanSpells.cast(boss,enemy,"sea_god_crypt"),"T5 crypt created");
        h.runAtTickTime(155,()->{
            h.assertTrue(enemy.getHealth()<=1000-SeaGodSwordRules.DAMAGE,
                    "Boss crypt must survive until the real descending sword strikes");
            h.assertTrue(boss.getHealth()==hp,"Caster inside its own crypt is immune");
            boss.discard(); enemy.discard(); h.succeed();
        });
    }
}
