package com.tnc.tnc.network;

import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.MagicStoneData;
import com.tnc.tnc.magic.MagicStoneLearning;
import com.tnc.tnc.magic.SpellCatalog;
import com.tnc.tnc.magic.compat.SpellEngineBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：魔法石界面的动作。
 *
 * <p>一共四件事：拉一次最新数据、解锁一个法术、切热键页、配/清一个配装槽。
 *
 * <p>⚠️ <b>页号和配装都必须走这个包</b>：它们是<b>服务端权威</b>的
 * （存在 {@link MagicStoneData} 里、跟着存档走）—— 客户端自己记一份的话，
 * 重登/换维度/死亡之后就会和服务端对不上，表现是"按了没反应但界面显示有法术" ✗。
 */
public class MagicStoneActionPacket {

    public enum Action {
        /** 打开界面时拉一次最新数据（服务端权威，客户端只是镜像）。 */
        REQUEST_SYNC,
        /** 花魔法点数解锁一个法术。 */
        UNLOCK,
        /** 切到下一页热键（循环）。不需要额外参数。 */
        SET_PAGE,
        /** 把 spellId 放到第 value 个配装槽（0..17）。 */
        SET_SLOT,
        /** 清空第 value 个配装槽。 */
        CLEAR_SLOT
    }

    private Action action;
    private String spellId;
    /** 槽位下标 / 页号（按 action 解释）。 */
    private int value = -1;

    public MagicStoneActionPacket() {
    }

    public MagicStoneActionPacket(Action action, String spellId) {
        this(action, spellId, -1);
    }

    public MagicStoneActionPacket(Action action, String spellId, int value) {
        this.action = action;
        this.spellId = spellId == null ? "" : spellId;
        this.value = value;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(spellId);
        buf.writeVarInt(value);
    }

    public static MagicStoneActionPacket decode(FriendlyByteBuf buf) {
        return new MagicStoneActionPacket(buf.readEnum(Action.class), buf.readUtf(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            switch (action) {
                case REQUEST_SYNC -> {
                    com.tnc.tnc.adventure.AdventureService.milestone(player,"inspected");
                    MagicStoneNetwork.syncTo(player);
                }
                case UNLOCK -> handleUnlock(player);
                case SET_PAGE -> handleSetPage(player);
                case SET_SLOT -> handleSetSlot(player);
                case CLEAR_SLOT -> handleClearSlot(player);
            }
        });
        context.setPacketHandled(true);
    }

    /**
     * 切页 —— 只改"当前页"，然后把新一页的 9 个法术重写进法杖。
     *
     * <p>为什么切页要落到法杖上：引擎只有 9 个施法热键（`Keybindings.spell_hotbar_1..9`），
     * 加不了第 10 个 ✗。所以"两页"的实现方式是<b>同一批键位、换一批法术</b> ——
     * 施法/读条/冷却/动画/魔力闸门全部原样复用引擎那套 ✓。
     */
    private void handleSetPage(ServerPlayer player) {
        if (com.tnc.tnc.combat.DownedCombat.isDowned(player)) {
            return;
        }
        MagicStoneData data = MagicStone.getOrNull(player);
        if (data == null) {
            return;
        }
        // ★ 读条中不许切页：引擎的 SpellCast$Process 记着当前法术 id，
        //   把那个法术从法杖里拿掉之后读条会指向一个不存在的东西 ✗
        if (isCasting(player)) {
            player.displayClientMessage(Component.literal("§c[TN-C] 施法中不能切页"), true);
            return;
        }
        int page = data.cycleLoadoutPage();
        SpellEngineBridge.WandResult result = SpellEngineBridge.ensureWand(player, data);
        MagicStoneNetwork.syncTo(player);
        player.displayClientMessage(Component.literal("§b[TN-C] §r第 §e" + (page + 1) + "§r/"
                + MagicStoneData.PAGE_COUNT + " 页 §7（"
                + SpellEngineBridge.describeWand(result, data.loadoutCount()) + "）"), true);
    }

