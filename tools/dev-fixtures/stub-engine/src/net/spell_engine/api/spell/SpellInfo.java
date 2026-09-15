package net.spell_engine.api.spell;

import net.minecraft.resources.ResourceLocation;

/**
 * 【开发用桩】对应真引擎的 {@code net.spell_engine.api.spell.SpellInfo}。
 * javap 实测是个 record：{@code (Spell spell, ResourceLocation id)}，带 {@code spell()} / {@code id()}。
 */
public record SpellInfo(Spell spell, ResourceLocation id) {
}
