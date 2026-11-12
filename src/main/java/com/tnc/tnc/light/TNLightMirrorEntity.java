package com.tnc.tnc.light;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import java.util.UUID;

/** Physical, stationary floating prism; server-owned expiry and ownership. */
public final class TNLightMirrorEntity extends Entity {
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(TNLightMirrorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLASH = SynchedEntityData.defineId(TNLightMirrorEntity.class, EntityDataSerializers.INT);
    private UUID owner;
    private int remaining = LightMirrorRules.lifetime(1);
    public TNLightMirrorEntity(EntityType<? extends TNLightMirrorEntity> type, Level level) {
        super(type, level); setNoGravity(true); noPhysics = true;
    }
    public void configure(LivingEntity caster, int tier) {
        owner = caster.getUUID(); entityData.set(TIER, LightMirrorRules.tier(tier));
        remaining = LightMirrorRules.lifetime(tier);
    }
    public int tier() { return entityData.get(TIER); }
    public boolean flashing() { return entityData.get(FLASH) > 0; }
    public void flash() { entityData.set(FLASH, 6); }
    public boolean belongsTo(LivingEntity caster) { return caster.getUUID().equals(owner); }
    public Vec3 center() { return position().add(0, 1.3, 0); }
    public Vec3 axis() { return Vec3.directionFromRotation(0, getYRot()); }
    public AABB opticalBounds() { return new AABB(center().subtract(.9, 1.2, .9), center().add(.9, 1.2, .9)); }
    @Override protected void defineSynchedData() { entityData.define(TIER, 1); entityData.define(FLASH, 0); }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (flashing()) entityData.set(FLASH, entityData.get(FLASH) - 1);
            if (--remaining <= 0 || (owner != null && level() instanceof ServerLevel s
                    && s.getEntity(owner) instanceof LivingEntity living && !living.isAlive())) discard();
        }
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) owner = tag.getUUID("Owner");
        entityData.set(TIER, LightMirrorRules.tier(tag.getInt("Tier")));
        remaining = Math.max(1, Math.min(tag.getInt("Remaining"), LightMirrorRules.lifetime(tier())));
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putInt("Tier", tier()); tag.putInt("Remaining", remaining);
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public boolean isPickable() { return true; }
    /**
     * 准星"贴脸上就能选中"的半径 ✓（作者 2026-11-07："那个镜子要把它弄成可以瞄准的东西"）。
     * 光镜本身只有一张薄片（碰撞盒 = 1 格方块），原版拾取要正好戳到那 1 格才中 ✗；
     * 放到 1.1 格之后，"看着它按右键/左键"就够了 ✓ —— 真正的施法瞄准在
     * {@link LightMirrorAim} 里另有一套（视线锥 + 光路盒子 ✓）。
     */
    @Override public float getPickRadius() { return 1.1F; }
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (!level().isClientSide && !isInvulnerableTo(source) && amount > 0) { discard(); return true; }
        return false;
    }
    @Override public AABB getBoundingBoxForCulling() { return opticalBounds().inflate(.5); }
}
