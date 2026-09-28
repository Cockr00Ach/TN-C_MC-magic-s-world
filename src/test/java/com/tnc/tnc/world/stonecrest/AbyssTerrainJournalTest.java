package com.tnc.tnc.world.stonecrest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Arrays;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class AbyssTerrainJournalTest {
    @TempDir Path dir;
    @Test void completedJobIsReconciledWithoutLoadingItsAnchor() {
        var data=new AbyssCitadelData();
        var job=new AbyssCitadelData.Job(new net.minecraft.core.BlockPos(2048,-64,2048));
        job.phase=4;
        data.jobs.put(job.origin.asLong(),job);
        var loaded=AbyssCitadelData.load(data.save(new net.minecraft.nbt.CompoundTag()));
        assertEquals(1,loaded.jobs.get(job.origin.asLong()).phase);
        assertFalse(loaded.jobs.get(job.origin.asLong()).initialized);
    }
    @Test void originalTerrainSurvivesRestartAndCannotBeOverwritten() throws Exception {
        int[] heights=new int[AbyssCitadelData.TILE_COUNT*256]; Arrays.fill(heights,80);
        Path file=dir.resolve("plan.nbt");
        AbyssTerrainJournal.write(file,123,heights,81);
        var restored=AbyssTerrainJournal.read(file,123);
        assertArrayEquals(heights,restored.getIntArray("Heights"));
        int floor=AbyssPitPlan.floor(190,0,restored.getIntArray("Heights")[0]);
        Arrays.fill(heights,floor);
        assertThrows(IOException.class,()->AbyssTerrainJournal.write(file,123,heights,81));
        assertEquals(floor,AbyssPitPlan.floor(190,0,AbyssTerrainJournal.read(file,123).getIntArray("Heights")[0]));
        assertThrows(IOException.class,()->AbyssTerrainJournal.read(file,456));
    }
}
