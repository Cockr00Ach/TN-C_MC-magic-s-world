package com.tnc.tnc.warrior.forge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** The only transferable copy of a workpiece carries all authoritative process state. */
public final class ForgeJobData {
    private ForgeJobData() {}
    public static final String KEY = "TncHandForge";
    public static CompoundTag save(ForgeSession s) {
        var t = new CompoundTag();
        t.putInt("Version", 1); t.putString("Pattern", s.pattern.name()); t.putString("Stage", s.stage.name());
        t.putInt("Heat", s.heat); t.putInt("Strokes", s.strokes); t.putInt("Corrections", s.corrections);
        t.putInt("Overheat", s.overheatTicks); t.putInt("Pumps", s.pumps); t.putInt("HeatingTicks", s.heatingTicks);
        t.putInt("GoodHeatTicks", s.goodHeatTicks); t.putInt("QuenchTicks", s.quenchTicks); t.putInt("Pose", s.pose);
        t.putInt("AssemblyTicks", s.assemblyTicks); t.putInt("ActiveTicks", s.activeTicks); t.putInt("LastStrike", s.lastStrike);
        t.putDouble("H", s.heatScore); t.putDouble("P", s.hammerScore); t.putDouble("S", s.shapeScore);
        t.putDouble("Q", s.quenchScore); t.putDouble("A", s.assemblyScore); t.putDouble("StrikeTotal", s.strikeTotal);
        for (int i = 0; i < 3; i++) t.putDouble("Deviation" + i, s.deviation[i]);
        t.putBoolean("Burned", s.burned); t.putBoolean("Cracked", s.cracked);
        t.putBoolean("Quenched", s.quenched); t.putBoolean("Slow", s.slow);
        return t;
    }

    public static ForgeSession load(CompoundTag t) {
        try {
            var s = new ForgeSession(ForgePattern.valueOf(t.getString("Pattern")));
            s.stage = ForgeSession.Stage.valueOf(t.getString("Stage"));
            s.heat = integer(t, "Heat", 0, 1000); s.strokes = integer(t, "Strokes", 0, 12);
            s.corrections = integer(t, "Corrections", 0, 2); s.overheatTicks = integer(t, "Overheat", 0, 100000);
            s.pumps = integer(t, "Pumps", 0, 2); s.heatingTicks = integer(t, "HeatingTicks", 0, 1000000);
            s.goodHeatTicks = integer(t, "GoodHeatTicks", 0, 1000000); s.quenchTicks = integer(t, "QuenchTicks", 0, 72000);
            s.pose = integer(t, "Pose", 0, 3); s.assemblyTicks = integer(t, "AssemblyTicks", 0, 100);
            s.activeTicks = integer(t, "ActiveTicks", 0, Integer.MAX_VALUE);
            s.lastStrike = Math.max(-1000, Math.min(s.activeTicks, t.getInt("LastStrike")));
            s.heatScore = ForgeQuality.clamp(t.getDouble("H")); s.hammerScore = ForgeQuality.clamp(t.getDouble("P"));
            s.shapeScore = ForgeQuality.clamp(t.getDouble("S")); s.quenchScore = ForgeQuality.clamp(t.getDouble("Q"));
            s.assemblyScore = ForgeQuality.clamp(t.getDouble("A"));
            double total = t.getDouble("StrikeTotal"); s.strikeTotal = Double.isFinite(total) ? Math.max(0, Math.min(1200, total)) : 0;
            for (int i = 0; i < 3; i++) s.deviation[i] = ForgeQuality.clamp(t.getDouble("Deviation" + i));
            s.burned = t.getBoolean("Burned"); s.cracked = t.getBoolean("Cracked");
            s.quenched = t.getBoolean("Quenched"); s.slow = t.getBoolean("Slow");
            return s;
        } catch (IllegalArgumentException e) { return null; }
    }
    private static int integer(CompoundTag t, String key, int low, int high) { return Math.max(low, Math.min(high, t.getInt(key))); }
    public static ForgeSession from(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(KEY, 10) ? load(stack.getTag().getCompound(KEY)) : null;
    }
}
