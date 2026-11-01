package com.tnc.tnc.light;

import com.tnc.tnc.boss.TNApolloEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Support caster: visibly deploys prisms rather than duplicating the boss's damage spells. */
public final class ApolloAcolyteEntity extends Monster {
    private UUID master;
    private int slot;
    private int deployCooldown = 35;
    public ApolloAcolyteEntity(EntityType<? extends ApolloAcolyteEntity> type, Level level) { super(type, level); xpReward=8; }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 65)
                .add(Attributes.MOVEMENT_SPEED, .26).add(Attributes.ARMOR, 4).add(Attributes.FOLLOW_RANGE, 48);
    }
    @Override protected void registerGoals() { goalSelector.addGoal(0, new FloatGoal(this)); goalSelector.addGoal(8, new RandomLookAroundGoal(this)); }
    public void bind(TNApolloEntity boss, int slot) { this.master=boss.getUUID(); this.slot=slot; }
    public boolean belongsTo(TNApolloEntity boss) { return boss.getUUID().equals(master); }
    @Override public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server)) return;
        TNApolloEntity boss = master == null ? server.getEntitiesOfClass(TNApolloEntity.class, getBoundingBox().inflate(40), LivingEntity::isAlive)
                .stream().findFirst().orElse(null) : server.getEntity(master) instanceof TNApolloEntity b ? b : null;
        if (boss == null || !boss.isAlive()) { if (master != null) discard(); return; }
        if (master == null) bind(boss, Math.floorMod(getId(), 3));
        LivingEntity victim=boss.getTarget();
        if (victim == null || !victim.isAlive()) { getNavigation().moveTo(boss, .85); return; }
        getLookControl().setLookAt(victim, 30, 30);
        double angle = slot * Math.PI * 2 / 3 + server.getGameTime() * .003;
        Vec3 station = boss.position().add(Math.cos(angle)*6, 0, Math.sin(angle)*6);
        if (position().distanceToSqr(station) > 4) getNavigation().moveTo(station.x, station.y, station.z, 1);
        if (--deployCooldown > 0) return;
        deployCooldown = 130 + slot * 15;
        var mirrors=server.getEntitiesOfClass(TNLightMirrorEntity.class, boss.getBoundingBox().inflate(100), m -> m.belongsTo(boss));
        if (mirrors.size() >= 6 || !boss.hasLineOfSight(victim)) return;
        // Central prism intercepts the direct ray; flank prisms intercept the broad sky columns.
        Vec3 toward = victim.getEyePosition().subtract(boss.getEyePosition());
        Vec3 at = slot == 0 ? boss.getEyePosition().add(toward.scale(.45)).add(0, -1.3, 0)
                : victim.position().add(Math.cos(angle)*7, .2, Math.sin(angle)*7);
        if (!server.hasChunkAt(net.minecraft.core.BlockPos.containing(at))) return;
        var mirror=LightMirrorRegistration.MIRROR.create(server);
        if (mirror == null) return;
        int tier = boss.getHealth() < boss.getMaxHealth()*.5F ? 5 : 3;
        mirror.configure(boss, tier);
        Vec3 out=victim.getEyePosition().subtract(at.add(0, 1.3, 0));
        mirror.moveTo(at.x, at.y, at.z, TNLightBeamMechanics.yawFor(out), 0);
        if (server.getWorldBorder().isWithinBounds(mirror.getBoundingBox()) && server.noCollision(mirror, mirror.getBoundingBox())) {
            server.addFreshEntity(mirror); swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            server.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                    net.minecraft.sounds.SoundSource.HOSTILE, .9F, 1.25F);
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); if(master != null) tag.putUUID("Apollo", master); tag.putInt("MirrorSlot", slot);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); master=tag.hasUUID("Apollo") ? tag.getUUID("Apollo") : null; slot=Math.floorMod(tag.getInt("MirrorSlot"), 3);
    }
    public static int count(TNApolloEntity boss) {
        return boss.level().getEntitiesOfClass(ApolloAcolyteEntity.class, boss.getBoundingBox().inflate(80),
                a -> a.isAlive() && a.belongsTo(boss)).size();
    }
    public static int summonMissing(TNApolloEntity boss) {
        if (!(boss.level() instanceof ServerLevel server) || !boss.isAlive() || boss.getTarget()==null) return 0;
        int summoned=0;
        var followers=server.getEntitiesOfClass(ApolloAcolyteEntity.class, boss.getBoundingBox().inflate(80), a -> a.isAlive() && a.belongsTo(boss));
        for(int slot=0; slot<3; slot++) {
            final int wanted=slot;
            if(followers.stream().anyMatch(a -> a.slot==wanted)) continue;
            var follower=LightMirrorRegistration.ACOLYTE.create(server); if(follower==null) continue;
            double angle=slot*Math.PI*2/3;
            var at=boss.position().add(Math.cos(angle)*3, 0, Math.sin(angle)*3);
            follower.bind(boss, slot); follower.moveTo(at.x, at.y, at.z, boss.getYRot(), 0);
            if(server.getWorldBorder().isWithinBounds(follower.getBoundingBox()) && server.noCollision(follower)
                    && server.addFreshEntity(follower)) summoned++;
        }
        return summoned;
    }
}
