package com.tnc.tnc.dungeon.mirror;

import com.mojang.authlib.GameProfile;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.SpellCatalog;
import com.tnc.tnc.magic.TnSpellMechanics;
import com.tnc.tnc.magic.compat.SpellEngineBridge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Some authored spells require a ServerPlayer. Their detached casting hand shares the mob's
 * identity and attributes, never a participant's inventory, advancement data or connection.
 * World lookups by entity UUID still find the combat mob. The hand never joins a level.
 */
public final class MirrorSpellHand extends FakePlayer {
    private static final Map<UUID, MirrorSpellHand> ACTIVE = new HashMap<>();
    static final ThreadLocal<MirrorSpellHand> CASTING = new ThreadLocal<>();
    final EchoDouble body;
    boolean delivered;
    private ResourceLocation preparedSpell;

    private MirrorSpellHand(EchoDouble mirror) {
        super((ServerLevel)mirror.level(), new GameProfile(mirror.getUUID(), "MirrorCaster"));
        body = mirror;
        setId(mirror.getId());
        var book = mirror.getPersistentData().getCompound("MirrorSpellbook");
        var stone = MagicStone.get(this).orElse(null);
        if (stone != null && book.contains("Stone")) stone.deserializeNBT(book.getCompound("Stone"));
        for (var slot : EquipmentSlot.values()) setItemSlot(slot, mirror.getItemBySlot(slot).copy());
        var inventory = book.getList("Supplies", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < inventory.size(); i++) {
            var saved = inventory.getCompound(i);
            getInventory().setItem(saved.getInt("Index"), ItemStack.of(saved.getCompound("Item")));
        }
        var attrs = book.getCompound("Power");
        for (String key : attrs.getAllKeys()) {
            var attr = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.tryParse(key));
            if (attr != null && getAttribute(attr) != null) getAttribute(attr).setBaseValue(attrs.getDouble(key));
        }
        align();
    }

    static MirrorSpellHand hand(EchoDouble body) {
        return ACTIVE.computeIfAbsent(body.getUUID(), ignored -> new MirrorSpellHand(body));
    }
    static void clear(EchoDouble body) {
        if (ACTIVE.remove(body.getUUID()) != null) {
            TnSpellMechanics.clearMirror(body.getUUID());
            com.tnc.tnc.magic.domain.DomainService.cancelPending(body.getUUID());
        }
    }
    static void stop() {
        ACTIVE.keySet().forEach(TnSpellMechanics::clearMirror); ACTIVE.clear(); CASTING.remove();
    }
    private void align() {
        moveTo(body.getX(), body.getY(), body.getZ(), body.getYRot(), body.getXRot());
        setYHeadRot(body.getYHeadRot());
        setDeltaMovement(body.getDeltaMovement());
        tickCount = body.tickCount;
    }

    /** Delayed spells that require a player-shaped owner can recover the same transient hand. */
    @Nullable
    public static ServerPlayer owner(ServerLevel level, UUID id) {
        if (id == null) return null;
        var player = level.getServer().getPlayerList().getPlayer(id);
        if (player != null) return player;
        if (level.getEntity(id) instanceof EchoDouble echo && !echo.melee() && echo.isAlive()) return hand(echo);
        return null;
    }

    @Nullable
    public static Player player(net.minecraft.world.level.Level level, UUID id) {
        Player found = level.getPlayerByUUID(id);
        if (found != null || !(level instanceof ServerLevel server)) return found;
        return server.getEntity(id) instanceof EchoDouble echo && !echo.melee() && echo.isAlive() ? hand(echo) : null;
    }

    static void advance(EchoDouble body) {
        MirrorSpellHand p = ACTIVE.get(body.getUUID());
        if (p == null) return;
        p.align();
        CASTING.set(p);
        try {
            TnSpellMechanics.tickPlayer(p);
            com.tnc.tnc.magic.domain.DomainService.tickDomain(p);
            com.tnc.tnc.magic.TNWindMechanics.tickSpirits(p);
        } finally { CASTING.remove(); }
    }

    static boolean release(EchoDouble body, ResourceLocation spell, ServerPlayer target,
                           boolean onAim, CompoundTag book) {
        MirrorSpellHand hand = hand(body);
        if (!hand.prepareMedium(spell, book)) return false;
        hand.align();
        var stone = MagicStone.get(hand).orElse(null);
        if (stone == null) return false;
        stone.setMaxMana(book.getInt("Max"));
        stone.setMana((int)book.getFloat("Mana"));
        int initialMana=stone.getMana();
        Vec3 before = hand.position();
        hand.delivered = false;
        CASTING.set(hand);
        try {
            if (SpellEngineBridge.enginePresent()) MirrorSpellEngine.release(hand, spell, target, onAim);
            else if (SpellCatalog.byId(spell) != null) hand.authored(spell);
            book.putFloat("Mana",Math.max(0,Math.min(book.getInt("Max"),book.getFloat("Mana")+stone.getMana()-initialMana)));
            if (hand.position().distanceToSqr(before) > .05 && hand.position().distanceToSqr(Vec3.atCenterOf(body.center())) < 21 * 21
                    && body.level().noCollision(body, body.getBoundingBox().move(hand.position().subtract(before)))) {
                body.teleportTo(hand.getX(),hand.getY(),hand.getZ());
            }
            // Persist the casting hand's ammunition, including partial stacks, across a save/reload.
            var supplies = book.getList("Supplies", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i=0;i<supplies.size();i++) {
                var slot = supplies.getCompound(i);
                slot.put("Item",hand.getInventory().getItem(slot.getInt("Index")).save(new CompoundTag()));
            }
        } finally { CASTING.remove(); }
        return hand.delivered;
    }

    /**
     * Engine callbacks, including channel pulses, read the main hand. An equipped
     * stone spell remains playable when its owner carried no wand at the snapshot.
     * The focus here belongs only to this detached hand, never to the mob's visible
     * equipment, the participant or loot. Old saves may have no Source entry.
     */
    boolean prepareMedium(ResourceLocation spell, CompoundTag book) {
        if (!book.getList("Pool", net.minecraft.nbt.Tag.TAG_STRING)
                .contains(net.minecraft.nbt.StringTag.valueOf(spell.toString()))) return false;
        if (spell.equals(preparedSpell) && !getMainHandItem().isEmpty()) return true;
        var sources = book.getCompound("Source");
        var medium = sources.contains(spell.toString())
                ? ItemStack.of(sources.getCompound(spell.toString())) : ItemStack.EMPTY;
        if (medium.isEmpty()) {
            if (SpellCatalog.byId(spell) == null) return false; // External spells need their actual source.
            var wand = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(SpellEngineBridge.WAND);
            if (wand == null) return false;
            medium = new ItemStack(wand);
            SpellEngineBridge.writePrologueContainer(medium, java.util.List.of(spell));
        }
        setItemSlot(EquipmentSlot.MAINHAND, medium);
        preparedSpell = spell;
        return true;
    }

    public void authored(ResourceLocation spell) {
        delivered = true;
        if (SpellCatalog.byId(spell) != null) {
            var stone = MagicStone.get(this).orElse(null);
            if (stone != null) TnSpellMechanics.onSpellCast(this, spell, stone);
        }
    }

    public boolean permits(ResourceLocation id) {
        if (CASTING.get() != this || body.melee() || !body.isAlive() || id == null) return false;
        var pool = body.getPersistentData().getCompound("MirrorSpellbook")
                .getList("Pool", net.minecraft.nbt.Tag.TAG_STRING);
        return pool.contains(net.minecraft.nbt.StringTag.valueOf(id.toString()));
    }

    @Override public boolean canHarmPlayer(Player player) { return body != null && MirrorSpells.enemy(body, player); }
    @Override public CompoundTag getPersistentData() { return body == null ? super.getPersistentData() : body.getPersistentData(); }
    @Override public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        return body == null ? super.addEffect(effect, source) : body.addEffect(effect, source == this ? body : source);
    }
    @Override public boolean hasEffect(MobEffect effect) { return body == null ? super.hasEffect(effect) : body.hasEffect(effect); }
    @Override public MobEffectInstance getEffect(MobEffect effect) { return body == null ? super.getEffect(effect) : body.getEffect(effect); }
    @Override public boolean removeEffect(MobEffect effect) { return body == null ? super.removeEffect(effect) : body.removeEffect(effect); }
    @Override public void heal(float amount) { if (body == null) super.heal(amount); else body.heal(amount); }
    @Override public boolean hurt(DamageSource source, float amount) { return body != null && body.hurt(source, amount); }
    @Override public float getHealth() { return body == null ? super.getHealth() : body.getHealth(); }
    @Override public void setHealth(float health) { if (body == null) super.setHealth(health); else body.setHealth(health); }
}
