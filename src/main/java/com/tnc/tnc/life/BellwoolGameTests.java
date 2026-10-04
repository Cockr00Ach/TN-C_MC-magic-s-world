package com.tnc.tnc.life;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class BellwoolGameTests {
    private BellwoolGameTests() {}

    @GameTest(template = "building_test_empty", batch = "bellwool", timeoutTicks = 30)
    public static void legacyAttunedSheepKeepsCooldownWithoutNewConversions(GameTestHelper h) {
        var level = h.getLevel();
        var sheep = EntityType.SHEEP.create(level);
        h.assertTrue(sheep != null, "Sheep fixture exists");
        var pos = h.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2));
        sheep.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addFreshEntity(sheep);
        try {
            var player = AdventureGameTests.player(h);
            ItemStack shears = new ItemStack(Items.SHEARS);
            h.assertTrue(!sheep.getPersistentData().getBoolean("tncBellwoolAttuned"),
                    "Fresh vanilla sheep is not a bellwool animal");
            sheep.getPersistentData().putBoolean("tncBellwoolAttuned", true);
            sheep.getPersistentData().putLong("tncBellwoolNextBonus", 0L);
            h.assertTrue(BellwoolHusbandry.shearAndBonus(sheep, shears, player, InteractionHand.MAIN_HAND),
                    "First living shear yields resonant fleece");
            h.assertTrue(sheep.isSheared(), "Ordinary wool is consumed by the same shear");
            sheep.setSheared(false); // Simulate the next natural grass-fed wool regrowth.
            h.assertTrue(!BellwoolHusbandry.shearAndBonus(sheep, shears, player, InteractionHand.MAIN_HAND),
                    "Regrowing vanilla wool cannot bypass the two-day bonus cooldown");
            AABB nearby = new AABB(pos).inflate(3);
            long fleece = level.getEntitiesOfClass(ItemEntity.class, nearby).stream()
                    .filter(drop -> drop.getItem().is(TNMod.RESONANT_FLEECE.get()))
                    .mapToInt(drop -> drop.getItem().getCount()).sum();
            h.assertTrue(fleece == 1, "Exactly one unique wool product was dropped");
            h.succeed();
        } finally {
            sheep.discard();
        }
    }
}
