package com.tnc.tnc.warrior.client;

import com.google.gson.JsonParser;
import com.tnc.tnc.TNMod;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Grip metadata travels with the authored model, so art changes cannot leave stale hardcoded anchors. */
@Mod.EventBusSubscriber(modid=TNMod.MODID, value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class WarriorModelGrip {
    private static final Map<ResourceLocation, Optional<Vec3>> CACHE = new HashMap<>();
    public static Optional<Vec3> find(ResourceLocation item) {
        return CACHE.computeIfAbsent(item, id -> {
            var path = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "models/item/"+id.getPath()+".json");
            var resource = Minecraft.getInstance().getResourceManager().getResource(path);
            if (resource.isEmpty()) return Optional.empty();
            try (var reader = resource.get().openAsReader()) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                if (!json.has("tnc_grip")) return Optional.empty();
                var v = json.getAsJsonArray("tnc_grip");
                if (v.size()!=3) return Optional.empty();
                var grip = new Vec3(v.get(0).getAsDouble()/16, v.get(1).getAsDouble()/16, v.get(2).getAsDouble()/16);
                return Double.isFinite(grip.lengthSqr()) ? Optional.of(grip) : Optional.empty();
            } catch (Exception error) {
                com.mojang.logging.LogUtils.getLogger().warn("Invalid grip metadata for {}", id, error);
                return Optional.empty();
            }
        });
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> {CACHE.clear();WarriorModelBlade.clear();WarriorTrail.clear();});
    }
}
