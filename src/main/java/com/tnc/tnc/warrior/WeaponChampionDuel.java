package com.tnc.tnc.warrior;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import com.tnc.tnc.combat.DownedCombat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.event.entity.player.PlayerEvent;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/** A field challenge starts only when its recipient accepts the named opponent's invitation. */
@Mod.EventBusSubscriber(modid = "tnc")
public final class WeaponChampionDuel {
    private static final Map<UUID, WeaponChampionEntity> ACTIVE = new HashMap<>();
    private static final Map<WeaponChampion, EntityType<WeaponChampionEntity>> TYPES = new EnumMap<>(WeaponChampion.class);

    private record Invitation(WeaponChampion champion, ResourceKey<Level> dimension, long expires) {}
    private static final Map<UUID, Invitation> INVITATIONS = new HashMap<>();

    private WeaponChampionDuel() {}

    public static EntityType<WeaponChampionEntity> type(WeaponChampion champion) {
        return TYPES.get(champion);
    }

    private static boolean ready(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || DownedCombat.isDowned(player)) return false;
        if (ACTIVE.containsKey(player.getUUID())) {
            message(player, "请先结束当前的首领战。");
            return false;
        }
        if (TrainingGround.inside(player)) {
            message(player, "这里是演武场。请到场外空地与首领约战。");
            return false;
        }
        if (player.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            message(player, "和平难度下无法迎战敌对首领。");
            return false;
        }
        return true;
    }

    static boolean invite(ServerPlayer player, WeaponChampion champion) {
        if (!ready(player)) return false;
        INVITATIONS.put(player.getUUID(), new Invitation(champion, player.level().dimension(), player.level().getGameTime() + 1200));
        player.sendSystemMessage(Component.literal(champion.title + "接下了战帖。准备好后，在一分钟内")
            .append(Component.literal("【赴约开战】").withStyle(s -> s.withColor(ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tempering champion accept " + champion.id()))))
            .append("。此战会受到真实伤害。"));
        return true;
    }

    static boolean accept(ServerPlayer player, WeaponChampion champion) {
        var invitation = INVITATIONS.get(player.getUUID());
        if (invitation == null || invitation.champion() != champion || invitation.dimension() != player.level().dimension()
                || invitation.expires() < player.level().getGameTime()) {
            message(player, "这封战帖已失效，请重新选择约战对手。");
            return false;
        }
        if (!ready(player)) return false;
        var level = player.serverLevel();
        var entity = type(champion).create(level);
        Item weapon = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("tnc", champion.weapon));
        if (entity == null || weapon == null) return false;
        var floor = findArrival(player, entity);
        if (floor == null) {
            message(player, "首领附近无处落脚。请移到开阔平地，再点击赴约。");
            return false;
        }
        entity.moveTo(floor.x, floor.y, floor.z, player.getYRot() + 180, 0);
        entity.begin(player, floor, new ItemStack(weapon));
        if (!level.addFreshEntity(entity)) {
            entity.discard();
            return false;
        }
        ACTIVE.put(player.getUUID(), entity);
        INVITATIONS.remove(player.getUUID());
        message(player, champion.title + " 已赴约！" + champion.hint);
        return true;
    }

    private static Vec3 findArrival(ServerPlayer player, WeaponChampionEntity entity) {
        var level = player.serverLevel();
        var facing = Vec3.directionFromRotation(0, player.getYRot());
        double reach = 5 + entity.getBbWidth() * .5;
        for (int i = 0; i < 8; i++) {
            var offset = facing.yRot(i * (float) Math.PI / 4).scale(reach);
            var column = BlockPos.containing(player.position().add(offset));
            for (int dy = 2; dy >= -3; dy--) {
                var feet = column.above(dy);
                if (!level.hasChunkAt(feet) || !level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                        || !level.getFluidState(feet).isEmpty()) continue;
                var position = Vec3.atBottomCenterOf(feet);
                var box = entity.getDimensions(entity.getPose()).makeBoundingBox(position);
                if (entity.champion() == WeaponChampion.SPEAR_ELITE) box = box.expandTowards(0, 6, 0);
                if (level.getWorldBorder().isWithinBounds(box) && level.noCollision(entity, box) && !level.containsAnyLiquid(box)
                        && TrainingGroundData.get(player.server).arenaAt(level.dimension().location().toString(),
                            position.x, position.y, position.z) == null) return position;
            }
        }
        return null;
    }

    static boolean active(WeaponChampionEntity entity, ServerPlayer player) {
        return player != null && ACTIVE.get(player.getUUID()) == entity && player.serverLevel() == entity.level();
    }

    static void cancel(ServerPlayer player) {
        INVITATIONS.remove(player.getUUID());
        var entity = ACTIVE.remove(player.getUUID());
        if (entity != null) entity.discard();
    }

    static void finish(ServerPlayer player, WeaponChampionEntity entity, String text) {
        if (!ACTIVE.remove(player.getUUID(), entity)) return;
        entity.discard();
        if (!text.isEmpty()) result(player, entity, text);
    }

    static void victory(ServerPlayer player, WeaponChampionEntity entity, WeaponChampion champion) {
        if (!ACTIVE.remove(player.getUUID(), entity)) return;
        var fastest = TrainingGroundData.get(player.server).recordBossVictory(player.getUUID(), champion, entity.elapsedTicks());
        result(player, entity, "约战胜利 · 击败" + champion.title + " ／ 最快 " + time(fastest));
    }

    private static void result(ServerPlayer player, WeaponChampionEntity entity, String text) {
        player.sendSystemMessage(Component.literal(text + " · 破势 " + entity.openings() + " 次 · 用时 "
            + time(entity.elapsedTicks())).append(Component.literal("  [再战]")
            .withStyle(style -> style.withColor(ChatFormatting.GOLD)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("再发一封战帖")))
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tempering champion " + entity.champion().id())))));
    }

    private static String time(long ticks) {
        return String.format(Locale.ROOT, "%.1f 秒", ticks / 20.0);
    }

    static LiteralArgumentBuilder<CommandSourceStack> commands() {
        var command = Commands.literal("champion");
        var accept = Commands.literal("accept");
        for (var champion : WeaponChampion.values()) {
            accept.then(Commands.literal(champion.id()).executes(context -> accept(context.getSource().getPlayerOrException(), champion) ? 1 : 0));
            command.then(Commands.literal(champion.id())
                .executes(context -> invite(context.getSource().getPlayerOrException(), champion) ? 1 : 0));
        }
        return command.then(accept);
    }

    private static void message(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text));
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        var entity = ACTIVE.get(player.getUUID());
        if (entity != null) {
            if (entity.isRemoved() || !active(entity, player)) finish(player, entity, "");
            else if (!player.isAlive() || DownedCombat.isDowned(player)) finish(player, entity, "约战落败 · " + entity.champion().title);
            else if (player.distanceToSqr(entity) > 48 * 48) finish(player, entity, "已脱离战斗，首领离去。");
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cancel(player);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cancel(player);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        INVITATIONS.clear();
    }

    @Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterEvent event) {
            event.register(Registries.ENTITY_TYPE, helper -> {
                for (var champion : WeaponChampion.values()) {
                    var id = ResourceLocation.fromNamespaceAndPath("tnc", champion.id());
                    var type = EntityType.Builder.<WeaponChampionEntity>of(
                        (t, level) -> new WeaponChampionEntity(t, level, champion), MobCategory.MONSTER)
                        .sized(champion.width(), champion.height()).clientTrackingRange(8).noSave().build(id.toString());
                    helper.register(id, type);
                    TYPES.put(champion, type);
                }
            });
        }

        @SubscribeEvent
        public static void attributes(EntityAttributeCreationEvent event) {
            TYPES.values().forEach(type -> event.put(type, WeaponChampionEntity.attributes().build()));
        }
    }
}
