package com.tnc.tnc.world.stonecrest;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;

/** Immutable original terrain and asset identity; published before any block changes. */
final class LandmarkJournal {
    static Path path(ServerLevel l,LandmarkData.Job j) {
        return l.getServer().getWorldPath(LevelResource.ROOT).resolve("data/tnc_landmark_plans").resolve(j.key()+".nbt");
    }
    static boolean restore(ServerLevel l,LandmarkData.Job j) throws IOException {
        var p=path(l,j); if (!Files.exists(p)) return false;
        var t=NbtIo.readCompressed(p.toFile());
        var planned=net.minecraft.core.BlockPos.of(t.getLong("Origin"));
        boolean sameSite=t.contains("DiscoveryOrigin") ? t.getLong("DiscoveryOrigin")==j.discoveryOrigin.asLong()
                && planned.getX()==j.discoveryOrigin.getX() && planned.getZ()==j.discoveryOrigin.getZ()
                : t.getLong("Origin")==j.origin.asLong();
        if (!t.getString("Fingerprint").equals(j.manifest().fingerprint()) || !sameSite)
            throw new IOException("Landmark asset changed; refusing to overwrite the old occurrence: "+j.key());
        j.origin=planned;
        j.surfaces=t.getIntArray("Surfaces"); j.grounds=t.getIntArray("Grounds"); j.materials=t.getIntArray("Materials");
        j.preserved.load(t.getLongArray("PreservedBlocks"));
        if (j.surfaces.length!=j.columns() || j.grounds.length!=j.columns() || j.materials.length!=j.columns())
            throw new IOException("Incomplete landmark terrain journal");
        j.palette.clear();
        for (Tag raw:t.getList("Palette",Tag.TAG_COMPOUND)) j.palette.add(NbtUtils.readBlockState(l.holderLookup(Registries.BLOCK),(CompoundTag)raw));
        for (int i:j.materials) if (i<0 || i>=j.palette.size()) throw new IOException("Invalid terrain palette index");
        j.phase=1; j.tile=0; j.cursor=0; j.piece=0;
        return true;
    }
    static void commit(ServerLevel l,LandmarkData.Job j) throws IOException {
        var p=path(l,j); if (Files.exists(p)) throw new IOException("Existing immutable plan: "+p);
        var t=new CompoundTag(); t.putString("Fingerprint",j.manifest().fingerprint()); t.putLong("Origin",j.origin.asLong());
        t.putLong("DiscoveryOrigin",j.discoveryOrigin.asLong());
        t.putIntArray("Surfaces",j.surfaces); t.putIntArray("Grounds",j.grounds); t.putIntArray("Materials",j.materials);
        t.putLongArray("PreservedBlocks",j.preserved.save());
        var pal=new ListTag(); for (var s:j.palette) pal.add(NbtUtils.writeBlockState(s)); t.put("Palette",pal);
        var bytes=new ByteArrayOutputStream(); NbtIo.writeCompressed(t,bytes);
        Files.createDirectories(p.getParent()); var temp=Files.createTempFile(p.getParent(),"plan-",".tmp");
        try (var c=FileChannel.open(temp,StandardOpenOption.WRITE)) {
            var buffer=ByteBuffer.wrap(bytes.toByteArray()); while (buffer.hasRemaining()) c.write(buffer); c.force(true);
        }
        Files.move(temp,p,StandardCopyOption.ATOMIC_MOVE);
    }
}
