package com.tnc.tnc.skyacademy;

import com.tnc.tnc.world.SkyIslandSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "tnc")
public final class SkyhavenLayoutSync {
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var tag = new CompoundTag();
            tag.putString("Type", "island_layout");
            boolean rebuilt = SkyIslandSavedData.get(player.serverLevel()).rebuiltSkyhaven();
            tag.putBoolean("Rebuilt", rebuilt);
            var world = player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            tag.putBoolean("Highlands", rebuilt && java.nio.file.Files.isRegularFile(
                    world.resolve("dimensions/tnc/skyhaven/.tnc-highlands-lod-v1")));
            AcademyNetwork.send(player, tag);
        }
    }

    private SkyhavenLayoutSync() {}
}
