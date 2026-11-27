package com.tnc.tnc.dungeon.mirror;

import com.tnc.tnc.combat.DownedCombat;
import com.tnc.tnc.adventure.ElementWands;
import com.tnc.tnc.dungeon.DungeonService;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.MagicStoneData;
import com.tnc.tnc.magic.SpellCatalog;
import com.tnc.tnc.magic.compat.SpellEngineBridge;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Independent loadout, mana, cast timing and cooldowns belong to this individual reflection. */
public final class MirrorSpells {
    private static final String STATE = "MirrorSpellbook", HISTORY = "TncReleasedSpells";
    private MirrorSpells() {}

    public static void record(ServerPlayer player, ResourceLocation spell) {
        if (player instanceof FakePlayer) return;
        var data = player.getPersistentData();
        ListTag recent = data.getList(HISTORY, Tag.TAG_STRING);
        while (recent.size() >= 32) recent.remove(0);
        recent.add(StringTag.valueOf(spell.toString()));
        data.put(HISTORY, recent);
    }

    static Set<ResourceLocation> equipped(MagicStoneData stone) {
        var ids = new LinkedHashSet<ResourceLocation>();
        if (stone != null) for (int i = 0; i < MagicStoneData.PAGE_COUNT; i++) {
            int page = (stone.getLoadoutPage() + i) % MagicStoneData.PAGE_COUNT;
            for (var id : stone.pageSpellIds(page)) if (id != null && stone.hasLearned(id)) ids.add(id);
        }
        return ids;
    }

    public static void snapshot(EchoDouble mirror, ServerPlayer owner) {
        MagicStoneData stone = MagicStone.getOrNull(owner);
        Set<ResourceLocation> ids = equipped(stone);
        CompoundTag state = new CompoundTag();
        if (SpellEngineBridge.enginePresent()) MirrorSpellEngine.readContainers(owner, ids, state);
        copyPageWands(owner, ids, state);
        var pool = new ListTag();
        for (var id : ids) pool.add(StringTag.valueOf(id.toString()));
        state.put("Pool", pool);
        state.put("Recent", owner.getPersistentData().getList(HISTORY, Tag.TAG_STRING).copy());
        int base = stone == null ? 100 : Math.max(1, stone.getMaxMana());
        int max = base;
        for(var id:ids) {
            var spell=SpellCatalog.byId(id);
            max=Math.max(max,spell==null?5:spell.manaCostFor(base));
        }
        state.putInt("CostBase",base);
        state.putInt("Max", max);
        state.putFloat("Mana", max);
        state.put("Cooldowns", new CompoundTag());
        if (stone != null) state.put("Stone", stone.serializeNBT());
        mirror.getPersistentData().put(STATE, state);
    }

    private static void copyPageWands(ServerPlayer owner, Set<ResourceLocation> ids, CompoundTag book) {
        // Wands may encode only the active page, or still be waiting for their periodic sync.
        // Carry the actual wand (including its material, name and enchantments) for either page.
        var inventory = owner.getInventory();
        var sources = book.getCompound("Source");
        var legacy = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(SpellEngineBridge.WAND);
        for (int n = 0; n < inventory.getContainerSize(); n++) {
            int slot = (inventory.selected + n) % inventory.getContainerSize();
            ItemStack wand = inventory.getItem(slot);
            if (wand.isEmpty() || !(wand.getItem() instanceof ElementWands.Wand || wand.is(legacy))
                    || com.tnc.tnc.world.yan.YanPrologueSpells.isWand(wand)) continue;
            for (var id : ids) {
                var spell = SpellCatalog.byId(id);
                if (spell == null || sources.contains(id.toString())
                        || wand.getItem() instanceof ElementWands.Wand typed && typed.design.tier() < spell.tier()) continue;
                var copy = wand.copyWithCount(1);
                SpellEngineBridge.writePrologueContainer(copy, List.of(id));
                sources.put(id.toString(), copy.save(new CompoundTag()));
            }
        }
        book.put("Source", sources);
    }

    public static void clear(EchoDouble mirror) { MirrorSpellHand.clear(mirror); }

    public static boolean enemy(LivingEntity caster, LivingEntity victim) {
        EchoDouble echo = body(caster);
        if (echo == null || !echo.isAlive() || echo.isRemoved() || echo.melee() || !(victim instanceof ServerPlayer player)
                || player.isSpectator() || player.isCreative() || !player.isAlive()
                || DownedCombat.isDowned(player) || DungeonService.recovering(player)) return false;
        var run = DungeonService.run(player);
        return run != null && run.id.equals(echo.runId()) && echo.level() == player.level()
                && player.blockPosition().closerThan(echo.center(), 30);
    }

    public static EchoDouble body(LivingEntity who) {
        return who instanceof EchoDouble echo ? echo : who instanceof MirrorSpellHand hand ? hand.body : null;
    }

    static record Cadence(int cast, int cooldown, int cost, double range, boolean defensive) {}

    private static Cadence cadence(ResourceLocation id, int max, EchoDouble mirror) {
        var entry = SpellCatalog.byId(id);
        boolean defensive = entry != null && (id.getPath().equals("mana_shield")
                || id.getPath().contains("blink") || entry.chain() == SpellCatalog.Chain.LIGHT_GRACE
                || entry.chain() == SpellCatalog.Chain.EARTH_RING || entry.chain() == SpellCatalog.Chain.SPEED);
        int cost = entry == null ? 5 : entry.manaCostFor(max);
        if (SpellEngineBridge.enginePresent()) return MirrorSpellEngine.cadence(id, mirror, cost, defensive);
        return new Cadence(15, 60, cost, 18, defensive);
    }

