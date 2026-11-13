package com.tnc.tnc.world.yan;

import com.tnc.tnc.world.ink.InkRealmTravel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.commands.Commands.literal;

@Mod.EventBusSubscriber(modid = "tnc")
public final class YanSanctuary {
    public static final ResourceLocation ID = id("yan_sanctuary");
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, ID);
    public static Block OBSIDIAN, FLAME, ALTAR;
    private static final String RETURN = "TncYanSanctuaryReturn";
    private static final Set<UUID> TRAVELLING = new HashSet<>();

    private YanSanctuary() {}

    static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("tnc", name); }

    @Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Content {
        @SubscribeEvent
        public static void register(RegisterEvent event) {
            event.register(Registries.BLOCK, registry -> {
                OBSIDIAN = new Block(BlockBehaviour.Properties.copy(Blocks.OBSIDIAN));
                FLAME = new StormFlame();
                ALTAR = new Altar();
                registry.register(id("stormvein_obsidian"), OBSIDIAN);
                registry.register(id("storm_flame"), FLAME);
                registry.register(id("yan_altar"), ALTAR);
            });
            event.register(Registries.ITEM, registry -> {
                registry.register(id("stormvein_obsidian"), new BlockItem(OBSIDIAN, new Item.Properties()));
                registry.register(id("storm_flame"), new BlockItem(FLAME, new Item.Properties()));
                registry.register(id("yan_altar"), new BlockItem(ALTAR, new Item.Properties()));
            });
            event.register(Registries.CHUNK_GENERATOR, registry -> registry.register(ID, YanSanctuaryGenerator.CODEC));
        }

        @SubscribeEvent
        public static void creativeTab(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
                event.accept(OBSIDIAN);
                event.accept(FLAME);
                event.accept(ALTAR);
            }
        }
    }

    private static final class StormFlame extends Block {
        StormFlame() {
            super(BlockBehaviour.Properties.copy(Blocks.SOUL_FIRE).noCollission().noOcclusion()
                    .lightLevel(state -> 13).noLootTable());
        }

        @Override
        public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
        }

        @Override
        public BlockState updateShape(BlockState state, Direction direction, BlockState adjacent,
                                      LevelAccessor level, BlockPos pos, BlockPos from) {
            return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
        }
    }

    private static final class Altar extends Block {
        Altar() {
            super(BlockBehaviour.Properties.copy(Blocks.OBSIDIAN).lightLevel(state -> 7));
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                     InteractionHand hand, BlockHitResult hit) {
            if (!KEY.equals(level.dimension()) || !YanSanctuaryLayout.ALTAR.equals(pos)) return InteractionResult.PASS;
            if (player instanceof ServerPlayer serverPlayer) challenge(serverPlayer);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("tnc").then(literal("yan")
                .then(literal("enter").executes(c -> travel(c.getSource(), false)))
                .then(literal("leave").executes(c -> travel(c.getSource(), true)))
                .then(literal("boss").requires(source -> source.hasPermission(2))
                        .executes(c -> challenge(c.getSource().getPlayerOrException()) ? 1 : 0))));
    }

    private static boolean challenge(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!KEY.equals(level.dimension()) || player.isSpectator()) {
            player.sendSystemMessage(Component.translatable("message.tnc.yan.enter_first"));
            return false;
        }
        if (!player.isAlive()) return false;
        String outcome = YanSanctuaryEncounter.get(level).summon(level);
        player.sendSystemMessage(Component.translatable("message.tnc.yan." + outcome));
        return outcome.equals("summoned");
    }

    private static int travel(CommandSourceStack source, boolean leaving) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (YanPrologue.active(player)) return 0;
        ServerLevel origin = player.serverLevel();
        if (leaving != KEY.equals(origin.dimension())) {
            source.sendFailure(Component.translatable(leaving ? "message.tnc.yan.enter_first" : "message.tnc.yan.already_here"));
            return 0;
        }
        if (!TRAVELLING.add(player.getUUID())) return 0;

        CompoundTag saved = player.getPersistentData().getCompound(RETURN);
        ServerLevel destination = leaving ? returnLevel(player, saved) : player.server.getLevel(KEY);
        if (destination == null) {
            TRAVELLING.remove(player.getUUID());
            source.sendFailure(Component.translatable("message.tnc.yan.unavailable"));
            return 0;
        }
        boolean exactReturn = leaving && saved.contains("x")
                && destination.dimension().location().toString().equals(saved.getString("dimension"));
        Vec3 point = !leaving ? Vec3.atBottomCenterOf(YanSanctuaryLayout.ARRIVAL)
                : exactReturn ? new Vec3(saved.getDouble("x"), saved.getDouble("y"), saved.getDouble("z"))
                : Vec3.atBottomCenterOf(destination.getSharedSpawnPos());
        BlockPos chunk = BlockPos.containing(point);
        destination.getChunkSource().getChunkFuture(chunk.getX() >> 4, chunk.getZ() >> 4, ChunkStatus.FULL, true)
                .whenCompleteAsync((result, error) -> {
                    try {
                        if (player.hasDisconnected() || !player.isAlive() || player.level() != origin) return;
                        if (error != null || result == null || result.left().isEmpty()) {
                            player.sendSystemMessage(Component.translatable("message.tnc.yan.unavailable"));
                            return;
                        }
                        if (leaving) {
                            if (exactReturn && clear(player, destination, point)) {
                                move(player, destination, point, saved.getFloat("yaw"), saved.getFloat("pitch"));
                            } else if (!InkRealmTravel.teleport(player, destination, point.x, point.z)) {
                                player.sendSystemMessage(Component.translatable("message.tnc.yan.obstructed"));
                                return;
                            }
                            player.getPersistentData().remove(RETURN);
                        } else {
                            if (!clear(player, destination, point)) {
                                player.sendSystemMessage(Component.translatable("message.tnc.yan.obstructed"));
                                return;
                            }
                            CompoundTag tag = new CompoundTag();
                            tag.putString("dimension", origin.dimension().location().toString());
                            tag.putDouble("x", player.getX());
                            tag.putDouble("y", player.getY());
                            tag.putDouble("z", player.getZ());
                            tag.putFloat("yaw", player.getYRot());
                            tag.putFloat("pitch", player.getXRot());
                            player.getPersistentData().put(RETURN, tag);
                            move(player, destination, point, 180, -7);
                        }
                        player.sendSystemMessage(Component.translatable(leaving ? "message.tnc.yan.left" : "message.tnc.yan.arrived"));
                    } finally {
                        TRAVELLING.remove(player.getUUID());
                    }
                }, player.server);
        return 1;
    }

    private static ServerLevel returnLevel(ServerPlayer player, CompoundTag saved) {
        ResourceLocation previous = ResourceLocation.tryParse(saved.getString("dimension"));
        ServerLevel level = previous == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, previous));
        return level == null || KEY.equals(level.dimension()) ? player.server.overworld() : level;
    }

    private static boolean clear(ServerPlayer player, ServerLevel level, Vec3 at) {
        var box = player.getBoundingBox().move(at.subtract(player.position()));
        return at.y >= level.getMinBuildHeight() && box.maxY < level.getMaxBuildHeight()
                && level.getWorldBorder().isWithinBounds(box) && level.noCollision(player, box)
                && !level.containsAnyLiquid(box);
    }

    private static void move(ServerPlayer player, ServerLevel level, Vec3 at, float yaw, float pitch) {
        player.teleportTo(level, at.x, at.y, at.z, yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
    }

    @SubscribeEvent
    public static void clone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        var original = event.getOriginal().getPersistentData();
        if (original.contains(RETURN)) event.getEntity().getPersistentData().put(RETURN, original.getCompound(RETURN).copy());
    }

    @SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        TRAVELLING.clear();
    }
}
