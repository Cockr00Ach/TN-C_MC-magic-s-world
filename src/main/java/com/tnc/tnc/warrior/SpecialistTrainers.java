package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ShieldItem;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 三位专项教官的课：盾卫（洛克）、剑术（莉娅）、风刃（岚）。
 *
 * <p>教官不是常驻 NPC —— 玩家在演武终端点谁，谁才当场显形，课也就开始了；
 * 结束或走开就退场。判定全部落在服务器：盾挡、蓄满的剑击、三格外风刃命中。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class SpecialistTrainers {
    static final String ROLE = InstructorEntity.ROLE;
    static final String OWNER = InstructorEntity.OWNER;
    private static final Map<UUID, List<InstructorEntity>> NPCS = new HashMap<>();
    private static final Map<UUID, Lesson> LESSONS = new HashMap<>();

    private static final class Lesson {
        final String role;
        final UUID target;
        final long until;
        int progress;
        long next;
        long click = Long.MIN_VALUE;
        long lastHit = Long.MIN_VALUE;
        boolean charged;

        Lesson(String role, InstructorEntity tutor, long now) {
            this.role = role;
            this.target = tutor.getUUID();
            this.until = now + 900;
            this.next = now + 50;
        }
    }

    private SpecialistTrainers() {
    }

    static String name(String role) {
        return switch (role) {
            case "guard" -> "洛克 · 盾卫";
            case "blade" -> "莉娅 · 剑术";
            default -> "岚 · 风刃";
        };
    }

    static int goal(String role) {
        return role.equals("wind") ? 3 : 5;
    }

    static boolean isWindTarget(Entity entity) {
        return entity != null && "wind".equals(entity.getPersistentData().getString(ROLE));
    }

    static List<InstructorEntity> trainers(ServerPlayer p) {
        return NPCS.getOrDefault(p.getUUID(), List.of());
    }

    /** 认领一位刚召出的专项教官（每人同时只带一位）。 */
    static void adopt(ServerPlayer p, InstructorEntity tutor) {
        cancel(p, false);
        remove(p);
        NPCS.put(p.getUUID(), new ArrayList<>(List.of(tutor)));
    }

    static void remove(ServerPlayer p) {
        cancel(p, false);
        var list = NPCS.remove(p.getUUID());
        if (list != null) list.forEach(Entity::discard);
    }

    static void cancel(ServerPlayer p, boolean notify) {
        if (LESSONS.remove(p.getUUID()) != null && notify) {
            p.sendSystemMessage(Component.literal("本次专项练习已结束，可在终端重新召出教官。"));
        }
    }

    static int progress(ServerPlayer p) {
        var lesson = LESSONS.get(p.getUUID());
        return lesson == null ? 0 : lesson.progress;
    }

    static String diagnostic(ServerPlayer p) {
        var lesson = LESSONS.get(p.getUUID());
        return lesson == null ? "no lesson"
            : lesson.role + " progress=" + lesson.progress + " charged=" + lesson.charged
            + " hit=" + lesson.lastHit + " until=" + lesson.until;
    }

    static boolean begin(ServerPlayer p, InstructorEntity teacher) {
        if (teacher == null || !TrainingGround.inside(p) || !TrainingGround.qualified(p)
            || !teacher.isAlive() || !teacher.getPersistentData().hasUUID(OWNER)
            || !teacher.getPersistentData().getUUID(OWNER).equals(p.getUUID())
            || p.distanceToSqr(teacher) > 16) {
            return false;
        }
        String role = teacher.getPersistentData().getString(ROLE);
        if (!Set.of("guard", "blade", "wind").contains(role)) return false;
        if (role.equals("guard") && !(p.getOffhandItem().getItem() instanceof ShieldItem)) {
            p.sendSystemMessage(Component.literal("洛克：请在副手装备盾牌，再来找我。"));
            return false;
        }
        if (role.equals("blade") && !TemperingEvents.weapon(p)) {
            p.sendSystemMessage(Component.literal("莉娅：请在主手持剑或斧，再来找我。"));
            return false;
        }
        if (role.equals("wind") && (!WindInfusion.isWind(p.getMainHandItem())
            || TemperingData.get(p.server).points(p.getUUID()) < 200)) {
            p.sendSystemMessage(Component.literal("岚：需要风灌注剑斧和 200 磨砺，再来练风刃。"));
            return false;
        }
        TemperingTraining.cancel(p);
        LESSONS.put(p.getUUID(), new Lesson(role, teacher, p.server.overworld().getGameTime()));
        teacher.playAnimation(role.equals("guard") ? "block" : role.equals("wind") ? "cast" : "greet");
        p.sendSystemMessage(Component.literal(name(role) + "：" + switch (role) {
            case "guard" -> "面向我，完成五次有效盾挡。观察出招前摇；击退后向前补位。";
            case "blade" -> "等攻击指示器蓄满，再持剑或斧命中我五次；急挥不计进度。";
            default -> "退到至少三格外，面向我，持风灌注武器潜行对空右键，三次风刃命中。冷却仍为八秒。";
        } + " 限时45秒，潜行右键可取消。已完成 "
            + SpecialistTrainingData.get(p.server).count(p.getUUID(), role) + " 次。"));
        return true;
    }

    private static void advance(ServerPlayer p, Lesson lesson) {
        lesson.progress++;
        p.displayClientMessage(Component.literal(name(lesson.role) + " · " + lesson.progress + "/" + goal(lesson.role)), true);
        if (lesson.progress < goal(lesson.role)) return;
        LESSONS.remove(p.getUUID());
        SpecialistTrainingData.get(p.server).complete(p.getUUID(), lesson.role);
        p.sendSystemMessage(Component.literal(name(lesson.role) + "：本次专项练习完成！纪录已保存；专项练习不额外发放磨砺奖励。"));
        p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 12, .4, .6, .4, 0);
    }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getTarget() instanceof InstructorEntity t)
            || !t.getPersistentData().contains(ROLE)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (p.isShiftKeyDown()) {
            if (t.getPersistentData().hasUUID(OWNER) && t.getPersistentData().getUUID(OWNER).equals(p.getUUID())) {
                cancel(p, true);
            }
            return;
        }
        var old = LESSONS.get(p.getUUID());
        long now = p.server.overworld().getGameTime();
        if (old != null && old.click != Long.MIN_VALUE && now - old.click < 10) return;
        if (begin(p, t)) LESSONS.get(p.getUUID()).click = now;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void block(ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        var lesson = LESSONS.get(p.getUUID());
        if (lesson != null && lesson.role.equals("guard") && event.getBlockedDamage() > 0
            && event.getDamageSource().getEntity() != null && lesson.target.equals(event.getDamageSource().getEntity().getUUID())) {
            long now = p.server.overworld().getGameTime();
            if (lesson.lastHit == Long.MIN_VALUE || now - lesson.lastHit >= 10) {
                lesson.lastHit = now;
                advance(p, lesson);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void swing(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        var lesson = LESSONS.get(p.getUUID());
        if (lesson != null && lesson.role.equals("blade") && lesson.target.equals(event.getTarget().getUUID())) {
            lesson.charged = p.getAttackStrengthScale(.5f) >= .85f && TemperingEvents.weapon(p);
            lesson.lastHit = p.server.overworld().getGameTime();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hit(LivingDamageEvent event) {
        if (event.getAmount() <= 0 || !(event.getSource().getEntity() instanceof ServerPlayer p)
            || event.getSource().getDirectEntity() != p
            || !event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)) {
            return;
        }
        var lesson = LESSONS.get(p.getUUID());
        if (lesson != null && lesson.role.equals("blade") && lesson.target.equals(event.getEntity().getUUID())) {
            if (lesson.charged && lesson.lastHit == p.server.overworld().getGameTime()) {
                lesson.charged = false;
                advance(p, lesson);
            } else {
                p.displayClientMessage(Component.literal("莉娅：这次太急了，等攻击指示器蓄满。"), true);
            }
        }
    }

    static void windHit(ServerPlayer p, LivingEntity target) {
        var lesson = LESSONS.get(p.getUUID());
        if (lesson != null && lesson.role.equals("wind") && lesson.target.equals(target.getUUID())) {
            if (p.distanceToSqr(target) >= 9) advance(p, lesson);
            else p.displayClientMessage(Component.literal("岚：再退远一些，至少三格外命中才计数。"), true);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        var lesson = LESSONS.get(p.getUUID());
        long now = p.server.overworld().getGameTime();
        if (lesson != null && (!TrainingGround.qualified(p) || TemperingTraining.active(p) || now >= lesson.until)) {
            cancel(p, true);
            lesson = null;
        }
        for (var tutor : trainers(p)) {
            if (tutor.isRemoved()) continue;
            var tag = tutor.getPersistentData();
            tutor.setPos(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
            tutor.setHealth(tutor.getMaxHealth());
            tutor.clearFire();
            var delta = p.position().subtract(tutor.position());
            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            tutor.setYRot(yaw);
            tutor.yBodyRot = yaw;
            tutor.setYHeadRot(yaw);
            String role = tag.getString(ROLE);
            boolean active = lesson != null && lesson.target.equals(tutor.getUUID());
            tutor.setCustomName(Component.literal(name(role) + " · " + (active
                ? lesson.progress + "/" + goal(role) + " · 剩余" + Math.max(0, (lesson.until - now) / 20) + "秒"
                : "右键开始")));
            if (!active || !role.equals("guard")) continue;
            if (p.distanceToSqr(tutor) > 12.25) {
                lesson.next = now + 50;
                if (now % 40 == 0) p.displayClientMessage(Component.literal("洛克：靠近我三格以内，正面举盾。"), true);
                continue;
            }
            if (now == lesson.next - 15) {
                p.serverLevel().sendParticles(ParticleTypes.CRIT, tutor.getX(), tutor.getY() + 1.8, tutor.getZ(), 5, .15, .15, .15, 0);
            }
            if (now >= lesson.next) {
                lesson.next = now + 50;
                tutor.playAnimation("attack");
                p.hurt(p.damageSources().mobAttack(tutor), 4);
            }
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        NPCS.clear();
        LESSONS.clear();
    }
}
