package com.tnc.tnc.warrior;

import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncwarrior") @PrefixGameTestTemplate(false)
public final class WindInfusionGameTests {
    @GameTest(template="building_test_empty", timeoutTicks=40)
    public static void windCutRequiresGrowthHitsNearestAndRespectsWallsAndCooldown(GameTestHelper h) {
        for(var pos:BlockPos.betweenClosed(new BlockPos(4,3,2),new BlockPos(6,5,12)))h.setBlock(pos,Blocks.AIR);
        var p=AdventureGameTests.player(h); p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5,3,3))));
        p.setYRot(0);p.setXRot(0);
        var sword=WindInfusion.infuse(new ItemStack(Items.IRON_SWORD));p.setItemInHand(InteractionHand.MAIN_HAND,sword);
        var data=TemperingData.get(p.server);data.setTalent(p.getUUID(),2);data.set(p.getUUID(),199);
        h.assertTrue(!WindInfusion.cast(p)&&sword.getDamageValue()==0,"Underqualified cast spends no durability or cooldown");
        data.set(p.getUUID(),200);
        var near=h.spawn(EntityType.ZOMBIE,new BlockPos(5,3,6)); near.setNoAi(true);
        var far=h.spawn(EntityType.ZOMBIE,new BlockPos(5,3,8)); far.setNoAi(true);
        float nearHealth=near.getHealth(),farHealth=far.getHealth();
        h.assertTrue(WindInfusion.cast(p),"Qualified cast succeeds");
        h.assertTrue(near.getHealth()<nearHealth && far.getHealth()==farHealth,"Only nearest hostile target is struck");
        h.assertTrue(!WindInfusion.cast(p),"Repeated casts share player cooldown");
        p.setItemInHand(InteractionHand.MAIN_HAND,WindInfusion.infuse(new ItemStack(Items.DIAMOND_AXE)));
        h.assertTrue(!WindInfusion.cast(p),"Weapon switching cannot reset cooldown");
        long now=p.server.overworld().getGameTime();
        var loaded=TemperingData.load(data.save(new CompoundTag()));
        h.assertTrue(!loaded.spendWindCooldown(p.getUUID(),now),"Reload preserves cooldown");
        h.assertTrue(loaded.spendWindCooldown(p.getUUID(),now+160),"Cooldown expires after eight seconds");
        var q=AdventureGameTests.player(h);q.setPos(p.position());q.setYRot(0);q.setXRot(0);
        data.setTalent(q.getUUID(),2);data.set(q.getUUID(),200);q.setItemInHand(InteractionHand.MAIN_HAND,WindInfusion.infuse(new ItemStack(Items.IRON_SWORD)));
        h.setBlock(new BlockPos(5,4,5),Blocks.STONE); near.invulnerableTime=0; float blockedHealth=near.getHealth();
        h.assertTrue(WindInfusion.cast(q)&&near.getHealth()==blockedHealth,"Wall blocks the actual wind strike");
        h.succeed();
    }
}
