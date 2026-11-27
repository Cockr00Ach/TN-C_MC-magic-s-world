package com.tnc.tnc.magic.free;

import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.adventure.ElementWands;
import com.tnc.tnc.magic.Element;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.fire.FireHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tncacademy")
@PrefixGameTestTemplate(false)
public final class FireContactGameTests {
    @GameTest(template="building_test_empty", batch="fire_ground_contact", timeoutTicks=100)
    public static void snakeTrailTouchesFeetOfTallCreatures(GameTestHelper h) {
        var player = caster(h);
        var floor = player.blockPosition().below();
        for (int x=-2; x<=2; x++) {
            for (int z=0; z<=8; z++) {
                h.getLevel().setBlockAndUpdate(floor.offset(x,0,z), Blocks.STONE.defaultBlockState());
                if (z==8) {
                    for (int y=1; y<=3; y++)
                        h.getLevel().setBlockAndUpdate(floor.offset(x,y,z), Blocks.STONE.defaultBlockState());
                }
            }
        }
        var standing = EntityType.IRON_GOLEM.create(h.getLevel());
        var flying = EntityType.IRON_GOLEM.create(h.getLevel());
        cast(player, 730, "火蛇");
        // Introduce the targets only after the projectile has reached the backstop.
        h.runAfterDelay(50, () -> {
            spawn(h, standing, player.position().add(0,0,5));
            spawn(h, flying, player.position().add(0,3,5));
        });
        h.runAfterDelay(65, () -> {
            float groundHeat=FireHeat.of(standing), airHeat=FireHeat.of(flying);
            standing.discard();
            flying.discard();
            for (int x=-2; x<=2; x++) for (int z=0; z<=8; z++) {
                h.getLevel().removeBlock(floor.offset(x,0,z),false);
                if (z==8) for (int y=1; y<=3; y++) h.getLevel().removeBlock(floor.offset(x,y,z),false);
            }
            clean(player);
            h.assertTrue(groundHeat>0 && airHeat==0, "Snake trail must contact feet only: ground="+groundHeat+", air="+airHeat);
            h.succeed();
        });
    }

    @GameTest(template="building_test_empty", batch="fire_thin_obstruction", timeoutTicks=120)
    public static void turningMagmaSprayStopsAtThinPane(GameTestHelper h) {
        var player = caster(h);
        var victim = EntityType.PIG.create(h.getLevel());
        spawn(h, victim, player.position().add(0,1,4));
        var pane = player.blockPosition().offset(0,1,1);
        h.getLevel().setBlockAndUpdate(pane, Blocks.GLASS_PANE.defaultBlockState());
        player.setYRot(-90);
        cast(player, 731, "岩浆喷射");
        h.runAfterDelay(38, () -> player.setYRot(0));
        float[] blockedHeat = new float[1];
        h.runAfterDelay(65, () -> {
            blockedHeat[0] = FireHeat.of(victim);
            h.getLevel().removeBlock(pane,false);
        });
        h.runAfterDelay(85, () -> {
            float clearHeat=FireHeat.of(victim);
            victim.discard();
            clean(player);
            h.assertTrue(blockedHeat[0]==0 && clearHeat>0, "Magma must stop at the pane and reach after removal: blocked="+blockedHeat[0]+", open="+clearHeat);
            h.succeed();
        });
    }

    private static ServerPlayer caster(GameTestHelper h) {
        var player=AdventureGameTests.player(h);
        var base=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,3,2)));
        player.setPos(base.x,h.getLevel().getMaxBuildHeight()-16,base.z);
        player.setYRot(0);
        player.setXRot(0);
        player.setItemInHand(InteractionHand.MAIN_HAND,ElementWands.stack(ElementWands.find("fire_wand_3")));
        var data=MagicStone.getOrNull(player);
        data.setAffinity(Element.FIRE,3);
        data.setMaxMana(200);
        data.setMana(200);
        return player;
    }

    private static void spawn(GameTestHelper h, Mob mob, Vec3 at) {
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(400);
        mob.setHealth(mob.getMaxHealth());
        mob.setPos(at);
        h.getLevel().addFreshEntity(mob);
    }

    private static void cast(ServerPlayer player, long token, String words) {
        FreeMagicServer.input(player,new FreeMagicNetwork.Input(FreeMagicNetwork.BEGIN,token,""));
        FreeMagicServer.input(player,new FreeMagicNetwork.Input(FreeMagicNetwork.FINAL,token,words));
    }

    private static void clean(ServerPlayer player) {
        var event = new PlayerEvent.PlayerLoggedOutEvent(player);
        FreeMagicServer.logout(event);
        ElementalRuntime.logout(event);
    }
}
