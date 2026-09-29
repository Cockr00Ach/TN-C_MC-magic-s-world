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
import java.util.UUID;

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

    /** 回魔相关的诊断日志（见 {@link #onPlayerTick}）✓ */
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TN-C/mana");

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
        // ★ 换世界/重进存档一定走这里 ⇒ 把回魔那几个 static 记账表清掉 ✓（见 forgetTimers）
        forgetTimers(player.getUUID());
        get(player).ifPresent(data -> {
            // Snapshot legacy mana/XP before default initialization or recalculation.
            com.tnc.tnc.adventure.AdventureService.profile(player);
            if (!data.isInitialized()) {
                data.assignDefaultAffinities(Config.defaultAffinity);
            }
            refreshMaxMana(player, data);
            // 魔法石才是权威数据：登录时把法杖内容对齐到"当前这一页的配装"。
            // 法杖丢了/内容少了都能在这里修回来 —— 否则丢了法杖就等于法术白解锁了。
            // ⚠️ 走 ensureWand(player, data.learnedView())：由它去问 SpellCatalog.wandSpellIds，
            //    这样页号与配装不会被别的路径冲掉 ✓
            if (Config.restoreWandOnLogin) {
                com.tnc.tnc.magic.compat.SpellEngineBridge.WandResult synced =
                        com.tnc.tnc.magic.compat.SpellEngineBridge.ensureWand(player, data.learnedView());
                if (synced == com.tnc.tnc.magic.compat.SpellEngineBridge.WandResult.GAVE_NEW_WAND) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§b[TN-C] §r没找到你的法杖，已按魔法石记录补发一根"));
                }
            }
            MagicStoneNetwork.syncTo(player);
        });
    }

    /**
     * 最近一次施法的时刻（只服务端用；单位见 {@link #nowTicks()}）。
     *
     * <p>用来实现"施法后短暂不回魔"：回魔是「每次 1 + 上限的 5%」，上限 620 时每 2 秒回 32，
     * 而一级法术只花 20 —— 不暂停的话扣完不到一个周期就回满，玩家在 HUD 上什么都看不见。
     */
    private static final java.util.Map<java.util.UUID, Long> LAST_CAST =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * ★★ 时间基准：**JVM 单调时钟**（单位 = tick，50 ms 一格），**不是** {@code level.getGameTime()} ✗✗
     *
     * <h2>为什么必须换掉（2026-09-29 实机，作者："怎么魔力又不自动恢复了"）</h2>
     * 原来 {@link #LAST_REGEN} / {@link #LAST_CAST} 存的都是<b>当前世界</b>的 gameTime，
     * 而这两个表是 <b>static</b>（跟着 JVM 走、跨世界不清 ✗）。于是：
     * <pre>
     *   世界 A（老存档，gameTime ≈ 1200000）最后回魔 → LAST_REGEN = 1200000 ✗
     *   换到世界 B（**新开的存档**，gameTime ≈ 300）→ now - lastRegen = **负数** ✗✗
     *   `now - lastRegen &lt; interval` ⇒ **负数 &lt; 40 恒成立** ⇒ 直接 return，
     *   既不回魔、也不打任何日志 ⇒ 表现就是"魔力卡死不动、日志一行没有" ✗✗✗
     * </pre>
     * 日志实锤：19:54:56 在世界 A 里还在 `mana regen 188 -> 202 / 270`；20:03 换到新世界之后
     * 整局 {@code TN-C/mana} <b>只有一行</b>（driver alive），魔力卡在 90 上 20 多秒不动 ✓。
     *
     * <p>换成单调时钟之后：跨世界、跨维度、世界时间倒退都不会再让回魔"永久停摆" ✓，
     * 而且和真实时间一致（玩家感知的 2 秒就是 2 秒 ✓）。
     */
    private static long nowTicks() {
        return System.currentTimeMillis() / 50L;
    }

    /** 记一次施法（由 SPELL_CAST 钩子扣完魔力后调用）。 */
    public static void markCast(ServerPlayer player) {
        LAST_CAST.put(player.getUUID(), nowTicks());
    }

    /**
     * ★ 换世界/重进存档时清掉按玩家 UUID 记账的临时表 ✓
     * （单调时钟已经让旧值无害，但留着旧条目只会让"回魔卡住"的判据误报 ✗）
     */
    public static void forgetTimers(java.util.UUID id) {
        LAST_REGEN.remove(id);
        LAST_CAST.remove(id);
        LAST_STUCK_WARN.remove(id);
    }

    /**
     * 魔力恢复：每 {@link com.tnc.tnc.Config#manaRegenIntervalTicks} tick 回一次
     * （默认 40 tick = 2 秒），每次回多少见 {@link com.tnc.tnc.Config#manaRegenFor}。
     *
     * <h2>★ 为什么有两个入口（2026-09-29 实测）</h2>
     * 这条路原来只挂在 {@code PlayerTickEvent} 上。作者那边实测（日志实锤）：
     * <b>整个 {@code PlayerTickEvent} 一次都没进来过</b> —— 魔力在 90 上一动不动 28 秒、
     * 而 {@code TN-C/mana} 那一行**一次都没打**（它只在回魔真的发生时打 ✗），
     * 同时 {@code ServerTickEvent} 是好的（NPC 补位日志每 15 秒照打 ✓）。
     *
     * <p>所以现在两条路都挂上：谁活着谁驱动 ✓ —— {@link #tickManaRegen} 内部有
     * "每个周期只回一次"的闸门（{@link #LAST_REGEN}），两个入口都来也只回一次 ✗ 不会翻倍。
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        markDriver("PlayerTickEvent");
        tickManaRegen(serverPlayer);
    }

    /** 兜底入口：服务端每 tick 遍历所有玩家（见 {@link #onPlayerTick} 的说明）✓ */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        markDriver("ServerTickEvent");
        for (ServerPlayer serverPlayer : event.getServer().getPlayerList().getPlayers()) {
            tickManaRegen(serverPlayer);
        }
    }

    /** 上一个真正回过魔的时刻：uuid -> {@link #nowTicks()}（"每周期只回一次"的闸门）✓ */
    private static final java.util.Map<java.util.UUID, Long> LAST_REGEN =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** "回魔算出来是 0 点"的告警节流：uuid -> 上次告警的 {@link #nowTicks()} ✓ */
    private static final java.util.Map<java.util.UUID, Long> LAST_STUCK_WARN =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** "施法后暂停"日志的节流：uuid -> 上次打印的 {@link #nowTicks()}（不然会每 tick 刷一行 ✗） */
    private static final java.util.Map<java.util.UUID, Long> LAST_PAUSE_LOG =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 哪个入口在驱动（只记一次，方便一眼看出 PlayerTickEvent 到底活没活）✓ */
    private static final java.util.Set<String> DRIVERS_SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void markDriver(String name) {
        if (DRIVERS_SEEN.add(name)) {
            LOGGER.info("TN-C: mana regen driver alive: {}", name);
        }
    }

    /**
     * 真正干活的回魔逻辑（三个入口共用：PlayerTickEvent / ServerTickEvent / TnSpellMechanics.tickPlayer）。
     *
     * <p><b>幂等</b>：同一周期内重复调用不会多回 ✓（{@link #LAST_REGEN} 闸门）。
     * 三个入口是有意为之：实机日志里见过 {@code PlayerTickEvent} 整局不进、
     * 也见过换世界后 {@code ServerTickEvent} 那条不再进来 —— 每条都单独能驱动 ✓。
     */
    public static void tickManaRegen(ServerPlayer serverPlayer) {
        if (serverPlayer.level().isClientSide()) {
            return;
        }
        int interval = Math.max(1, Config.manaRegenIntervalTicks);
        // ★ 单调时钟（见 nowTicks 的说明）：**绝不能**再用 level.getGameTime() ✗ ——
        //   换到新世界时 gameTime 会倒退，delta 变负数会让这里的闸门永久成立 ⇒ 回魔彻底停摆 ✗✗
        long now = nowTicks();
        Long lastRegen = LAST_REGEN.get(serverPlayer.getUUID());
        if (lastRegen != null && now >= lastRegen && now - lastRegen < interval) {
            return;                             // 这个周期已经回过了（另一个入口或上一 tick 回过）✓
        }
        // 刚放完法术先停一会儿：让玩家在 HUD 上看得见扣掉的那一截（默认 3 秒）
        int castDelay = Config.manaRegenDelayAfterCastTicks;
        if (castDelay > 0) {
            Long last = LAST_CAST.get(serverPlayer.getUUID());
            long since = last == null ? Long.MAX_VALUE : now - last;
            if (since >= 0 && since < castDelay) {
                UUID id = serverPlayer.getUUID();
                Long lastLog = LAST_PAUSE_LOG.get(id);
                if (lastLog == null || now - lastLog >= 20) {     // 1 秒最多一行 ✓
                    LAST_PAUSE_LOG.put(id, now);
                    LOGGER.info("TN-C: mana regen paused after cast ({} / {} ticks since cast)", since, castDelay);
                }
                return;
            }
        }
        get(serverPlayer).ifPresent(data -> {
            if (data.getMana() < data.getMaxMana()) {
                int regen = Config.manaRegenFor(data.getMaxMana());
                if (regen > 0) {
                    int before = data.getMana();
                    data.addMana(regen);
                    LAST_REGEN.put(serverPlayer.getUUID(), now);
                    LOGGER.info("TN-C: mana regen {} -> {} / {} (+{} every {}t)",
                            before, data.getMana(), data.getMaxMana(), regen, interval);
                    // ★ 必须同步给客户端，否则客户端的魔力值会一直停在旧值上 ——
                    //   HUD 的魔力条就永远不会回涨（玩家会以为回魔坏了）。
                    MagicStoneNetwork.syncTo(serverPlayer);
                } else {
                    // ★★ 告警而不是静默：万一以后又出现"魔力不回"，
                    //    这一行会直接把原因写出来（每次回魔量被配置算成了 0）✓
                    UUID id = serverPlayer.getUUID();
                    Long lastWarn = LAST_STUCK_WARN.get(id);
                    if (lastWarn == null || now - lastWarn >= 200) {  // 10 秒最多一行 ✓
                        LAST_STUCK_WARN.put(id, now);
                        LOGGER.warn("TN-C: mana regen STUCK: 每次回魔量算出来是 0 "
                                        + "(manaRegenPerSecond={} manaRegenPercentPerSecond={} maxMana={} interval={}t) "
                                        + "⇒ 检查 config/tnc-common.toml ✗",
                                Config.manaRegenPerSecond, Config.manaRegenPercentPerSecond,
                                data.getMaxMana(), interval);
                    }
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
        int growth = 0;
        if (player instanceof ServerPlayer serverPlayer) {
            var profile = com.tnc.tnc.adventure.AdventureService.profile(serverPlayer);
            growth = profile.level() - 1;
            data.setAdventurePoints(profile.learningPoints());
        }
        data.recomputeMaxMana(growth,
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
