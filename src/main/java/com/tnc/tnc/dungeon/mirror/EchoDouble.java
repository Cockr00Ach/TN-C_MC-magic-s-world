package com.tnc.tnc.dungeon.mirror;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** A server-owned opponent. Its rendering subject and its own identity are intentionally separate. */
public final class EchoDouble extends PathfinderMob {
    private static final EntityDataAccessor<CompoundTag> LOOK = SynchedEntityData.defineId(EchoDouble.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Boolean> MELEE = SynchedEntityData.defineId(EchoDouble.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> ACTION = SynchedEntityData.defineId(EchoDouble.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(EchoDouble.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DURATION = SynchedEntityData.defineId(EchoDouble.class, EntityDataSerializers.INT);
    private UUID run, owner;
    private BlockPos origin = BlockPos.ZERO;
    private boolean reported;
    /** Client presentation state follows this entity's tracking lifetime, including world unload. */
    public transient Object renderSubject;

    public EchoDouble(EntityType<? extends EchoDouble> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, 0);
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(LOOK, new CompoundTag());
        entityData.define(MELEE, false);
        entityData.define(ACTION, "");
        entityData.define(START, 0L);
        entityData.define(DURATION, 0);
    }

    public void bind(ServerPlayer player, UUID encounter, BlockPos center) {
        run = encounter;
        owner = player.getUUID();
        origin = center.immutable();
        CompoundTag appearance = new CompoundTag();
        appearance.put("Profile", NbtUtils.writeGameProfile(new CompoundTag(), player.getGameProfile()));
        int layers = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) if (player.isModelPartShown(part)) layers |= part.getMask();
        appearance.putInt("Layers", layers);
        var dimensions = player.getDimensions(Pose.STANDING);
        appearance.putFloat("Width", dimensions.width / .6f);
        appearance.putFloat("Height", dimensions.height / 1.8f);
        appearance.put("Accessories", MirrorAccessories.snapshot(player));
        entityData.set(LOOK, appearance);
        setLeftHanded(player.getMainArm() == HumanoidArm.LEFT);
        setCustomName(Component.literal(player.getGameProfile().getName()));
        setCustomNameVisible(true);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setItemSlot(slot, player.getItemBySlot(slot).copy());
            setDropChance(slot, 0);
        }
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(Mth.clamp(player.getMaxHealth()*1.2f, 28, 56));
        setHealth(getMaxHealth());
        MirrorSpells.snapshot(this, player);
        action("rise", 20);
    }

    public void enterMelee() {
        entityData.set(MELEE, true);
        removeAllEffects();
        stopUsingItem();
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(40);
        setHealth(40);
        action("rise", 20);
    }
    public UUID ownerId() { return owner; }
    public UUID runId() { return run; }
    public BlockPos center() { return origin; }
    public boolean melee() { return entityData.get(MELEE); }
    public CompoundTag appearance() { return entityData.get(LOOK); }

    public void action(String name, int duration) {
        entityData.set(ACTION, name);
        entityData.set(START, level().getGameTime());
        entityData.set(DURATION, Math.max(0,duration));
    }
    public int actionAge() { return (int) Math.min(Integer.MAX_VALUE, Math.max(0, level().getGameTime()-entityData.get(START))); }
    public int actionDuration() { return entityData.get(DURATION); }
    public String actionName() { return actionAge() < actionDuration() ? entityData.get(ACTION) : ""; }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        var target = MirrorFight.target(this);
        setTarget(target);
        if (target == null || "rise".equals(actionName())) {
            getNavigation().stop();
            stopUsingItem();
            return;
        }
        if (melee()) MirrorMelee.tick(this,target);
        else MirrorSpells.tick(this,target);
    }

    @Override public boolean canAttackType(EntityType<?> type) { return type == EntityType.PLAYER; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override public boolean shouldDropExperience() { return false; }
    @Override protected void dropEquipment() {}
    @Override public void die(DamageSource source) {
        if (reported) return;
        super.die(source);
        if (!dead) return;
        reported = true;
        action("dissolve", melee() ? 12 : 32);
        if (!level().isClientSide && run != null) MirrorFight.fallen(this);
    }
    @Override protected void tickDeath() {
        deathTime++;
        if (!level().isClientSide && deathTime >= (melee() ? 12 : 32)) discard();
    }

    @Override public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        if (run != null) t.putUUID("MirrorRun",run);
        if (owner != null) t.putUUID("MirrorOwner",owner);
        t.putLong("MirrorCenter",origin.asLong());
        t.put("MirrorLook",appearance().copy());
        t.putBoolean("MirrorMelee",melee());
        t.putBoolean("MirrorReported",reported);
        t.putString("MirrorAction",entityData.get(ACTION));
        t.putLong("MirrorActionStart",entityData.get(START));
        t.putInt("MirrorDuration",actionDuration());
    }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        if(t.hasUUID("MirrorRun")) run=t.getUUID("MirrorRun");
        if(t.hasUUID("MirrorOwner")) owner=t.getUUID("MirrorOwner");
        origin=BlockPos.of(t.getLong("MirrorCenter"));
        entityData.set(LOOK,t.getCompound("MirrorLook").copy());
        entityData.set(MELEE,t.getBoolean("MirrorMelee"));
        reported=t.getBoolean("MirrorReported");
        entityData.set(ACTION,t.getString("MirrorAction"));
        entityData.set(START,t.getLong("MirrorActionStart"));
        entityData.set(DURATION,t.getInt("MirrorDuration"));
    }
}
