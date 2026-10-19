package com.tnc.tnc.world.eartharena;

import com.tnc.tnc.TNMod;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import static net.minecraft.commands.Commands.literal;

@Mod.EventBusSubscriber(modid = "tnc")
public final class EarthArena {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, id("earth_arena"));
    private static final Set<UUID> PENDING = new HashSet<>();
    public static Item KEYSTONE;
    public static net.minecraft.world.level.block.Block PLATFORM;
    public static net.minecraft.world.level.block.Block DARK_PLATFORM;
    public static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("tnc", name); }

    @Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Content {
        @SubscribeEvent public static void register(RegisterEvent event) {
            event.register(Registries.BLOCK, helper -> {
                PLATFORM = new net.minecraft.world.level.block.Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                        .mapColor(net.minecraft.world.level.material.MapColor.TERRACOTTA_BROWN).strength(-1, 3600000).sound(net.minecraft.world.level.block.SoundType.STONE));
                helper.register(id("earth_arena_pillar_stone"), PLATFORM);
                DARK_PLATFORM = new net.minecraft.world.level.block.Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(PLATFORM)
                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK));
                helper.register(id("earth_arena_black_pillar_stone"), DARK_PLATFORM);
            });
            event.register(Registries.CHUNK_GENERATOR, helper -> helper.register(id("earth_arena"), EarthArenaChunkGenerator.CODEC));
            event.register(Registries.ITEM, helper -> {
                helper.register(id("earth_arena_pillar_stone"), new net.minecraft.world.item.BlockItem(PLATFORM, new Item.Properties()));
                helper.register(id("earth_arena_black_pillar_stone"), new net.minecraft.world.item.BlockItem(DARK_PLATFORM, new Item.Properties()));
                KEYSTONE = new Item(new Item.Properties().stacksTo(1)) {
                    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                        if (player instanceof ServerPlayer serverPlayer) travel(serverPlayer);
                        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
                    }
                };
                helper.register(id("earth_arena_keystone"), KEYSTONE);
            });
        }
        @SubscribeEvent public static void creative(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey().equals(TNMod.TNC_TAB.getKey())) { event.accept(KEYSTONE); event.accept(PLATFORM); event.accept(DARK_PLATFORM); }
        }
    }

    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) { PENDING.clear(); }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("tnc").then(literal("earth").requires(source -> source.hasPermission(2))
                .executes(context -> { travel(context.getSource().getPlayerOrException()); return 1; })));
        event.getDispatcher().register(literal("tnc").then(literal("eartharena").requires(source -> source.hasPermission(2))
                .executes(context -> { travel(context.getSource().getPlayerOrException()); return 1; })));
    }

    public static void travel(ServerPlayer player) {
        if (!PENDING.add(player.getUUID())) return;
        var server = player.getServer();
        boolean returning = player.level().dimension().equals(KEY);
        CompoundTag saved = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, saved);
        ServerLevel destination;
        Vec3 location;
        float yaw, pitch;
        if (returning) {
            ResourceLocation previous = ResourceLocation.tryParse(saved.getString("EarthArenaReturnDimension"));
            destination = previous == null ? server.overworld() : server.getLevel(ResourceKey.create(Registries.DIMENSION, previous));
            if (destination == null || destination.dimension().equals(KEY)) destination = server.overworld();
            location = saved.contains("EarthArenaReturnX") ? new Vec3(saved.getDouble("EarthArenaReturnX"), saved.getDouble("EarthArenaReturnY"), saved.getDouble("EarthArenaReturnZ")) : Vec3.atBottomCenterOf(destination.getSharedSpawnPos());
            yaw = saved.getFloat("EarthArenaReturnYaw"); pitch = saved.getFloat("EarthArenaReturnPitch");
        } else {
            destination = server.getLevel(KEY);
            if (destination == null) { PENDING.remove(player.getUUID()); player.sendSystemMessage(Component.literal("地核战场尚未载入，请重新进入存档。")); return; }
            saved.putString("EarthArenaReturnDimension", player.level().dimension().location().toString());
            saved.putDouble("EarthArenaReturnX", player.getX()); saved.putDouble("EarthArenaReturnY", player.getY()); saved.putDouble("EarthArenaReturnZ", player.getZ());
            saved.putFloat("EarthArenaReturnYaw", player.getYRot()); saved.putFloat("EarthArenaReturnPitch", player.getXRot());
            location = new Vec3(8.5, 0, 8.5); yaw = -90; pitch = 0;
        }
        ServerLevel target = destination;
        Vec3 point = location;
        float finalYaw = yaw, finalPitch = pitch;
        player.sendSystemMessage(Component.literal(returning ? "正在返回原来的位置……" : "正在进入地核战场……"));
        target.getChunkSource().getChunkFuture(net.minecraft.util.Mth.floor(point.x) >> 4, net.minecraft.util.Mth.floor(point.z) >> 4, ChunkStatus.FULL, true)
                .whenCompleteAsync((chunk, error) -> {
                        if (error != null || chunk == null || chunk.left().isEmpty() || player.hasDisconnected() || player.isRemoved()) {
                            if (!player.hasDisconnected()) player.sendSystemMessage(Component.literal("战场加载未完成，请稍后重试。"));
                            PENDING.remove(player.getUUID());
                            return;
                        }
                        var ready=returning?java.util.concurrent.CompletableFuture.<Void>completedFuture(null):EarthArenaUpgrade.ready(target.getChunk(net.minecraft.util.Mth.floor(point.x)>>4,net.minecraft.util.Mth.floor(point.z)>>4));
                        ready.whenCompleteAsync((ignored,upgradeError)->{
                            try{
                                if(upgradeError!=null||player.hasDisconnected()||player.isRemoved()||!player.isAlive())return;
                                double y = returning ? point.y : target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 8, 8) + .1;
                                player.teleportTo(target, point.x, y, point.z, finalYaw, finalPitch);
                                player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0;
                            }finally{PENDING.remove(player.getUUID());}
                        },server);
                }, server);
    }
}