    /** 配键：把某个法术放进某个槽。 */
    private void handleSetSlot(ServerPlayer player) {
        if (com.tnc.tnc.combat.DownedCombat.isDowned(player)) {
            return;
        }
        MagicStoneData data = MagicStone.getOrNull(player);
        if (data == null) {
            return;
        }
        if (value < 0 || value >= MagicStoneData.LOADOUT_SLOTS) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(spellId);
        if (id == null) {
            return;
        }
        // ★ 只能配"学过 + 引擎认识"的法术（防呆闸门，见 SpellCatalog.canBind）
        if (!SpellCatalog.canBind(data, id)) {
            player.displayClientMessage(Component.literal("§c[TN-C] 这个法术还不能配到键位上"), true);
            return;
        }
        if (!data.setSlot(value, id)) {
            return;
        }
        SpellEngineBridge.ensureWand(player, data);
        MagicStoneNetwork.syncTo(player);
    }

    /** 清空某个槽。 */
    private void handleClearSlot(ServerPlayer player) {
        if (com.tnc.tnc.combat.DownedCombat.isDowned(player)) {
            return;
        }
        MagicStoneData data = MagicStone.getOrNull(player);
        if (data == null) {
            return;
        }
        if (!data.clearSlot(value)) {
            return;
        }
        SpellEngineBridge.ensureWand(player, data);
        MagicStoneNetwork.syncTo(player);
    }

    /** 玩家现在是不是在读条/引导（引擎侧判定）。 */
    private static boolean isCasting(ServerPlayer player) {
        try {
            var caster = (net.spell_engine.internals.casting.SpellCasterEntity) player;
            return caster.getSpellCastProcess() != null;
        } catch (Throwable ignored) {
            // 没装引擎时没有这条判定 —— 放行，不要因为判定失败就把切页卡死 ✗
            return false;
        }
    }

    private void handleUnlock(ServerPlayer player) {
        if(com.tnc.tnc.combat.DownedCombat.isDowned(player))return;
        ResourceLocation id = ResourceLocation.tryParse(spellId);
        if (id == null) {
            return;
        }
        SpellCatalog.Entry entry = SpellCatalog.byId(id);
        if (entry == null) {
            player.sendSystemMessage(Component.literal("§c[TN-C] 未知法术：" + id));
            return;
        }
        MagicStoneData data = MagicStone.getOrNull(player);
        if (data == null) {
            return;
        }
        MagicStoneLearning.Result result = MagicStoneLearning.unlock(data, entry);
        if(result==MagicStoneLearning.Result.OK) {
            com.tnc.tnc.magic.LearningVisuals.start(player,entry.element());
            com.tnc.tnc.adventure.AdventureService.milestone(player,"learned");
        }
        player.sendSystemMessage(MagicStoneLearning.describe(result, entry, data));
        if (result == MagicStoneLearning.Result.OK || result == MagicStoneLearning.Result.ALREADY_LEARNED) {
            // 解锁成功 = 把"当前这一页的配装"同步进法杖（玩家看不到卷轴/法术书/注册台）。
            // 魔法石是权威数据，法杖只是它的一个投影。
            // ⚠️ 走 ensureWand(player, data)：由它去问 SpellCatalog.wandSpellIds，
            //    这样"法杖上该有什么"只有一份口径（页号 + 配装），不会再被按等级排序的旧列表冲掉 ✗
            SpellEngineBridge.WandResult synced = SpellEngineBridge.ensureWand(player, data);
            player.sendSystemMessage(Component.literal("§7[TN-C] 法杖："
                    + SpellEngineBridge.describeWand(synced, data.loadoutCount())));
        }
        // 无论成功失败都同步一次：成功要刷新点数/已学，失败也能让界面显示最新状态
        MagicStoneNetwork.syncTo(player);
    }
}
