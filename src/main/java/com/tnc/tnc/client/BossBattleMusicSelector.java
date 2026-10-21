package com.tnc.tnc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

/** Select a nearby living boss without depending on optional boss implementation classes. */
public final class BossBattleMusicSelector {
    private BossBattleMusicSelector() {}

    public static ResourceLocation select(Minecraft minecraft, ResourceLocation playing, double range) {
        if (minecraft.level == null || minecraft.player == null) return null;
        ResourceLocation selected = null;
        double nearest = Double.MAX_VALUE;
        for (LivingEntity entity : minecraft.level.getEntitiesOfClass(LivingEntity.class,
                minecraft.player.getBoundingBox().inflate(range), LivingEntity::isAlive)) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            if (id == null || !id.getNamespace().equals("tnc")) continue;
            String track = switch (id.getPath()) {
                case "yan_dark" -> "yan_crywolf";
                case "stone_lancer" -> "earth_dawn";
                case "wind_general", "wind_warden", "canglan_warlord" -> "wind_phoenix";
                default -> null;
            };
            if (track == null) continue;
            ResourceLocation music = ResourceLocation.fromNamespaceAndPath("tnc", "music.boss." + track);
            // Local audio is intentionally not in Git. A source-only update must retain
            // usable battle music until the collaborator supplies their audio overlay.
            if (!minecraft.getSoundManager().getAvailableSounds().contains(music)
                    || minecraft.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("tnc",
                        "sounds/music/boss/" + track + ".ogg")).isEmpty()) {
                music = ResourceLocation.fromNamespaceAndPath("minecraft", "music.dragon");
            }
            // Keep the current encounter's music while its boss remains nearby.
            if (music.equals(playing)) return music;
            double distance = minecraft.player.distanceToSqr(entity);
            if (distance < nearest) { nearest = distance; selected = music; }
        }
        return selected;
    }
}
