package com.tnc.tnc.world.stonecrest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;

/** Immutable write-ahead terrain plan. Flushed BEFORE any excavation. */
final class AbyssTerrainJournal {
    static Path path(ServerLevel level,AbyssCitadelData.Job job) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/tnc_abyss_plans")
                .resolve(Long.toHexString(job.origin.asLong())+".nbt");
    }
    static boolean restore(ServerLevel level,AbyssCitadelData.Job job) throws IOException {
        Path path=path(level,job);
        if (!Files.exists(path)) return false;
        var tag=read(path,job.origin.asLong());
        int[] heights=tag.getIntArray("Heights");
        job.originalSurfaces=heights;
        job.lookoutY=tag.getInt("LookoutY");
        job.phase=1; job.tile=0; job.cursor=0; job.piece=0;
        return true;
    }
    static void commit(ServerLevel level,AbyssCitadelData.Job job) throws IOException {
        write(path(level,job),job.origin.asLong(),job.originalSurfaces,job.lookoutY);
    }
    static CompoundTag read(Path path,long origin) throws IOException {
        var tag=NbtIo.readCompressed(path.toFile());
        if (tag.getLong("Origin")!=origin || tag.getIntArray("Heights").length!=AbyssCitadelData.TILE_COUNT*256)
            throw new IOException("Invalid immutable abyss plan: "+path);
        return tag;
    }
    static void write(Path path,long origin,int[] heights,int lookoutY) throws IOException {
        if (heights.length!=AbyssCitadelData.TILE_COUNT*256) throw new IOException("Incomplete terrain plan");
        if (Files.exists(path)) throw new IOException("Refusing to replace existing abyss terrain plan");
        Files.createDirectories(path.getParent());
        var tag=new CompoundTag(); tag.putLong("Origin",origin); tag.putIntArray("Heights",heights);
        tag.putInt("LookoutY",lookoutY);
        var bytes=new ByteArrayOutputStream(); NbtIo.writeCompressed(tag,bytes);
        Path temp=Files.createTempFile(path.getParent(),"plan-",".tmp");
        try (var channel=FileChannel.open(temp,StandardOpenOption.WRITE)) {
            var buffer=ByteBuffer.wrap(bytes.toByteArray());
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        // Never start destructive work if the atomic write fails.
        Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE);
    }
}