    public static void tick(EchoDouble mirror, ServerPlayer target) {
        var state = mirror.getPersistentData().getCompound(STATE);
        var level = (ServerLevel) mirror.level();
        var cooldowns = state.getCompound("Cooldowns");
        for (String id : List.copyOf(cooldowns.getAllKeys())) {
            int remaining = cooldowns.getInt(id) - 1;
            if (remaining <= 0) cooldowns.remove(id); else cooldowns.putInt(id, remaining);
        }
        int max = state.getInt("Max");
        int costBase=state.contains("CostBase")?state.getInt("CostBase"):max;
        float mana = state.getFloat("Mana");
        state.putFloat("Mana", Math.min(max, mana + max * .0125f / 20));
        MirrorSpellHand.advance(mirror);
        if (state.getInt("Recovery") > 0) {
            state.putInt("Recovery", state.getInt("Recovery") - 1);
            return;
        }
        if (state.contains("Casting")) {
            var id = ResourceLocation.tryParse(state.getString("Casting"));
            if (id == null) { state.remove("Casting"); return; }
            int left = state.getInt("Remaining");
            if (left > 5) setAim(state, target);
            Vec3 point = aim(state);
            if (id.getPath().contains("blink") && target.distanceTo(mirror) < 4)
                point = mirror.getEyePosition().scale(2).subtract(point);
            mirror.lookAt(Anchor.EYES, point);
            mirror.setYHeadRot(mirror.getYRot());
            if (SpellEngineBridge.enginePresent())
                MirrorSpellEngine.castingTick(mirror, target, id, state.getInt("Duration") - left + 1, state.getInt("Duration"));
            if (left % 4 == 0) {
                Vec3 tip = mirror.getEyePosition().add(mirror.getLookAngle().scale(.65));
                level.sendParticles(ParticleTypes.ENCHANT, tip.x, tip.y, tip.z, 4, .1, .1, .1, .08);
            }
            if (left > 1) { state.putInt("Remaining", left - 1); return; }
            Cadence spell = cadence(id, costBase, mirror);
            boolean hitAim = target.getBoundingBox().inflate(.45).contains(aim(state)) && mirror.hasLineOfSight(target);
            if (MirrorSpellHand.release(mirror, id, target, hitAim, state)) {
                state.putFloat("Mana", Math.max(0, state.getFloat("Mana") - spell.cost()));
                cooldowns.putInt(id.toString(), Math.max(2, spell.cooldown()));
                String last = state.getString("Last");
                state.putInt("Streak", last.equals(id.toString()) ? state.getInt("Streak") + 1 : 1);
                state.putString("Last", id.toString());
            } else cooldowns.putInt(id.toString(), 30);
            state.remove("Casting");
            mirror.action("release", 10);
            state.putInt("Recovery", 10);
            return;
        }
        double distance = mirror.distanceTo(target);
        if (mirror.tickCount % 5 != 0) return;
        var pool = state.getList("Pool", Tag.TAG_STRING);
        ResourceLocation choice = null;
        int best = Integer.MIN_VALUE;
        var recent = state.getList("Recent", Tag.TAG_STRING);
        for (int i = 0; i < pool.size(); i++) {
            String key = pool.getString(i);
            var id = ResourceLocation.tryParse(key);
            if (id == null || cooldowns.contains(key)) continue;
            if (SpellEngineBridge.enginePresent() && !MirrorSpellEngine.available(id)) continue;
            Cadence c = cadence(id, costBase, mirror);
            if (c.cost() > state.getFloat("Mana") || !c.defensive()
                    && (distance > c.range() || !mirror.hasLineOfSight(target))) continue;
            int frequency = 0;
            for (int j = 0; j < recent.size(); j++) if (key.equals(recent.getString(j))) frequency++;
            int priority = frequency * 2 + (c.defensive() ? distance < 3 ? 160 : -90 : 0);
            if (state.getString("Last").equals(key) && state.getInt("Streak") >= 2) priority -= 100;
            if (priority > best) { best = priority; choice = id; }
        }
        if (choice != null) {
            int ticks = Math.max(5, cadence(choice, costBase, mirror).cast());
            mirror.getNavigation().stop();
            state.putString("Casting", choice.toString());
            state.putInt("Remaining", ticks);
            state.putInt("Duration", ticks);
            setAim(state, target);
            mirror.action("cast", ticks);
            return;
        }
        if (pool.isEmpty() || distance < 3) {
            if (distance < 2.6 && mirror.hasLineOfSight(target)) {
                mirror.lookAt(Anchor.EYES, target.getEyePosition());
                mirror.swing(InteractionHand.MAIN_HAND);
                mirror.doHurtTarget(target);
                mirror.action("slash", 8);
                state.putInt("Recovery", 14);
            }
            if (!pool.isEmpty() && distance < 3) {
                Vec3 away = mirror.position().subtract(target.position()).multiply(1, 0, 1).normalize();
                Vec3 back = mirror.position().add(away.scale(3));
                if (back.distanceToSqr(Vec3.atCenterOf(mirror.center())) < 20*20)
                    mirror.getNavigation().moveTo(back.x, back.y, back.z, 1.12);
                if (!state.contains("Recovery") || state.getInt("Recovery") == 0) mirror.action("retreat", 8);
                return;
            }
        }
        if (distance > 2.1) mirror.getNavigation().moveTo(target, 1.0);
    }

    private static void setAim(CompoundTag tag, ServerPlayer target) {
        var point = target.getBoundingBox().getCenter();
        tag.putDouble("AimX", point.x); tag.putDouble("AimY", point.y); tag.putDouble("AimZ", point.z);
    }
    private static Vec3 aim(CompoundTag t) { return new Vec3(t.getDouble("AimX"), t.getDouble("AimY"), t.getDouble("AimZ")); }
}
