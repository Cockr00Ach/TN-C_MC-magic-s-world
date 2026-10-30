package com.tnc.tnc.world;

import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SkyhavenStarterTest {
    @TempDir Path root;
    @Test void portalRequiresLeavingDestinationBeforeRearming() {
        var data=new net.minecraft.nbt.CompoundTag();
        data.putBoolean("tnc_sky_portal_exit_required",true);
        assertTrue(SkyIslandManager.portalExitBlocked(data,true));
        assertTrue(SkyIslandManager.portalExitBlocked(data,true),"standing still must never return automatically");
        assertTrue(SkyIslandManager.portalExitBlocked(data,false),"exit movement only rearms for the next visit");
        assertFalse(SkyIslandManager.portalExitBlocked(data,true));
    }
    @Test void freshWorldHasCompleteIslandBeforeLevelsLoad() throws Exception {
        assertTrue(SkyhavenStarter.install(root));
        var data=NbtIo.readCompressed(root.resolve("data/tnc_sky_island_v5.dat").toFile()).getCompound("data");
        assertEquals("COMPLETE",data.getString("Phase"));
        assertEquals(-260,data.getInt("OriginX"));
        assertEquals(0,data.getInt("GroundPortalY"),"overworld gate must be surveyed for this seed");
        assertTrue(NbtIo.readCompressed(root.resolve("data/tnc_sky_world_choice.dat").toFile()).getCompound("data").getBoolean("Realm"));
        assertTrue(Files.size(root.resolve("dimensions/tnc/skyhaven/region/r.0.0.mca"))>8192);
        assertFalse(Files.exists(root.resolve("playerdata")));
        assertFalse(Files.exists(root.resolve("data/tnc_adventure_v1.dat")));
        assertFalse(SkyhavenStarter.install(root),"restart never reinstalls authored chunks");
    }
    @Test void existingCompletedOrIncompleteSaveIsUntouched() throws Exception {
        Files.createDirectories(root.resolve("data"));
        var path=root.resolve("data/tnc_sky_island_v5.dat");
        Files.writeString(path,"existing lifecycle");
        assertFalse(SkyhavenStarter.install(root));
        assertEquals("existing lifecycle",Files.readString(path));
        assertFalse(Files.exists(root.resolve("dimensions")));
    }
    @Test void existingUntrackedDimensionIsNeverOverwritten() throws Exception {
        var dir=root.resolve("dimensions/tnc/skyhaven");Files.createDirectories(dir);
        Files.writeString(dir.resolve("player-build.txt"),"retain");
        assertFalse(SkyhavenStarter.install(root));
        assertEquals("retain",Files.readString(dir.resolve("player-build.txt")));
    }
    @Test void interruptedPublishRecoversMetadataWithoutRewritingChunks() throws Exception {
        SkyhavenStarter.install(root);
        Files.move(root.resolve("data/tnc_sky_island_v5.dat"),root.resolve(".tnc-skyhaven-starter/island.dat"));
        var region=root.resolve("dimensions/tnc/skyhaven/region/r.0.0.mca");
        long modified=Files.getLastModifiedTime(region).toMillis();
        assertTrue(SkyhavenStarter.install(root));
        assertEquals(modified,Files.getLastModifiedTime(region).toMillis());
        assertTrue(Files.exists(root.resolve("data/tnc_sky_island_v5.dat")));
    }
}
