package com.tnc.tnc.life.fauna;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.home.HousingService;
import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.UUID;

/** An original living sheep species with its own birth, model, fleece and rhythm. */
public final class BellwoolSheepEntity extends Animal {
    private static final EntityDataAccessor<Boolean> SHEARED =
            SynchedEntityData.defineId(BellwoolSheepEntity.class, EntityDataSerializers.BOOLEAN);
    private static final long HARVEST_INTERVAL = 48_000L;
    private static final long BREED_INTERVAL = 24_000L;
    private EatBlockGoal eatGoal;
    private int eatingTicks;
    private int nuzzleTicks;
    private long nextHarvestTick;
    private long nextBreedTick;
    private long lastActiveTick;
    @Nullable private UUID caretaker;

    public BellwoolSheepEntity(EntityType<? extends BellwoolSheepEntity> type,
                               net.minecraft.world.level.Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D);
    }

    @Override protected void registerGoals() {
        eatGoal = new EatBlockGoal(this);
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.25D));
        goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
        goalSelector.addGoal(3, new TemptGoal(this, 1.1D,
                Ingredient.of(TNMod.BELLWOOL_FODDER.get()), false));
        goalSelector.addGoal(4, new FollowParentGoal(this, 1.1D));
        goalSelector.addGoal(5, eatGoal);
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SHEARED, false);
    }

    public boolean isSheared() { return entityData.get(SHEARED); }
    private void setSheared(boolean value) { entityData.set(SHEARED, value); }
    @Nullable public UUID caretaker() { return caretaker; }
    long nextHarvestTick() { return nextHarvestTick; }

    @Override protected void customServerAiStep() {
        eatingTicks = eatGoal.getEatAnimationTick();
        super.customServerAiStep();
    }

    @Override public void aiStep() {
        if (level().isClientSide) {
            eatingTicks = Math.max(0, eatingTicks - 1);
            nuzzleTicks = Math.max(0, nuzzleTicks - 1);
        }
        super.aiStep();
        if (!level().isClientSide && isSheared() && level().getGameTime() >= nextHarvestTick)
            setSheared(false);
        if (!level().isClientSide) lastActiveTick = level().getGameTime();
        if (!level().isClientSide && !isBaby() && tickCount % 220 == 0 && random.nextInt(3) == 0)
            level().playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_BELL.get(),
                    SoundSource.NEUTRAL, 0.25F, 1.35F);
    }

    @Override public void handleEntityEvent(byte event) {
        if (event == 10) eatingTicks = 40;
        else if (event == 11) nuzzleTicks = 30;
        else super.handleEntityEvent(event);
    }

    public float grazingPitch(float partialTick) {
        if (eatingTicks <= 0) return 0;
        return 0.55F + 0.14F * net.minecraft.util.Mth.sin((40 - eatingTicks + partialTick) * 0.5F);
    }

    public float nuzzlePitch(float partialTick) {
        return nuzzleTicks <= 0 ? 0.0F :
                -0.24F * net.minecraft.util.Mth.sin((30 - nuzzleTicks + partialTick) * 0.17F);
    }

    @Override public void ate() {
        super.ate();
        if (isSheared() && level().getGameTime() >= nextHarvestTick) setSheared(false);
    }

    @Override public boolean isFood(ItemStack stack) {
        return stack.is(TNMod.BELLWOOL_FODDER.get());
    }

    private boolean mayCareFor(ServerPlayer player) {
        if (TownProtection.denied(player, blockPosition())) return false;
        return caretaker == null || caretaker.equals(player.getUUID()) || player.isCreative()
                || HousingService.mayDecorate(player, blockPosition());
    }

    private boolean breedingSpace() {
        if (!(level() instanceof ServerLevel server)) return true;
        int cx = blockPosition().getX() >> 4;
        int cz = blockPosition().getZ() >> 4;
        AABB area = new AABB(cx << 4, server.getMinBuildHeight(), cz << 4,
                (cx << 4) + 16, server.getMaxBuildHeight(), (cz << 4) + 16);
        return server.getEntitiesOfClass(BellwoolSheepEntity.class, area).size() < 8
                && server.getEntitiesOfClass(Animal.class, area).size() < 24;
    }

    @Override public boolean canFallInLove() {
        return super.canFallInLove() && !isBaby()
                && level().getGameTime() >= nextBreedTick && breedingSpace();
    }

    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer
                && (stack.is(Items.SHEARS) || isFood(stack)) && !mayCareFor(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.literal("这只响铃羊有照料者，或当前地块禁止操作。"), true);
            return InteractionResult.FAIL;
        }
        if (stack.is(Items.SHEARS)) {
            if (level().isClientSide) return InteractionResult.SUCCESS;
            if (!(player instanceof ServerPlayer serverPlayer) || !harvest(serverPlayer)) {
                player.displayClientMessage(Component.literal("响绒还没长好；它每两游戏日可剪一次。"), true);
                return InteractionResult.CONSUME;
            }
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            return InteractionResult.SUCCESS;
        }
        if (isFood(stack)) {
            if (isBaby()) {
                if (!level().isClientSide)
                    player.displayClientMessage(Component.literal("幼羊要自然长大约两游戏日，饲捆不能催熟。"), true);
                return InteractionResult.CONSUME;
            }
            if (!canFallInLove()) {
                if (!level().isClientSide)
                    player.displayClientMessage(Component.literal("一天繁殖间隔未过，或这一片牧场已满。"), true);
                return InteractionResult.CONSUME;
            }
            InteractionResult result = super.mobInteract(player, hand);
            if (!level().isClientSide && result.consumesAction() && caretaker == null)
                caretaker = player.getUUID();
            return result;
        }
        if (stack.isEmpty() && !isBaby()) {
            if (!level().isClientSide) {
                level().broadcastEntityEvent(this, (byte) 11);
                level().playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_BELL.get(),
                        SoundSource.NEUTRAL, 0.3F, 1.65F);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    public boolean readyForHarvest() {
        return isAlive() && !isBaby() && !isSheared()
                && level().getGameTime() >= nextHarvestTick;
    }

    /** Atomic living harvest; repeated shear or relog cannot duplicate two fleeces. */
    boolean harvest(ServerPlayer player) {
        if (!readyForHarvest() || !mayCareFor(player)) return false;
        long now = level().getGameTime();
        setSheared(true);
        nextHarvestTick = now + HARVEST_INTERVAL;
        lastActiveTick = now;
        level().playSound(null, blockPosition(), SoundEvents.SHEEP_SHEAR,
                SoundSource.PLAYERS, 0.85F, 1.13F);
        spawnAtLocation(new ItemStack(TNMod.RESONANT_FLEECE.get(), 2));
        if (level() instanceof ServerLevel server)
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE,
                    getX(), getY() + 0.9D, getZ(), 5, 0.3D, 0.2D, 0.3D, 0.01D);
        return true;
    }

    @Override public BellwoolSheepEntity getBreedOffspring(ServerLevel level, AgeableMob partner) {
        BellwoolSheepEntity child = FaunaRegistry.BELLWOOL_SHEEP.create(level);
        if (child != null) {
            child.setAge(-48_000);
            if (partner instanceof BellwoolSheepEntity other && caretaker != null
                    && caretaker.equals(other.caretaker)) child.caretaker = caretaker;
        }
        return child;
    }

    @Override public void finalizeSpawnChildFromBreeding(ServerLevel level, Animal partner,
                                                         @Nullable AgeableMob child) {
        super.finalizeSpawnChildFromBreeding(level, partner, child);
        nextBreedTick = level.getGameTime() + BREED_INTERVAL;
        if (partner instanceof BellwoolSheepEntity other)
            other.nextBreedTick = level.getGameTime() + BREED_INTERVAL;
        if (child != null) child.setAge(-48_000);
    }

    @Override public void addAdditionalSaveData(CompoundTag data) {
        super.addAdditionalSaveData(data);
        data.putBoolean("BellwoolSheared", isSheared());
        data.putLong("BellwoolNextHarvest", nextHarvestTick);
        data.putLong("BellwoolNextBreed", nextBreedTick);
        data.putLong("BellwoolLastActive", lastActiveTick);
        if (caretaker != null) data.putUUID("BellwoolCaretaker", caretaker);
    }

    @Override public void readAdditionalSaveData(CompoundTag data) {
        super.readAdditionalSaveData(data);
        setSheared(data.getBoolean("BellwoolSheared"));
        nextHarvestTick = data.getLong("BellwoolNextHarvest");
        nextBreedTick = data.getLong("BellwoolNextBreed");
        caretaker = data.hasUUID("BellwoolCaretaker") ? data.getUUID("BellwoolCaretaker") : null;
        long now = level().getGameTime();
        long elapsed = Math.max(0L, now - data.getLong("BellwoolLastActive"));
        if (data.contains("BellwoolLastActive") && elapsed > 0L) {
            // Loaded time counts fully. Unloaded time gives only half credit,
            // never more than one new harvest eligibility or juvenile maturity.
            long credit = elapsed / 2L;
            if (isSheared())
                nextHarvestTick = now + Math.max(0L,
                        nextHarvestTick - data.getLong("BellwoolLastActive") - credit);
            if (getAge() < 0)
                setAge((int) Math.min(0L, (long) getAge() + Math.min(48_000L, credit)));
            if (nextBreedTick > 0L)
                nextBreedTick = now + Math.max(0L,
                        nextBreedTick - data.getLong("BellwoolLastActive") - credit);
        }
        lastActiveTick = now;
    }

    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound() { return SoundEvents.SHEEP_AMBIENT; }
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return SoundEvents.SHEEP_HURT;
    }
    @Override protected net.minecraft.sounds.SoundEvent getDeathSound() { return SoundEvents.SHEEP_DEATH; }
}
