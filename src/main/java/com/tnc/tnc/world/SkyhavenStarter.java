package com.tnc.tnc.world;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/** Installs immutable authored chunks before the dimension is opened, never during gameplay. */
@Mod.EventBusSubscriber(modid="tnc")
public final class SkyhavenStarter {
    @SubscribeEvent public static void beforeLevels(ServerAboutToStartEvent event) {
        // Small registry tests must not import the full pack's decorative block palette.
        if (event.getServer() instanceof net.minecraft.gametest.framework.GameTestServer) return;
        try {
            install(event.getServer().getWorldPath(LevelResource.ROOT));
        } catch (IOException failure) {
            throw new IllegalStateException("天空岛起始地形准备失败；停止加载，避免退回施工平台", failure);
        }
    }

    static boolean install(Path root) throws IOException {
        Path data=root.resolve("data"), island=data.resolve("tnc_sky_island_v5.dat");
        Path realm=root.resolve("dimensions/tnc/skyhaven");
        // Existing saves, including interrupted builds, keep their exact contents.
        Path staging=root.resolve(".tnc-skyhaven-starter");
        if (Files.exists(island)) return false;
        if (Files.exists(realm)) {
            if (Files.exists(realm.resolve(".tnc-starter")) && Files.exists(staging.resolve("island.dat"))) {
                Files.createDirectories(data);writeChoice(data);
                Files.move(staging.resolve("island.dat"),island);
                return true;
            }
            return false;
        }
        Files.createDirectories(staging);
        try (InputStream source=SkyhavenStarter.class.getResourceAsStream("/data/tnc/skyhaven/starter.zip")) {
            if (source==null) throw new IOException("缺少预制天空岛资源");
            try (ZipInputStream zip=new ZipInputStream(source)) {
                ZipEntry entry;
                while ((entry=zip.getNextEntry())!=null) {
                    Path dest=staging.resolve(entry.getName()).normalize();
                    if (!dest.startsWith(staging.normalize())) throw new IOException("非法地形路径");
                    if (entry.isDirectory()) {Files.createDirectories(dest);continue;}
                    Files.createDirectories(dest.getParent());
                    Files.copy(zip,dest,StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        if (!Files.exists(staging.resolve("realm/region/r.0.0.mca")) || !Files.exists(staging.resolve("island.dat")))
            throw new IOException("预制天空岛不完整");
        // Publish lifecycle metadata last. A preparation marker permits recovery if
        // the process stopped between publishing the realm and the master record.
        Files.createDirectories(realm.getParent());
        Files.createDirectories(data);
        Files.writeString(staging.resolve("realm/.tnc-starter"),"1");
        Files.move(staging.resolve("realm"),realm);
        writeChoice(data);
        Files.move(staging.resolve("island.dat"),island);
        LogUtils.getLogger().info("[Skyhaven] Authored island installed before world loading; no runtime construction");
        return true;
    }

    private static void writeChoice(Path data)throws IOException {
        CompoundTag choice=new CompoundTag(),payload=new CompoundTag();
        payload.putBoolean("Realm",true);payload.putBoolean("GroundBuilt",false);
        choice.put("data",payload);choice.putInt("DataVersion",3465);
        NbtIo.writeCompressed(choice,data.resolve("tnc_sky_world_choice.dat").toFile());
    }
    private SkyhavenStarter(){}
}
