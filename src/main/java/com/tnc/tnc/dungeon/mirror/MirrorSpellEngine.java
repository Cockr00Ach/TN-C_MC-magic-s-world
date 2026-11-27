package com.tnc.tnc.dungeon.mirror;

import com.tnc.tnc.adventure.ElementWands;
import com.tnc.tnc.magic.SpellCatalog;
import com.tnc.tnc.magic.compat.SpellEngineBridge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellContainerHelper;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCasterEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Loaded only when the installed pack supplies Spell Engine. */
final class MirrorSpellEngine {
    private MirrorSpellEngine() {}
    static boolean available(ResourceLocation id) { return SpellRegistry.getSpell(id) != null; }

    static void readContainers(ServerPlayer original, Set<ResourceLocation> ids, CompoundTag data) {
        var sources = new CompoundTag();
        var supplies = new ListTag();
        // Scan the current hand first so duplicate spell containers have stable precedence.
        var inventory = original.getInventory();
        List<Integer> order = new ArrayList<>();
        order.add(inventory.selected);
        for (int i = 0; i < inventory.getContainerSize(); i++) if (i != inventory.selected) order.add(i);
        for (int i : order) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            var item = ForgeRegistries.ITEMS.getKey(stack.getItem());
            boolean mirrorOfStone = SpellEngineBridge.WAND.equals(item) || stack.getItem() instanceof ElementWands.Wand;
            if (mirrorOfStone && com.tnc.tnc.world.yan.YanPrologueSpells.isWand(stack)) continue;
            if (!SpellContainerHelper.hasUsableContainer(stack)) continue;
            var container = SpellContainerHelper.containerFromItemStack(stack);
            if (container == null || container.spell_ids == null) continue;
            for (String key : container.spell_ids) {
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id == null || !available(id) || sources.contains(key)) continue;
                if (mirrorOfStone || SpellCatalog.byId(id) != null) {
                    if (!ids.contains(id)) continue;
                } else ids.add(id);
                sources.put(key, stack.copyWithCount(1).save(new CompoundTag()));
            }
        }
        Set<String> ammunition = new java.util.HashSet<>();
        for (var id : ids) {
            Spell spell = SpellRegistry.getSpell(id);
            if (spell != null && spell.cost != null && spell.cost.item_id != null)
                ammunition.add(spell.cost.item_id);
        }
        for (int i=0; i<36 && supplies.size()<35; i++) {
            ItemStack stack=inventory.getItem(i);
            var item=ForgeRegistries.ITEMS.getKey(stack.getItem());
            if(stack.isEmpty() || item==null || !ammunition.contains(item.toString())) continue;
            var saved=new CompoundTag();
            saved.putInt("Index",supplies.size()+1);
            saved.put("Item",stack.save(new CompoundTag())); supplies.add(saved);
        }
        data.put("Source", sources);
        data.put("Supplies", supplies);
        var power = new CompoundTag();
        for (var attr : ForgeRegistries.ATTRIBUTES.getEntries()) {
            if (!attr.getKey().location().getNamespace().equals("spell_power")) continue;
            if (original.getAttribute(attr.getValue()) != null)
                power.putDouble(attr.getKey().location().toString(), original.getAttributeValue(attr.getValue()));
        }
        data.put("Power", power);
    }

    static MirrorSpells.Cadence cadence(ResourceLocation id, EchoDouble mob, int cost, boolean defensive) {
        var spell = SpellRegistry.getSpell(id);
        if (spell == null) return new MirrorSpells.Cadence(20, 40, cost, 0, defensive);
        var hand = MirrorSpellHand.hand(mob);
        int cast = (int)Math.ceil(20 * SpellHelper.getCastDuration(hand, spell));
        int cooldown = (int)Math.ceil(20 * SpellHelper.getCooldownDuration(hand, spell));
        // Many TN-C spells dispatch a custom Java attack via a SELF marker with JSON range 0.
        double range = spell.range > 0 ? spell.range : 18;
        return new MirrorSpells.Cadence(cast, cooldown, cost, range, defensive);
    }

    static void release(MirrorSpellHand hand, ResourceLocation id, ServerPlayer target, boolean aimed) {
        Spell spell = SpellRegistry.getSpell(id);
        if (spell == null) return;
        ((SpellCasterEntity) (Object) hand).getCooldownManager().remove(id);
        List<Entity> targets;
        if (spell.release.target.type == Spell.Release.Target.Type.AREA) {
            targets = new ArrayList<>(hand.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                    hand.getBoundingBox().inflate(spell.range), p -> MirrorSpells.enemy(hand, p)
                            && p.distanceToSqr(hand) <= spell.range * spell.range && hand.hasLineOfSight(p)));
        } else targets = aimed ? List.of(target) : List.of();
        if (SpellHelper.isChanneled(spell)) {
            // Channel pulses are delivered by castingTick at their original intervals.
            if (spell.cast.channel_ticks <= 0) return;
        }
        SpellHelper.performSpell(hand.level(),hand,id,targets,SpellCast.Action.RELEASE,1);
    }

    static void castingTick(EchoDouble mob, ServerPlayer target, ResourceLocation id, int age, int length) {
        Spell spell = SpellRegistry.getSpell(id);
        if (spell == null || !SpellHelper.isChanneled(spell) || age <= 0
                || spell.cast.channel_ticks <= 0 || age % spell.cast.channel_ticks != 0 || !mob.hasLineOfSight(target)) return;
        var hand = MirrorSpellHand.hand(mob);
        if (!hand.prepareMedium(id, mob.getPersistentData().getCompound("MirrorSpellbook"))) return;
        hand.moveTo(mob.getX(),mob.getY(),mob.getZ(),mob.getYRot(),mob.getXRot());
        MirrorSpellHand.CASTING.set(hand);
        try {
            SpellHelper.performSpell(mob.level(),hand,id,List.of(target),SpellCast.Action.CHANNEL,
                    Math.min(1F,age/(float)Math.max(1,length)));
        } finally { MirrorSpellHand.CASTING.remove(); }
    }
}
