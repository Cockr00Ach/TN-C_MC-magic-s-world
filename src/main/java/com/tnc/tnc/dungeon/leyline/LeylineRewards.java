package com.tnc.tnc.dungeon.leyline;

import com.tnc.tnc.adventure.InventoryTransaction;
import com.tnc.tnc.alchemy.*;
import com.tnc.tnc.arcana.ArcanaContent;
import com.tnc.tnc.dungeon.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import java.util.*;

/** Entitlements live in the saved player ledger, independent of loaded rooms and inventory space. */
public final class LeylineRewards {
    public static List<ItemStack> loot(DungeonData.Run run, LeylineKind kind, boolean rare) {
        var out = new ArrayList<ItemStack>();
        out.add(new ItemStack(DungeonRewards.material(run.theme), 2 + run.floor / 5));
        switch (kind) {
            case BRIDGE, DUAL_BRIDGE, GRAVITY, RAPIDS -> out.add(elixir(Elixir.FEATHER, 2));
            case FALSE_VAULT, LENS -> out.add(item("shadow_mana_crystal", 3));
            case TIDE, MUSIC -> out.add(item("prism_crown_seed", 1));
            case ECHO, SUNDIAL -> out.add(elixir(Elixir.ECHO, 2));
            case FACTIONS -> out.add(item("capture_shrink_elixir", 2));
            case STOMACH -> out.add(item("pure_medicinal_powder", 4));
            case VINES -> out.add(item("shadow_hour_orchid_seed", 1));
            case PUPPETS, REFRACTION, CHESS -> out.add(item("botanical_mana_filament", 4));
            case MEAL -> { out.add(item("slime_pudding", 2)); out.add(item("moon_honey_cake", 2)); }
            case POLLUTION -> out.add(item("mana_ash", 6));
            case SCALE -> out.add(item("astral_wand_core", 1));
            case CAULDRON -> { var potion = elixir(Elixir.FOCUS, 5); potion.getOrCreateTag().putInt("RitualQuality", 2); out.add(potion); }
            case EXHIBITION -> out.add(item("shadow_mana_crystal", rare ? 5 : 2));
            case PAWN -> out.add(rare ? item("void_parasitic_seed", 1) : elixir(Elixir.CLEANSE, 2));
            default -> { } // Ancient alchemy dispenses its actual brewed bottles from the pot.
        }
        return out;
    }
    private static ItemStack item(String id, int count) { return new ItemStack(ArcanaContent.item(id), count); }
    private static ItemStack elixir(Elixir kind, int count) { return new ItemStack(AlchemyContent.POTIONS.get(kind), count); }

    public static void award(MinecraftServer server, DungeonData.Run run, DungeonGraph.Room room, UUID id, boolean rare) {
        var data = DungeonData.get(server);
        var state = LeylineService.state(run, room);
        String claim = "Paid_" + id;
        if (state.getBoolean(claim)) return;
        var profile = data.profile(id);
        var mail = profile.getList("LeylineMail", Tag.TAG_COMPOUND);
        for (var stack : loot(run, LeylineService.kind(run, room), rare)) {
            if (stack.isEmpty()) throw new IllegalStateException("Missing reward for " + LeylineService.kind(run, room));
            mail.add(stack.save(new CompoundTag()));
        }
        profile.put("LeylineMail", mail);
        profile.putBoolean("LeylineSolved_"+LeylineService.kind(run,room).name(),true);
        state.putBoolean(claim, true);
        data.setDirty();
        var player = server.getPlayerList().getPlayer(id);
        if (player != null) {
            deliver(player);
            DungeonService.message(player, LeylineService.kind(run, room).title + " · 奇遇已完成，奖励已记录");
        }
    }
    public static void deliver(ServerPlayer player) {
        var data = DungeonData.get(player.server);
        var profile = data.profile(player.getUUID());
        for(var kind:LeylineKind.values())if(profile.getBoolean("LeylineSolved_"+kind.name())){
            var advancement=player.server.getAdvancements().getAdvancement(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","leyline/"+kind.name().toLowerCase(java.util.Locale.ROOT)));
            if(advancement!=null)player.getAdvancements().award(advancement,"solved");
        }
        var mail = profile.getList("LeylineMail", Tag.TAG_COMPOUND);
        while (!mail.isEmpty()) {
            var stack = ItemStack.of(mail.getCompound(0));
            if (stack.isEmpty()) break; // Preserve unknown items for a future corrected registry.
            var tx = new InventoryTransaction(player.getInventory());
            if (!tx.add(stack)) break;
            tx.commit(); mail.remove(0); data.setDirty();
        }
        profile.put("LeylineMail", mail);
    }
    private LeylineRewards() {}
}
