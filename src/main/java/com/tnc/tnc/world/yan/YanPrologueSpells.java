package com.tnc.tnc.world.yan;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.MagicStoneData;
import com.tnc.tnc.magic.SpellCatalog;
import com.tnc.tnc.magic.TnSpellMechanics;
import com.tnc.tnc.magic.compat.SpellEngineBridge;
import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class YanPrologueSpells {
    static final int MAX_MANA = 1000;
    private static final String WAND_MARK = "TncYanPrologueWand";
    static final List<ResourceLocation> SPELLS = List.of(
            "lightning_recharge", "molten_crush", "giant_gale", "wind_weapon_2",
            "abyss", "earth_rift", "divine_light", "summon_lord"
    ).stream().map(path -> ResourceLocation.fromNamespaceAndPath("tnc", path)).toList();

    static ItemStack wand() {
        var wand = new ItemStack(TNMod.WAND.get());
        wand.setHoverName(Component.literal("序幕法杖 · 吴归衡"));
        wand.getOrCreateTag().putBoolean(WAND_MARK, true);
        SpellEngineBridge.writePrologueContainer(wand, SPELLS);
        return wand;
    }

    public static boolean isWand(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(WAND_MARK);
    }

    public static boolean blocksCast(ServerPlayer player, ResourceLocation id) {
        if (!YanPrologue.canCast(player) || !isWand(player.getMainHandItem()) || !SPELLS.contains(id)) return true;
        var entry = SpellCatalog.byId(id);
        var stone = MagicStone.getOrNull(player);
        if (entry == null || stone == null) return true;
        if (stone.getMana() >= entry.manaCost()) return false;
        player.displayClientMessage(Component.literal("魔力不足（" + stone.getMana() + " / " + entry.manaCost() + "）"), true);
        return true;
    }

    public static void release(ServerPlayer player, MagicStoneData stone, SpellCatalog.Entry entry) {
        if (blocksCast(player, entry.id()) || !stone.spendMana(entry.manaCost())) return;
        MagicStone.markCast(player);
        TnSpellMechanics.onSpellCast(player, entry.id(), stone);
        MagicStoneNetwork.syncTo(player);
    }

    private YanPrologueSpells() {}
}
