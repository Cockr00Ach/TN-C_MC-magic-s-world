package net.minecraft.core;

import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import raccoonman.reterraforged.data.worldgen.preset.PresetNoiseData;
import raccoonman.reterraforged.data.worldgen.preset.settings.Presets;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.noise.module.Noise;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Standalone registry for the native ReTerraForged 0.0.6 Forge/SRG preview generator. */
public final class RTFProbe implements BootstapContext<Noise>, HolderGetter<Noise>, HolderOwner<Noise> {
    private final Map<ResourceKey<Noise>, Holder.Reference<Noise>> noises = new HashMap<>();

    @Override
    public Holder.Reference<Noise> m_255042_(ResourceKey<Noise> key, Noise value, Lifecycle lifecycle) {
        var holder = Holder.Reference.m_254896_(this, key);
        holder.m_247654_(value);
        if (noises.putIfAbsent(key, holder) != null) {
            throw new IllegalStateException("Duplicate RTF noise " + key);
        }
        return holder;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HolderGetter<T> m_255420_(ResourceKey<? extends Registry<? extends T>> key) {
        if (!key.equals(raccoonman.reterraforged.registries.RTFRegistries.NOISE)) {
            throw new IllegalArgumentException("Unexpected registry " + key);
        }
        return (HolderGetter<T>) this;
    }

    @Override
    public Optional<Holder.Reference<Noise>> m_254902_(ResourceKey<Noise> key) {
        return Optional.ofNullable(noises.get(key));
    }

    @Override
    public Optional<HolderSet.Named<Noise>> m_254901_(TagKey<Noise> key) {
        return Optional.empty();
    }

    public static void main(String[] args) throws Exception {
        int seed = Integer.parseInt(args[0]);
        int originX = Integer.parseInt(args[1]);
        int originZ = Integer.parseInt(args[2]);
        int width = Integer.parseInt(args[3]);
        int depth = Integer.parseInt(args[4]);
        int spacing = Integer.parseInt(args[5]);
        SharedConstants.m_142977_();
        Bootstrap.m_135870_();

        var preset = Presets.makeLegacyBeautiful();
        preset.world().properties.worldHeight = 384;
        preset.terrain().mountains.verticalScale *= 1.5f;
        preset.terrain().mountains.weight *= 1.35f;
        preset.terrain().general.fancyMountains = true;
        var registry = new RTFProbe();
        PresetNoiseData.bootstrap(preset, registry);
        var context = GeneratorContext.makeUncached(preset, registry, seed, 3, 0, 1);
        var heightmap = context.lookup.getHeightmap();
        var cell = new Cell();
        try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[6]))))) {
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    heightmap.apply(cell.reset(), originX + x * spacing, originZ + z * spacing, true);
                    out.writeShort(context.levels.scale(cell.height));
                    out.writeShort(cell.terrain.getId());
                    out.writeFloat(cell.riverMask);
                }
            }
        }
        // The mod's background pool normally lives for the duration of a game.
        System.exit(0);
    }
}
