package com.tnc.tnc.life;

import com.mojang.authlib.GameProfile;
import com.tnc.tnc.life.pasture.PastureRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class WarningLampUpgradeGameTests {
    @GameTest(template="building_test_empty",batch="warning_lamp_upgrade",timeoutTicks=60)
    public static void lensChangesActualAlarmRangeAndReloadRetainsInventory(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 3, 2));
        level.setBlockAndUpdate(pos, WarningMoss.LANTERN.defaultBlockState());
        var lamp = (WarningMoss.LanternEntity)level.getBlockEntity(pos);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "warning_upgrade")); lamp.claim(player.getUUID());
        var zombie = EntityType.ZOMBIE.create(level); h.assertTrue(zombie != null, "Hostile fixture exists");
        zombie.setNoAi(true); zombie.setNoGravity(true);
        zombie.setPos(pos.getX() + 10.5, pos.getY(), pos.getZ() + .5);
        var fixtureChunk = new net.minecraft.world.level.ChunkPos(zombie.blockPosition());
        boolean alreadyForced = level.getForcedChunks().contains(fixtureChunk.toLong());
        level.setChunkForced(fixtureChunk.x, fixtureChunk.z, true);
        level.addFreshEntity(zombie);
        // The hostile is outside the small template; allow entity chunk visibility to settle.
        h.runAfterDelay(5, () -> {
        try {
            WarningMoss.LANTERN.tick(level.getBlockState(pos), level, pos, level.random);
            h.assertTrue(!level.getBlockState(pos).getValue(WarningMoss.LanternBlock.LIT), "Unmodified eight-block lamp ignores ten-block hostile");
            ItemStack lens = new ItemStack(PastureRegistry.item("warning_lens")); lamp.interact(player, lens, false);
            h.assertTrue(lens.isEmpty(), "Installation consumes one actual lens");
            lamp.load(lamp.saveWithoutMetadata());
            WarningMoss.LANTERN.tick(level.getBlockState(pos), level, pos, level.random);
            h.assertTrue(lamp.range() == 12 && level.getBlockState(pos).getValue(WarningMoss.LanternBlock.LIT),
                    "Reloaded lens extends the real hostile query to twelve");
            lamp.interact(player, ItemStack.EMPTY, true);
            WarningMoss.LANTERN.tick(level.getBlockState(pos), level, pos, level.random);
            h.assertTrue(lamp.range() == 8 && !level.getBlockState(pos).getValue(WarningMoss.LanternBlock.LIT), "Detachment actually contracts alarm range");
            h.succeed();
        } finally {
            zombie.discard();
            if (!alreadyForced) level.setChunkForced(fixtureChunk.x, fixtureChunk.z, false);
        }
        });
    }
}
