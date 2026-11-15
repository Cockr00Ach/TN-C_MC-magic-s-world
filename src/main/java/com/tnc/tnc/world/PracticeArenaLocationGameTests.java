package com.tnc.tnc.world;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.warrior.PracticeArena;
import com.tnc.tnc.warrior.TrainingGroundData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tncwarrior")
@PrefixGameTestTemplate(false)
public final class PracticeArenaLocationGameTests {
    @GameTest(template="building_test_empty", timeoutTicks=600)
    public static void oldGroundDeckMigratesToAuthoredIslandSiteAndTeleportUsesIt(GameTestHelper h) {
        var server=h.getLevel().getServer();
        var root=server.overworld();
        var choice=SkyIslandWorlds.Choice.get(server);boolean wasRealm=choice.realm;choice.realm=true;
        var level=SkyIslandWorlds.island(server);
        var previous=SkyIslandSavedData.get(root);
        var island=new SkyIslandSavedData();
        island.phase=SkyIslandSavedData.Phase.COMPLETE;island.layoutReady=true;
        island.originX=10000;island.originZ=10000;island.originY=90;
        island.arrivalY=180;
        root.getDataStorage().set(SkyIslandSavedData.FILE_ID,island);
        var oldFloor=new BlockPos(10084,105,10256);
        try {
            var casino = new BlockPos(10084, 180, 10256);
            level.setBlock(casino, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
            PracticeArena.build(level, casino.getX(), casino.getZ());
            h.assertTrue(level.getBlockState(casino).is(Blocks.DIAMOND_BLOCK), "Casino footprint is protected");
            var data=TrainingGroundData.get(level.getServer());
            data.markArena("minecraft:overworld",10084,105,10256);
            level.setBlock(oldFloor,Blocks.GOLD_BLOCK.defaultBlockState(),3);
            var site=new BlockPos(10466,176,10236);
            level.setBlock(site.below(), Blocks.STONE.defaultBlockState(), 3);
            h.assertTrue(PracticeArena.ensure(level.getServer()),"Old record must not bypass island construction");
            h.assertTrue(PracticeArena.site(level.getServer()).equals(site),"Deck follows the authored island site and elevation");
            h.assertTrue(level.getBlockState(site.above()).is(TNMod.PRACTICE_TERMINAL.get()),"Island deck has a terminal");
            h.assertTrue(level.getBlockState(oldFloor).is(Blocks.GOLD_BLOCK),"Migration preserves old structure");
            int count=data.arenas().size();
            h.assertTrue(PracticeArena.ensure(level.getServer())&&data.arenas().size()==count,"Ensure is idempotent");
            // A later unrelated arena must not redirect the command.
            data.markArena("minecraft:overworld",12000,70,12000);
            var p=AdventureGameTests.player(h);
            try {
                h.assertTrue(PracticeArena.teleportIn(p),"Command reaches island arena");
                h.assertTrue(p.serverLevel()==level&&p.getY()==177&&p.getX()==10466.5,"Destination is the relocated deck in the independent realm");
            } finally {p.discard();}
            h.succeed();
        } finally {root.getDataStorage().set(SkyIslandSavedData.FILE_ID,previous);choice.realm=wasRealm;choice.setDirty();}
    }
}
