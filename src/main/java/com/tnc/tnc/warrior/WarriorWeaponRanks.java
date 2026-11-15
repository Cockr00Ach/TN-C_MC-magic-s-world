package com.tnc.tnc.warrior;

import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Authored progression, ordered by branch and rank for the creative catalogue. */
public final class WarriorWeaponRanks {
    public enum Branch {
        LONGSWORD, GREATSWORD, HAMMER, SPEAR, HALBERD, SCYTHE, BOW;

        public String translationKey() {
            return "tooltip.tnc.weapon.branch." + name().toLowerCase(Locale.ROOT);
        }
    }

    public record Entry(String id, Branch branch, int rank) {}

    private static final List<Entry> CATALOG = List.of(
            new Entry("ashwood_jian", Branch.LONGSWORD, 1),
            new Entry("yue_bronze_jian", Branch.LONGSWORD, 1),
            new Entry("ring_pommel_dao", Branch.LONGSWORD, 2),
            new Entry("han_iron_jian", Branch.LONGSWORD, 2),
            new Entry("willow_leaf_dao", Branch.LONGSWORD, 3),
            new Entry("star_rapier", Branch.LONGSWORD, 3),
            new Entry("frost_katana", Branch.LONGSWORD, 4),
            new Entry("cloudridge_jian", Branch.LONGSWORD, 4),
            new Entry("dragon_sword", Branch.LONGSWORD, 5),
            new Entry("bronze_greatsword", Branch.GREATSWORD, 1),
            new Entry("greatsword", Branch.GREATSWORD, 2),
            new Entry("monk_spade", Branch.GREATSWORD, 3),
            new Entry("crescent_guandao", Branch.GREATSWORD, 4),
            new Entry("crimson_warblade", Branch.GREATSWORD, 5),
            new Entry("ironwood_mallet", Branch.HAMMER, 1),
            new Entry("hammer", Branch.HAMMER, 2),
            new Entry("iron_jian_mace", Branch.HAMMER, 2),
            new Entry("earthshaker_hammer", Branch.HAMMER, 3),
            new Entry("ring_flange_hammer", Branch.HAMMER, 4),
            new Entry("thunder_bronze_hammer", Branch.HAMMER, 5),
            new Entry("militia_spear", Branch.SPEAR, 1),
            new Entry("qimei_staff", Branch.SPEAR, 1),
            new Entry("bronze_spear", Branch.SPEAR, 2),
            new Entry("red_tassel_spear", Branch.SPEAR, 3),
            new Entry("spear", Branch.SPEAR, 4),
            new Entry("silver_hook_spear", Branch.SPEAR, 4),
            new Entry("coiled_dragon_spear", Branch.SPEAR, 5),
            new Entry("bronze_ge_halberd", Branch.HALBERD, 1),
            new Entry("single_crescent_halberd", Branch.HALBERD, 2),
            new Entry("fangtian_ji", Branch.HALBERD, 3),
            new Entry("black_iron_halberd", Branch.HALBERD, 4),
            new Entry("sky_halberd", Branch.HALBERD, 5),
            new Entry("field_scythe", Branch.SCYTHE, 1),
            new Entry("iron_crescent_scythe", Branch.SCYTHE, 2),
            new Entry("doom_scythe", Branch.SCYTHE, 3),
            new Entry("white_bone_scythe", Branch.SCYTHE, 4),
            new Entry("eclipse_scythe", Branch.SCYTHE, 5),
            new Entry("bow", Branch.BOW, 1),
            new Entry("storm_bow", Branch.BOW, 2),
            new Entry("moon_bow", Branch.BOW, 3),
            new Entry("laminated_horn_bow", Branch.BOW, 4),
            new Entry("ember_bow", Branch.BOW, 5));

    private static final java.util.Map<String, Entry> BY_ID = CATALOG.stream().collect(
            java.util.stream.Collectors.toUnmodifiableMap(Entry::id, entry -> entry));

    private WarriorWeaponRanks() {}

    public static List<Entry> catalogue() {
        return CATALOG;
    }

    public static Entry entry(String id) {
        return BY_ID.get(id);
    }

    public static int comboCount(int rank) {
        return Math.max(1, Math.min(5, rank)) + 2;
    }

    public static boolean allowsUltimate(int rank, int points) {
        return rank >= 3 && points > 3000;
    }

    public static int rank(ItemStack stack) {
        return stack == null || stack.isEmpty() ? 5 : rank(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    public static int rank(ResourceLocation id) {
        if (id == null) return 5;
        if (id.getNamespace().equals("tnc")) {
            Entry entry = BY_ID.get(id.getPath());
            if (entry != null) return entry.rank();
            return switch (id.getPath()) {
                case "bronze_dao", "bronze_dagger", "bronze_halberd" -> 2;
                case "wind_relic_sword", "wind_relic_spear" -> 4;
                default -> 5;
            };
        }
        if (id.getNamespace().equals("minecraft")) {
            return switch (id.getPath()) {
                case "wooden_sword", "stone_sword", "bow" -> 1;
                case "golden_sword", "crossbow" -> 2;
                case "iron_sword" -> 3;
                case "diamond_sword" -> 4;
                default -> 5;
            };
        }
        return 5;
    }

    public static Branch branch(ItemStack stack) {
        WarriorWeaponFamily family = WarriorWeapons.family(stack);
        if (family == null) return null;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null && id.getNamespace().equals("tnc")) {
            Entry entry = BY_ID.get(id.getPath());
            if (entry != null) return entry.branch();
            if (id.getPath().equals("bronze_halberd")) return Branch.HALBERD;
        }
        return Branch.valueOf(family.name());
    }
}
