package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.combat.DownedCombat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 演武场的服务端逻辑：谁在场内、陪练怎么出招、场内保护。
 *
 * <p>场地在天空岛上（见 {@link PracticeArena}），这里只管「练」这一层：
 * 陪练傀儡的节拍挥击、场内不掉血、教官只吃玩家的正面一击。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class TrainingGround {
    private static final Map<UUID, InstructorEntity> TEACHERS = new HashMap<>();

    private TrainingGround() {
    }

    static boolean qualified(ServerPlayer p) {
        var data = TemperingData.get(p.server);
        return p.isAlive() && !p.isCreative() && !p.isSpectator() && !DownedCombat.isDowned(p)
            && data.enrolled(p.getUUID()) && data.talent(p.getUUID()) > 0;
    }

    public static boolean inside(ServerPlayer p) {
        return PracticeArena.inside(p);
    }

    /** 演武场里的教官（不管是陪练还是专项教官）。 */
    public static boolean trainer(Entity entity) {
        return entity instanceof InstructorEntity;
    }

    static boolean owns(Entity entity, ServerPlayer p) {
        return entity instanceof InstructorEntity
            && entity.getPersistentData().hasUUID(InstructorEntity.OWNER)
            && entity.getPersistentData().getUUID(InstructorEntity.OWNER).equals(p.getUUID());
    }

    /** 当前召出的教官角色名；没有则空串。 */
    static String activeRole(ServerPlayer p) {
        var teacher = teacher(p);
        if (teacher != null) return teacher.getPersistentData().getString(InstructorEntity.ROLE);
        var tutors = SpecialistTrainers.trainers(p);
        return tutors.isEmpty() ? "" : tutors.get(0).getPersistentData().getString(InstructorEntity.ROLE);
    }

    static InstructorEntity teacher(ServerPlayer p) {
        var entity = TEACHERS.get(p.getUUID());
        return entity != null && entity.isAlive() && !entity.isRemoved() && entity.level() == p.level()
            ? entity : null;
    }

    /** 当场召出一位教官，站在该玩家的演武位上。 */
    static InstructorEntity spawnTutor(ServerPlayer p, TrainingGroundData.Arena arena, int pad, String role) {
        if (arena == null) return null;
        var center = arena.center();
        int px = TrainingGroundRules.padX(pad, center.getX());
        int pz = TrainingGroundRules.padZ(pad, center.getZ());
        double feet = center.getY() + 1;
        var level = p.serverLevel();
        var tutor = WarriorEntities.INSTRUCTOR.get().create(level);
        if (tutor == null) return null;
        tutor.setVariant(InstructorEntity.variantOf(role));
        tutor.equipForRole();
        tutor.getPersistentData().putUUID(InstructorEntity.OWNER, p.getUUID());
        tutor.getPersistentData().putString(InstructorEntity.ROLE, role);
        tutor.getPersistentData().putDouble("HomeX", px + 0.5);
        tutor.getPersistentData().putDouble("HomeY", feet);
        tutor.getPersistentData().putDouble("HomeZ", pz + 2.5);
        tutor.getPersistentData().putLong("NextStrike", p.server.overworld().getGameTime() + 50);
        tutor.moveTo(px + 0.5, feet, pz + 2.5, 0f, 0f);
        tutor.setCustomName(Component.literal(displayName(role)));
        tutor.setCustomNameVisible(true);
        tutor.setPersistenceRequired();
        if (!level.addFreshEntity(tutor)) return null;
        tutor.playAnimation("greet");
        if ("counter".equals(role)) {
            TEACHERS.put(p.getUUID(), tutor);
        } else {
            SpecialistTrainers.adopt(p, tutor);
        }
        return tutor;
    }

    static String displayName(String role) {
        return switch (role) {
            case "guard" -> "洛克 · 盾卫";
            case "blade" -> "莉娅 · 剑术";
            case "wind" -> "岚 · 风刃";
            default -> "演武陪练 · 守势反击";
        };
    }

    /** 收起这位玩家名下的全部教官与训练状态（不动演武位）。 */
    static void dismissAll(ServerPlayer p) {
        TemperingTraining.cancel(p);
        SpecialistTrainers.remove(p);
        var teacher = TEACHERS.remove(p.getUUID());
        if (teacher != null) teacher.discard();
        // 把玩家周围所有归属于他的教官一起收掉，不依赖台子坐标。
        p.serverLevel().getEntitiesOfClass(InstructorEntity.class,
                p.getBoundingBox().inflate(64, 24, 64), entity -> owns(entity, p))
            .forEach(Entity::discard);
    }

    /** 陪练傀儡的节拍：站回自己的位子、面向玩家、按难度周期挥击。 */
    static void practiceTick(ServerPlayer p) {
        if (!inside(p)) return;
        if (!qualified(p)) {
            dismissAll(p);
            TrainingGroundData.get(p.server).release(p.getUUID());
            return;
        }
        var teacher = teacher(p);
        if (teacher == null) return;
        var tag = teacher.getPersistentData();
        teacher.setPos(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        teacher.setHealth(teacher.getMaxHealth());
        teacher.clearFire();
        var delta = p.position().subtract(teacher.position());
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        teacher.setYRot(yaw);
        teacher.yBodyRot = yaw;
        teacher.setYHeadRot(yaw);
        long now = p.server.overworld().getGameTime();
        int mode = TemperingTraining.mode(p);
        int period = PracticeDifficulty.period(mode);
        int windup = PracticeDifficulty.windup(mode);
        boolean active = TemperingTraining.active(p);
        teacher.setCustomName(Component.literal("演武陪练 · " + PracticeDifficulty.name(mode) + " · "
            + (!active ? "按修习键开始" : TemperingTraining.blockCount(p) < 3 ? "举盾 "
            + TemperingTraining.blockCount(p) + "/3" : TemperingTraining.remaining(p, now) > 0 ? "现在反击！" : "再次格挡刷新窗口")));
        if (!active) {
            tag.putLong("NextStrike", now + period);
            tag.putBoolean("Windup", false);
            return;
        }
        if (teacher.distanceToSqr(p) > 12.25) {
            tag.putLong("NextStrike", now + period);
            tag.putBoolean("Windup", false);
            if (now % 40 == 0) p.displayClientMessage(Component.literal("请靠近陪练，正面举盾；盾挡后可向前补位。"), true);
            return;
        }
        long next = tag.getLong("NextStrike");
        if (now >= next - windup && !tag.getBoolean("Windup")) {
            tag.putBoolean("Windup", true);
            p.serverLevel().sendParticles(ParticleTypes.CRIT, teacher.getX(), teacher.getY() + 1.8,
                teacher.getZ(), 5, .18, .15, .18, 0);
            p.serverLevel().playSound(null, teacher.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(),
                SoundSource.PLAYERS, .5f, .7f);
        }
        if (now >= next) {
            teacher.playAnimation("attack");
            p.hurt(p.damageSources().mobAttack(teacher), 4);
            tag.putInt("Swings", tag.getInt("Swings") + 1);
            tag.putLong("NextStrike", now + period);
            tag.putBoolean("Windup", false);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        practiceTick(p);
        // 走远了就把演武位让出来，别一直占着。
        var data = TrainingGroundData.get(p.server);
        if (data.slot(p.getUUID()) >= 0 && !inside(p)) {
            dismissAll(p);
            data.release(p.getUUID());
            TemperingEvents.sync(p);
        }
    }

    static void resetRhythm(ServerPlayer p) {
        var teacher = teacher(p);
        if (teacher == null) return;
        var tag = teacher.getPersistentData();
        tag.putLong("NextStrike", p.server.overworld().getGameTime() + PracticeDifficulty.period(TemperingTraining.mode(p)));
        tag.putBoolean("Windup", false);
    }

    static boolean cyclePractice(ServerPlayer p) {
        if (!inside(p) || !qualified(p) || TemperingTraining.active(p)) return false;
        TrainingGroundData.get(p.server).cycle(p.getUUID());
        resetRhythm(p);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void safePlayer(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && inside(p)) {
            event.setCanceled(true);
            if (event.getAmount() > 0 && owns(event.getSource().getEntity(), p)) {
                TemperingTraining.miss(p);
                p.displayClientMessage(Component.literal("未挡住这次挥击。观察前摇，正面举盾后再反击。"), true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void trainerAttack(LivingAttackEvent event) {
        if (!trainer(event.getEntity())) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || !owns(event.getEntity(), p) || !inside(p)
            || !(event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)
                || SpecialistTrainers.isWindTarget(event.getEntity())
                && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC)
                && WindInfusion.isWind(p.getMainHandItem()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void trainerHurt(LivingHurtEvent event) {
        if (trainer(event.getEntity())) event.setAmount(Math.min(1, event.getAmount()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void shield(ShieldBlockEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && inside(p) && owns(event.getDamageSource().getEntity(), p)) {
            event.setShieldTakesDamage(false);
        }
    }

    @SubscribeEvent
    public static void experience(LivingExperienceDropEvent event) {
        if (trainer(event.getEntity())) event.setDroppedExperience(0);
    }

    @SubscribeEvent
    public static void drops(LivingDropsEvent event) {
        if (trainer(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p && inside(p)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void place(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && inside(p)) event.setCanceled(true);
    }

    private static boolean practiceItem(ItemStack stack) {
        return stack.isEmpty() || WindInfusion.weapon(stack) || stack.getItem() instanceof ShieldItem || stack.isEdible();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void useItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer p && inside(p) && !practiceItem(event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !inside(p)) return;
        // 演武终端是场内唯一允许右键的方块。
        if (event.getLevel().getBlockState(event.getPos()).is(TNMod.PRACTICE_TERMINAL.get())) return;
        event.setUseBlock(Event.Result.DENY);
        if (!practiceItem(event.getItemStack())) event.setUseItem(Event.Result.DENY);
    }

    @SubscribeEvent
    public static void tool(BlockEvent.BlockToolModificationEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p && inside(p)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) PracticeArena.rescueLegacy(p);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            dismissAll(p);
            TrainingGroundData.get(p.server).release(p.getUUID());
        }
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            dismissAll(p);
            TrainingGroundData.get(p.server).release(p.getUUID());
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        TEACHERS.clear();
    }

    /** 供命令/测试用：直接判定某坐标是否在场内。 */
    public static boolean contains(double x, double y, double z, ServerPlayer p) {
        return TrainingGroundData.get(p.server)
            .arenaAt(p.level().dimension().location().toString(), x, y, z) != null;
    }
}
