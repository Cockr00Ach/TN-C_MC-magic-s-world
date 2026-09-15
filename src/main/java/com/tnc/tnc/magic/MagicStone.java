package com.tnc.tnc.magic;

import com.tnc.tnc.Config;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 把 {@link MagicStoneData} 挂到玩家身上（Forge Capability）。
 *
 * <p>这里是魔法石系统的"接入口"：其它地方统一用 {@link #get(Player)} 取数据。
 * 数据是<b>服务端权威</b>的，客户端那份靠 {@link MagicStoneNetwork} 同步过来给 GUI/HUD 读。
 *
 * <p>注意：本类上的 {@code @Mod.EventBusSubscriber} <b>不能省</b> ——
 * 挂载/重生/登录/tick 这些游戏事件全靠它才会被注册（默认就是 Forge 总线）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public class MagicStone {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "magic_stone");

    public static final Capability<MagicStoneData> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    // ------------------------------------------------------------------
    //  事件（Forge 总线）
    // ------------------------------------------------------------------

    /** 玩家实体创建时挂上数据。 */
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(ID, new Provider());
        }
    }

    /** 死亡重生/换维度时把数据带过去。 */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // 1.20.1 里旧玩家的 capability 已经被 invalidate，必须先 reviveCaps 才能读
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(CAPABILITY).ifPresent(oldData ->
                event.getEntity().getCapability(CAPABILITY).ifPresent(newData ->
                        newData.copyFrom(oldData)));
        event.getOriginal().invalidateCaps();

        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            MagicStoneNetwork.syncTo(serverPlayer);
        }
    }

    /**
     * 玩家第一次进游戏时分配亲和力（天生固定，只做一次），
     * 之后每次登录/升级都会重算魔力上限。
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        get(player).ifPresent(data -> {
            if (!data.isInitialized()) {
                data.assignDefaultAffinities(Config.defaultAffinity);
            }
            refreshMaxMana(player, data);
            // 魔法石才是权威数据：登录时把法杖内容对齐到"已解锁"的集合。
            // 法杖丢了/内容少了都能在这里修回来 —— 否则丢了法杖就等于法术白解锁了。
            if (Config.restoreWandOnLogin) {
                com.tnc.tnc.magic.compat.SpellEngineBridge.WandResult synced =
                        com.tnc.tnc.magic.compat.SpellEngineBridge.ensureWand(player, data.getLearned());
                if (synced == com.tnc.tnc.magic.compat.SpellEngineBridge.WandResult.GAVE_NEW_WAND) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§b[TN-C] §r没找到你的法杖，已按魔法石记录补发一根"));
                }
            }
            MagicStoneNetwork.syncTo(player);
        });
    }

    /** 魔力恢复：每秒回一次，回多少 = 固定值 + 上限百分比（见 Config.manaRegenFor）。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide()) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        get(player).ifPresent(data -> {
            if (data.getMana() < data.getMaxMana()) {
                int regen = Config.manaRegenFor(data.getMaxMana());
                if (regen > 0) {
                    data.addMana(regen);
                }
            }
        });
    }

    /** 原版等级变了（升级/掉级）就重算魔力上限。 */
    @SubscribeEvent
    public static void onPlayerChangeLevel(net.minecraftforge.event.entity.player.PlayerXpEvent.LevelChange event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            get(player).ifPresent(data -> {
                refreshMaxMana(player, data);
                MagicStoneNetwork.syncTo(player);
            });
        }
    }

    // ------------------------------------------------------------------
    //  工具方法
    // ------------------------------------------------------------------

    public static void refreshMaxMana(Player player, MagicStoneData data) {
        data.recomputeMaxMana(MagicStoneData.vanillaLevelOf(player),
                Config.manaPerAffinity, Config.manaPerVanillaLevel, Config.flatManaBonus);
    }

    public static LazyOptional<MagicStoneData> get(Player player) {
        return player.getCapability(CAPABILITY);
    }

    /** 取不到就当场创建一个（正常情况下不会走到这里）。 */
    @Nullable
    public static MagicStoneData getOrNull(Player player) {
        return get(player).orElse(null);
    }

    // ------------------------------------------------------------------
    //  Capability 提供者
    // ------------------------------------------------------------------

    public static class Provider implements ICapabilitySerializable<CompoundTag> {

        private final MagicStoneData data = new MagicStoneData();
        private final LazyOptional<MagicStoneData> optional = LazyOptional.of(() -> data);

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            if (cap == CAPABILITY) {
                return optional.cast();
            }
            return LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            data.deserializeNBT(nbt);
        }
    }

    // ------------------------------------------------------------------
    //  注册（MOD 总线）
    // ------------------------------------------------------------------

    @Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class Registration {

        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(MagicStoneData.class);
        }
    }
}
