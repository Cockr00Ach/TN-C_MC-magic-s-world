package com.tnc.tnc.warrior;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 演武场教官：一块会动的木人桩。
 *
 * <p>它只做三件事：站在自己的演武位上、按节拍挥击、被学员反击打中后记数。
 * 没有 AI（{@link #registerGoals()} 空）、不自然消失、不掉落、不烧不淹不摔。
 * 外观是 GeckoLib 的独立模型（geo + 4 套贴图），不再借用尸壳。
 */
public class InstructorEntity extends Monster implements GeoEntity {
    /** 陪练傀儡：守势反击的靶子。 */
    public static final int VARIANT_COUNTER = 0;
    /** 洛克 · 盾卫。 */
    public static final int VARIANT_GUARD = 1;
    /** 莉娅 · 剑术。 */
    public static final int VARIANT_BLADE = 2;
    /** 岚 · 风刃。 */
    public static final int VARIANT_WIND = 3;
    /** 变体名（存档/贴图/角色名共用）。 */
    public static final String[] VARIANTS = {"counter", "guard", "blade", "wind"};
    /** 教官佩戴的角色标记，SpecialistTrainers / TrainingGround 用它区分。 */
    public static final String ROLE = "TncSpecialist";
    /** 教官归属的玩家。 */
    public static final String OWNER = "TncPracticeOwner";

    private static final EntityDataAccessor<Integer> DATA_VARIANT =
        SynchedEntityData.defineId(InstructorEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public InstructorEntity(EntityType<? extends InstructorEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 200.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.0D)
            .add(Attributes.ATTACK_DAMAGE, 0.0D)
            .add(Attributes.FOLLOW_RANGE, 8.0D);
    }

    /** 木人桩不追人：目标选择交给演武场的节拍逻辑。 */
    @Override
    protected void registerGoals() {
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_VARIANT, VARIANT_COUNTER);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("InstructorVariant", variant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setVariant(tag.getInt("InstructorVariant"));
    }

    public int variant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public void setVariant(int variant) {
        this.entityData.set(DATA_VARIANT, Math.max(0, Math.min(VARIANTS.length - 1, variant)));
    }

    /** 变体名 → 变体号；未知名字回落到陪练傀儡。 */
    public static int variantOf(String role) {
        for (int i = 0; i < VARIANTS.length; i++) {
            if (VARIANTS[i].equals(role)) return i;
        }
        return VARIANT_COUNTER;
    }

    public String roleName() {
        return VARIANTS[Math.max(0, Math.min(VARIANTS.length - 1, variant()))];
    }

    /**
     * 真的给教官手里塞东西——不是画上去的几何体，是装备槽里的 {@code ItemStack}。
     *
     * <p>陪练拿木剑（假想敌，别真开刃）；洛克铁剑 + 副手盾（他就是教举盾的）；
     * 莉娅铁剑（三段连击）；岚是风刃（{@code WindInfusion} 那把带风元素标记的剑，
     * 和学员要拿的东西一致）。渲染由 {@code InstructorRenderer} 在手腕骨骼上画。
     */
    public void equipForRole() {
        var main = new net.minecraft.world.item.ItemStack(
            "counter".equals(roleName())
                ? net.minecraft.world.item.Items.WOODEN_SWORD
                : net.minecraft.world.item.Items.IRON_SWORD);
        if ("wind".equals(roleName())) main = WindInfusion.infuse(main);
        setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, main);
        // 只有盾卫举盾：陪练要空出手来挥，剑士与风刃不配盾。
        setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
            "guard".equals(roleName())
                ? new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD)
                : net.minecraft.world.item.ItemStack.EMPTY);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /** 只吃玩家与生物的正面一击；火、摔、淹、岩浆这些环境伤害不参与演武。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_FALL)
            || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && this.isAlive() && this.getHealth() < this.getMaxHealth() * 0.35F) {
            this.setHealth(this.getMaxHealth());
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        triggerAnim("action", "death");
        super.die(source);
    }

    /** 动作播报：由演武场在挥击/举盾/起风时调用。 */
    public void playAnimation(String name) {
        if (name != null && !name.isEmpty()) triggerAnim("action", name);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 4,
            state -> state.setAndContinue(RawAnimation.begin().thenLoop("idle"))));
        AnimationController<InstructorEntity> action = new AnimationController<>(this, "action", 2,
            state -> PlayState.STOP);
        action.triggerableAnim("attack", RawAnimation.begin().thenPlay("attack"));
        action.triggerableAnim("block", RawAnimation.begin().thenPlay("block"));
        action.triggerableAnim("cast", RawAnimation.begin().thenPlay("cast"));
        action.triggerableAnim("greet", RawAnimation.begin().thenPlay("greet"));
        action.triggerableAnim("death", RawAnimation.begin().thenPlay("death"));
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
