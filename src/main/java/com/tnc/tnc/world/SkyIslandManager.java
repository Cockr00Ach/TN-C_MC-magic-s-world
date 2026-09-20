package com.tnc.tnc.world;

import com.mojang.logging.LogUtils;
import com.tnc.tnc.TNMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.common.world.ForgeChunkManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Crash-resumable v5 island builder and paired visual portal runtime.
 *
 * <p>Every template placement is an idempotent checkpoint. A crash can therefore repeat the
 * current piece, but can never skip it or mark an incomplete island as ready.</p>
 */
public final class SkyIslandManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int[][] ISLAND_OFFSETS = {
            {768, 0}, {-768, 0}, {0, 768}, {0, -768},
            {640, 640}, {640, -640}, {-640, 640}, {-640, -640}
    };
    private static final int[][] PORTAL_OFFSETS = {
            {128, 0}, {-128, 0}, {0, 128}, {0, -128},
            {96, 96}, {96, -96}, {-96, 96}, {-96, -96},
            {192, 0}, {-192, 0}, {0, 192}, {0, -192},
            {144, 144}, {144, -144}, {-144, 144}, {-144, -144}
    };
    private static final int[][] PORTAL_SAMPLES = {
            {0, 0}, {-6, -6}, {-6, 6}, {6, -6}, {6, 6}
    };
    private static final long PORTAL_COOLDOWN_TICKS = 60L;
    private static final String PLAYER_COOLDOWN = "tnc_sky_portal_cooldown";
    private static final Map<MinecraftServer, SkyIslandManifest> MANIFESTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<UUID, PortalChargeState> PORTAL_CHARGES = new HashMap<>();

    private SkyIslandManager() {
    }

    public static void onPlayerLogin(ServerPlayer player) {
        MinecraftServer server = player.server;
        ServerLevel overworld = server.overworld();
        try {
            SkyIslandManifest manifest = manifest(server);
            SkyIslandSavedData data = SkyIslandSavedData.get(overworld);

            if (isComplete(data, manifest)) {
                tellCoordinates(player, data);
                return;
            }

            if (data.version != manifest.version()) {
                releaseActiveChunks(overworld, data);
                data.resetFor(manifest);
                player.sendSystemMessage(Component.literal("§b正在勘测主世界中的天空岛位置，新手村会在后台分批生成。"));
            } else if (data.phase == SkyIslandSavedData.Phase.IDLE) {
                data.resetFor(manifest);
                player.sendSystemMessage(Component.literal("§b正在勘测主世界中的天空岛位置，新手村会在后台分批生成。"));
            } else if (data.phase == SkyIslandSavedData.Phase.ERROR) {
                releaseActiveChunks(overworld, data);
                data.lastError = "";
                data.phase = data.layoutReady
                        ? SkyIslandSavedData.Phase.BUILDING
                        : SkyIslandSavedData.Phase.SURVEY;
                data.setDirty();
                player.sendSystemMessage(Component.literal("§e正在从上次失败的检查点重试天空岛生成。"));
            } else {
                player.sendSystemMessage(Component.literal("§b天空岛新手村仍在生成，进度："
                        + data.nextPiece + "/" + manifest.pieces().size() + "。"));
            }
        } catch (Exception error) {
            LOGGER.error("[TN-C Sky Island] could not start generation", error);
            player.sendSystemMessage(Component.literal("§c天空岛清单加载失败：" + shortMessage(error)));
        }
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        SkyIslandSavedData data = SkyIslandSavedData.get(level);

        if (data.phase == SkyIslandSavedData.Phase.IDLE) {
            return;
        }

        SkyIslandManifest manifest;
        try {
            manifest = manifest(server);
        } catch (Exception error) {
            fail(level, data, "清单加载失败", error);
            return;
        }

        try {
            switch (data.phase) {
                case SURVEY -> surveyStep(level, data, manifest);
                case BUILDING -> buildStep(level, data, manifest);
                case FINALIZING -> finalizeBuild(level, data, manifest);
                case COMPLETE -> portalTick(level, data, manifest);
                case ERROR, IDLE -> {
                }
            }
        } catch (Throwable error) {
            fail(level, data, "状态 " + data.phase + " 执行失败", error);
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        PORTAL_CHARGES.clear();
        ServerLevel level = server.overworld();
        SkyIslandSavedData data = SkyIslandSavedData.get(level);
        try {
            releaseActiveChunks(level, data);
        } catch (Throwable error) {
            LOGGER.error("[TN-C Sky Island] could not release chunk tickets during shutdown", error);
        }
    }

    private static void surveyStep(ServerLevel level, SkyIslandSavedData data, SkyIslandManifest manifest) {
        List<BlockPos> samples = surveySamples(manifest);
        if (data.surveyCandidate >= ISLAND_OFFSETS.length) {
            finishSurvey(level, data, manifest);
            return;
        }

        BlockPos spawn = level.getSharedSpawnPos();
        int[] candidateOffset = ISLAND_OFFSETS[data.surveyCandidate];
        int candidateOriginX = spawn.getX() + candidateOffset[0] - manifest.dimensions().getX() / 2;
        int candidateOriginZ = spawn.getZ() + candidateOffset[1] - manifest.dimensions().getZ() / 2;
        BlockPos sample = samples.get(data.surveySample);
        int worldX = candidateOriginX + sample.getX();
        int worldZ = candidateOriginZ + sample.getZ();

        level.getChunk(worldX >> 4, worldZ >> 4);
        int terrainY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ) - 1;
        int islandBottomY = manifest.worldOriginY() + sample.getY();
        int overlap = Math.max(0, terrainY - islandBottomY + 1);
        data.surveyScore += overlap * 10_000 + Math.max(0, terrainY - 80);
        data.surveySample++;

        if (data.surveySample >= samples.size()) {
            if (data.surveyScore < data.bestSurveyScore) {
                data.bestSurveyScore = data.surveyScore;
                data.bestOriginX = candidateOriginX;
                data.bestOriginZ = candidateOriginZ;
            }
            LOGGER.info("[TN-C Sky Island] surveyed candidate {}/{} at {},{} score={}",
                    data.surveyCandidate + 1, ISLAND_OFFSETS.length,
                    candidateOriginX, candidateOriginZ, data.surveyScore);
            data.surveyCandidate++;
            data.surveySample = 0;
            data.surveyScore = 0;
        }
        data.setDirty();
    }

    private static List<BlockPos> surveySamples(SkyIslandManifest manifest) {
        List<BlockPos> result = new ArrayList<>(manifest.rootTips());
        // The central shell is much thicker than the roots. A conservative local Y=30 keeps it
        // away from extreme mountain peaks without rejecting ordinary hills under the island.
        result.add(new BlockPos(
                manifest.dimensions().getX() / 2,
                30,
                manifest.dimensions().getZ() / 2
        ));
        return result;
    }

    private static void finishSurvey(ServerLevel level, SkyIslandSavedData data, SkyIslandManifest manifest) {
        if (data.bestSurveyScore == Integer.MAX_VALUE) {
            throw new IllegalStateException("no island candidate was surveyed");
        }

        data.originX = data.bestOriginX;
        data.originY = manifest.worldOriginY();
        data.originZ = data.bestOriginZ;
        data.centerX = data.originX + manifest.townCenterLocal().getX();
        data.centerY = data.originY + manifest.townCenterLocal().getY();
        data.centerZ = data.originZ + manifest.townCenterLocal().getZ();
        data.arrivalX = data.originX + manifest.arrivalLocal().getX();
        data.arrivalY = data.originY + manifest.arrivalLocal().getY();
        data.arrivalZ = data.originZ + manifest.arrivalLocal().getZ();

        BlockPos portalOrigin = chooseGroundPortal(level);
        data.groundPortalX = portalOrigin.getX();
        data.groundPortalY = portalOrigin.getY();
        data.groundPortalZ = portalOrigin.getZ();
        data.layoutReady = true;
        data.phase = SkyIslandSavedData.Phase.BUILDING;
        data.nextPiece = 0;
        data.setDirty();

        LOGGER.info("[TN-C Sky Island] layout chosen: origin={}, {}, {}; portal={}, {}, {}; score={}",
                data.originX, data.originY, data.originZ,
                data.groundPortalX, data.groundPortalY, data.groundPortalZ,
                data.bestSurveyScore);
        notifyAll(level.getServer(), "§b天空岛位置勘测完成，开始分批生成 "
                + manifest.pieces().size() + " 个结构块。");
    }

    /** Selects the flattest dry 15x15 portal site in a ring around world spawn. */
    private static BlockPos chooseGroundPortal(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        int bestScore = Integer.MAX_VALUE;
        BlockPos best = null;

        for (int[] offset : PORTAL_OFFSETS) {
            int centerX = spawn.getX() + offset[0];
            int centerZ = spawn.getZ() + offset[1];
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            int water = 0;
            for (int[] sample : PORTAL_SAMPLES) {
                int x = centerX + sample[0];
                int z = centerZ + sample[1];
                level.getChunk(x >> 4, z >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                if (!level.getFluidState(new BlockPos(x, y, z)).isEmpty()) {
                    water++;
                }
            }
            int score = (maxY - minY) * 200 + water * 20_000;
            if (score < bestScore) {
                bestScore = score;
                best = new BlockPos(centerX - 7, maxY, centerZ - 7);
            }
        }

        if (best == null) {
            throw new IllegalStateException("no ground portal candidate was available");
        }
        return best;
    }

    private static void buildStep(ServerLevel level, SkyIslandSavedData data, SkyIslandManifest manifest) throws IOException {
        if (data.nextPiece >= manifest.pieces().size()) {
            releaseActiveChunks(level, data);
            data.phase = SkyIslandSavedData.Phase.FINALIZING;
            data.setDirty();
            return;
        }

        SkyIslandManifest.Piece piece = manifest.pieces().get(data.nextPiece);
        BlockPos target = new BlockPos(data.originX, data.originY, data.originZ).offset(piece.offset());

        if (data.activeChunks.isEmpty()) {
            forcePieceChunks(level, data, target, piece.size());
            data.waitUntilTick = level.getGameTime() + 2L;
            data.setDirty();
            return;
        }
        if (level.getGameTime() < data.waitUntilTick) {
            return;
        }
        ensureActiveChunksLoaded(level, data);

        StructureTemplate template = level.getStructureManager().get(piece.resource())
                .orElseThrow(() -> new IOException("missing structure template " + piece.resource()));
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(false)
                .setKeepLiquids(false)
                .setFinalizeEntities(true);
        boolean placed = template.placeInWorld(
                level,
                target,
                target,
                settings,
                RandomSource.create(manifest.seed() ^ data.nextPiece),
                2
        );
        if (!placed) {
            throw new IOException("template placement returned false for " + piece.resource());
        }

        int placedIndex = data.nextPiece;
        data.nextPiece++;
        data.setDirty();
        releaseActiveChunks(level, data);

        int oldPercent = placedIndex * 100 / manifest.pieces().size();
        int newPercent = data.nextPiece * 100 / manifest.pieces().size();
        if (newPercent / 10 > oldPercent / 10 && newPercent < 100) {
            notifyAll(level.getServer(), "§b天空岛生成进度：" + newPercent + "%（"
                    + data.nextPiece + "/" + manifest.pieces().size() + "）");
        }
        LOGGER.info("[TN-C Sky Island] placed {}/{} {} at {}",
                data.nextPiece, manifest.pieces().size(), piece.resource(), target);
    }

    private static void finalizeBuild(ServerLevel level, SkyIslandSavedData data, SkyIslandManifest manifest) throws IOException {
        BlockPos islandPortalOrigin = new BlockPos(
                data.arrivalX - 7,
                data.arrivalY - 1,
                data.arrivalZ - 7
        );
        BlockPos groundPortalOrigin = new BlockPos(
                data.groundPortalX,
                data.groundPortalY,
                data.groundPortalZ
        );

        if (data.activeChunks.isEmpty()) {
            forceAreaChunks(level, data, islandPortalOrigin, 15, 4, 15);
            forceAreaChunks(level, data, groundPortalOrigin, 15, 4, 15);
            data.waitUntilTick = level.getGameTime() + 2L;
            data.setDirty();
            return;
        }
        if (level.getGameTime() < data.waitUntilTick) {
            return;
        }
        ensureActiveChunksLoaded(level, data);

        placeTemplate(level, manifest.islandPortal(), islandPortalOrigin, manifest.seed() ^ 0x51A1D5L);
        placeTemplate(level, manifest.groundPortal(), groundPortalOrigin, manifest.seed() ^ 0x6A0D5L);
        supportGroundPortal(level, groundPortalOrigin);

        ensureLanding(level, islandLanding(data));
        ensureLanding(level, groundLanding(data));
        releaseActiveChunks(level, data);

        data.phase = SkyIslandSavedData.Phase.COMPLETE;
        data.lastError = "";
        data.setDirty();
        LOGGER.info("[TN-C Sky Island] generation complete: pieces={}, blocks={}, center={}, {}, {}; arrival={}, {}, {}",
                manifest.pieces().size(), manifest.nonAirBlocks(),
                data.centerX, data.centerY, data.centerZ,
                data.arrivalX, data.arrivalY, data.arrivalZ);

        notifyAll(level.getServer(), "§a天空岛新手村生成完成！地面传送阵坐标：X "
                + (data.groundPortalX + 7) + " / Y " + (data.groundPortalY + 2)
                + " / Z " + (data.groundPortalZ + 7));
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            tellCoordinates(player, data);
        }
    }

    private static void placeTemplate(ServerLevel level, ResourceLocation resource, BlockPos target, long seed)
            throws IOException {
        StructureTemplate template = level.getStructureManager().get(resource)
                .orElseThrow(() -> new IOException("missing structure template " + resource));
        boolean placed = template.placeInWorld(
                level,
                target,
                target,
                new StructurePlaceSettings().setIgnoreEntities(false).setKeepLiquids(false),
                RandomSource.create(seed),
                2
        );
        if (!placed) {
            throw new IOException("template placement returned false for " + resource);
        }
    }

    private static void supportGroundPortal(ServerLevel level, BlockPos origin) {
        for (int x = 0; x < 15; x++) {
            for (int z = 0; z < 15; z++) {
                double distance = Math.hypot(x - 7, z - 7);
                if (distance > 6.7) {
                    continue;
                }
                for (int depth = 1; depth <= 5; depth++) {
                    BlockPos support = origin.offset(x, -depth, z);
                    if (!level.getBlockState(support).isAir()
                            && level.getFluidState(support).isEmpty()) {
                        break;
                    }
                    level.setBlock(support, depth <= 2
                            ? Blocks.COBBLESTONE.defaultBlockState()
                            : Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void ensureLanding(ServerLevel level, BlockPos landing) {
        BlockPos floor = landing.below();
        if (level.getBlockState(floor).isAir() || !level.getFluidState(floor).isEmpty()) {
            level.setBlock(floor, Blocks.SEA_LANTERN.defaultBlockState(), 3);
        }
        level.setBlock(landing, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(landing.above(), Blocks.AIR.defaultBlockState(), 3);
    }

    private static void forcePieceChunks(
            ServerLevel level,
            SkyIslandSavedData data,
            BlockPos target,
            net.minecraft.core.Vec3i size
    ) {
        forceAreaChunks(level, data, target, size.getX(), size.getY(), size.getZ());
    }

    private static void forceAreaChunks(
            ServerLevel level,
            SkyIslandSavedData data,
            BlockPos target,
            int sizeX,
            int sizeY,
            int sizeZ
    ) {
        int minChunkX = target.getX() >> 4;
        int minChunkZ = target.getZ() >> 4;
        int maxChunkX = (target.getX() + sizeX - 1) >> 4;
        int maxChunkZ = (target.getZ() + sizeZ - 1) >> 4;
        BlockPos owner = ticketOwner(data);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                long packed = ChunkPos.asLong(chunkX, chunkZ);
                if (data.activeChunks.contains(packed)) {
                    continue;
                }
                // A false return means this exact TN-C owner already had the ticket (for example
                // after a crash). It is still ours and must still be tracked for later release.
                ForgeChunkManager.forceChunk(level, TNMod.MODID, owner, chunkX, chunkZ, true, false);
                level.getChunk(chunkX, chunkZ);
                data.activeChunks.add(packed);
                data.setDirty();
            }
        }
    }

    private static void releaseActiveChunks(ServerLevel level, SkyIslandSavedData data) {
        if (data.activeChunks.isEmpty()) {
            return;
        }
        BlockPos owner = ticketOwner(data);
        // Remove one checkpoint entry only after its release call returned normally. If an
        // exception interrupts cleanup, all untouched entries remain durable for the next retry.
        while (!data.activeChunks.isEmpty()) {
            long packed = data.activeChunks.get(data.activeChunks.size() - 1);
            ChunkPos chunk = new ChunkPos(packed);
            ForgeChunkManager.forceChunk(level, TNMod.MODID, owner, chunk.x, chunk.z, false, false);
            data.activeChunks.remove(data.activeChunks.size() - 1);
            data.setDirty();
        }
    }

    /** Synchronous guard for crash recovery before touching a template's blocks. */
    private static void ensureActiveChunksLoaded(ServerLevel level, SkyIslandSavedData data) {
        for (long packed : data.activeChunks) {
            ChunkPos chunk = new ChunkPos(packed);
            level.getChunk(chunk.x, chunk.z);
        }
    }

    private static BlockPos ticketOwner(SkyIslandSavedData data) {
        return new BlockPos(data.originX, data.originY, data.originZ);
    }

    private static void portalTick(ServerLevel level, SkyIslandSavedData data, SkyIslandManifest manifest) {
        if (!isComplete(data, manifest)) {
            PORTAL_CHARGES.clear();
            return;
        }

        BlockPos ground = groundLanding(data);
        BlockPos island = islandLanding(data);
        boolean effectsTick = level.getGameTime() % 5L == 0L;
        if (effectsTick) {
            portalParticles(level, ground);
            portalParticles(level, island);
        }

        Set<UUID> presentPlayers = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            presentPlayers.add(player.getUUID());
            long cooldown = player.getPersistentData().getLong(PLAYER_COOLDOWN);
            if (level.getGameTime() < cooldown) {
                PORTAL_CHARGES.remove(player.getUUID());
                continue;
            }
            boolean atGround = insidePortal(player, ground);
            boolean atIsland = insidePortal(player, island);
            updatePortalCharge(player, level, manifest, ground, island, atGround, atIsland, effectsTick);
        }
        PORTAL_CHARGES.keySet().removeIf(uuid -> !presentPlayers.contains(uuid));
    }

    private static void updatePortalCharge(
            ServerPlayer player,
            ServerLevel level,
            SkyIslandManifest manifest,
            BlockPos ground,
            BlockPos island,
            boolean atGround,
            boolean atIsland,
            boolean effectsTick
    ) {
        UUID playerId = player.getUUID();
        PortalChargeState charge = PORTAL_CHARGES.get(playerId);
        boolean inPortal = atGround || atIsland;
        boolean fromGround = atGround;
        boolean alive = player.isAlive() && !player.isRemoved();

        if (!alive || !inPortal) {
            if (charge != null) {
                PORTAL_CHARGES.remove(playerId);
                if (alive) {
                    player.displayClientMessage(Component.literal("§7传送蓄能已取消"), true);
                }
            }
            return;
        }

        if (charge == null || charge.fromGround() != fromGround) {
            charge = new PortalChargeState(fromGround, level.getGameTime());
            PORTAL_CHARGES.put(playerId, charge);
            player.displayClientMessage(Component.literal("§d传送阵正在蓄能……请留在阵中"), true);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.BLOCKS, 0.55F, 1.25F);
        }

        PortalChargeState.Decision decision = charge.evaluate(alive, atGround, atIsland, level.getGameTime());
        if (decision == PortalChargeState.Decision.CANCEL) {
            PORTAL_CHARGES.remove(playerId);
            player.displayClientMessage(Component.literal("§7传送蓄能已取消"), true);
            return;
        }

        long elapsed = level.getGameTime() - charge.startedAt();
        double progress = Math.min(1.0D, elapsed / (double) PortalChargeState.DURATION_TICKS);
        if (effectsTick) {
            chargeParticles(level, player, progress);
            int percent = (int) Math.min(100L,
                    elapsed * 100L / PortalChargeState.DURATION_TICKS);
            player.displayClientMessage(Component.literal("§d传送蓄能 §f" + percent + "%"), true);
        }

        if (decision != PortalChargeState.Decision.COMPLETE) {
            return;
        }

        PORTAL_CHARGES.remove(playerId);
        BlockPos source = fromGround ? ground : island;
        BlockPos destination = fromGround ? island : ground;
        departureBurst(level, source);
        level.playSound(null, source, SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.9F, 0.8F);
        teleport(player, level, destination);
        arrivalBurst(level, destination);
        level.playSound(null, destination, SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.8F, 1.25F);
    }

    private static boolean insidePortal(Player player, BlockPos landing) {
        double dx = player.getX() - (landing.getX() + 0.5D);
        double dz = player.getZ() - (landing.getZ() + 0.5D);
        return dx * dx + dz * dz <= 10.0D
                && player.getY() >= landing.getY() - 1.0D
                && player.getY() <= landing.getY() + 3.5D;
    }

    private static void teleport(ServerPlayer player, ServerLevel level, BlockPos destination) {
        level.getChunk(destination.getX() >> 4, destination.getZ() >> 4);
        ensureLanding(level, destination);
        player.getPersistentData().putLong(PLAYER_COOLDOWN, level.getGameTime() + PORTAL_COOLDOWN_TICKS);
        player.teleportTo(
                level,
                destination.getX() + 0.5D,
                destination.getY(),
                destination.getZ() + 0.5D,
                player.getYRot(),
                player.getXRot()
        );
    }

    private static void portalParticles(ServerLevel level, BlockPos landing) {
        if (!level.hasChunkAt(landing)) {
            return;
        }
        level.sendParticles(
                ParticleTypes.PORTAL,
                landing.getX() + 0.5D,
                landing.getY() + 0.6D,
                landing.getZ() + 0.5D,
                12,
                2.1D,
                0.8D,
                2.1D,
                0.02D
        );
        level.sendParticles(
                ParticleTypes.ENCHANT,
                landing.getX() + 0.5D,
                landing.getY() + 0.2D,
                landing.getZ() + 0.5D,
                5,
                1.4D,
                0.2D,
                1.4D,
                0.01D
        );
    }

    private static void chargeParticles(ServerLevel level, ServerPlayer player, double progress) {
        double centerX = player.getX();
        double centerY = player.getY() + 0.15D;
        double centerZ = player.getZ();
        double radius = 1.65D - progress * 0.85D;
        double phase = level.getGameTime() * 0.18D;
        int points = 8 + (int) Math.floor(progress * 6.0D);
        for (int index = 0; index < points; index++) {
            double angle = phase + Math.PI * 2.0D * index / points;
            level.sendParticles(
                    ParticleTypes.END_ROD,
                    centerX + Math.cos(angle) * radius,
                    centerY + progress * 1.2D,
                    centerZ + Math.sin(angle) * radius,
                    1,
                    0.0D,
                    0.015D,
                    0.0D,
                    0.0D
            );
        }
        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                centerX,
                centerY + 0.8D,
                centerZ,
                4 + (int) Math.floor(progress * 12.0D),
                0.9D - progress * 0.45D,
                0.7D,
                0.9D - progress * 0.45D,
                0.035D
        );
        level.sendParticles(
                ParticleTypes.ENCHANT,
                centerX,
                centerY + 0.45D,
                centerZ,
                5 + (int) Math.floor(progress * 9.0D),
                1.15D - progress * 0.35D,
                0.35D + progress * 0.5D,
                1.15D - progress * 0.35D,
                0.025D
        );
    }

    private static void departureBurst(ServerLevel level, BlockPos center) {
        level.sendParticles(ParticleTypes.FLASH,
                center.getX() + 0.5D, center.getY() + 1.0D, center.getZ() + 0.5D,
                2, 0.2D, 0.4D, 0.2D, 0.0D);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                center.getX() + 0.5D, center.getY() + 0.8D, center.getZ() + 0.5D,
                45, 1.3D, 1.0D, 1.3D, 0.12D);
    }

    private static void arrivalBurst(ServerLevel level, BlockPos center) {
        level.sendParticles(ParticleTypes.END_ROD,
                center.getX() + 0.5D, center.getY() + 0.8D, center.getZ() + 0.5D,
                32, 1.7D, 1.1D, 1.7D, 0.06D);
        level.sendParticles(ParticleTypes.ENCHANT,
                center.getX() + 0.5D, center.getY() + 0.4D, center.getZ() + 0.5D,
                36, 2.2D, 0.5D, 2.2D, 0.08D);
    }


    private static BlockPos groundLanding(SkyIslandSavedData data) {
        return new BlockPos(data.groundPortalX + 7, data.groundPortalY + 2, data.groundPortalZ + 7);
    }

    private static BlockPos islandLanding(SkyIslandSavedData data) {
        return new BlockPos(data.arrivalX, data.arrivalY + 1, data.arrivalZ);
    }

    private static void tellCoordinates(ServerPlayer player, SkyIslandSavedData data) {
        BlockPos ground = groundLanding(data);
        player.sendSystemMessage(Component.literal("§b地面传送阵：X " + ground.getX()
                + " / Y " + ground.getY() + " / Z " + ground.getZ()));
        player.sendSystemMessage(Component.literal("§b天空岛新手村中心：X " + data.centerX
                + " / Y " + data.centerY + " / Z " + data.centerZ));
        player.sendSystemMessage(Component.literal("§b新手村南门：X " + data.arrivalX
                + " / Y " + (data.arrivalY + 1) + " / Z " + data.arrivalZ));
    }

    private static boolean isComplete(SkyIslandSavedData data, SkyIslandManifest manifest) {
        return data.version >= manifest.version()
                && data.phase == SkyIslandSavedData.Phase.COMPLETE
                && data.layoutReady;
    }

    private static SkyIslandManifest manifest(MinecraftServer server) throws IOException {
        SkyIslandManifest cached = MANIFESTS.get(server);
        if (cached != null) {
            return cached;
        }
        SkyIslandManifest loaded = SkyIslandManifest.load(server);
        MANIFESTS.put(server, loaded);
        return loaded;
    }

    private static void fail(ServerLevel level, SkyIslandSavedData data, String context, Throwable error) {
        LOGGER.error("[TN-C Sky Island] {}", context, error);
        String message = context + "：" + shortMessage(error);
        data.lastError = message;
        data.phase = SkyIslandSavedData.Phase.ERROR;
        data.setDirty();
        try {
            releaseActiveChunks(level, data);
        } catch (Throwable cleanupError) {
            LOGGER.error("[TN-C Sky Island] ticket cleanup also failed", cleanupError);
            data.lastError += "；票据清理失败：" + shortMessage(cleanupError);
            data.setDirty();
        }
        notifyAll(level.getServer(), "§c天空岛生成失败，未写入完成标记：" + message
                + "。下次进入世界会从检查点重试。");
    }

    private static String shortMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        return message.length() > 240 ? message.substring(0, 240) : message;
    }

    private static void notifyAll(MinecraftServer server, String message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(message));
        }
    }
}
