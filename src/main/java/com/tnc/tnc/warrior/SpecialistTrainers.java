package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * 教官的课：盾卫（洛克）、剑术（莉娅）、风刃（岚），外加陪练的连招靶。
 *
 * <p>教官不是常驻 NPC —— 玩家在演武终端点谁，谁才当场显形；结束或走开就退场。
 * 判定全部落在服务器。
 *
 * <p>从"一位教官一节课"改成<b>课程表</b>（见 {@link InstructorCourses}）后，每节课只考一件事：
 * 举盾、振刀、破架、蓄满、破锋式、旋转斩击、逆鳞斩、风刃、穿云箭、后跃射。
 * 过关就当场开下一课，不用退出去重新召。结课记在 {@link SpecialistTrainingData} 的
 * {@code role/courseId} 上，所以出师过的课不会被复习打回第一课。
 *
 * <p>连招课不监听任何连招事件：{@link WarriorComboService} 把每条连招的冷却写进自己的
 * {@code State.cooldowns}，这里<b>轮询冷却的变化</b>就能知道"刚刚放出了哪一招"。
 * 好处是两边文件互不依赖 —— 连招系统改判定、加招式，教官这边一行都不用动。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class SpecialistTrainers {
    static final String ROLE = InstructorEntity.ROLE;
    static final String OWNER = InstructorEntity.OWNER;
    /** 一节课 45 秒。 */
    private static final int LESSON_TICKS = 900;
    private static final Map<UUID, List<InstructorEntity>> NPCS = new HashMap<>();
    private static final Map<UUID, Lesson> LESSONS = new HashMap<>();
    /** 陪练的连招靶：陪练的课由 {@link TemperingTraining} 管，这里只旁听连招。 */
    private static final Map<UUID, Target> TARGETS = new HashMap<>();

    /** 夸奖的短句，按进度取，同一玩家每次顺序稳定。 */
    private static final String[] PRAISE = {"稳。", "就是这样。", "记住这个节奏。", "手别僵。", "干净利落。"};
    /** 出师时说的话。 */
    private static final String[] FAREWELL = {
        "这一套我教完了，剩下的靠你自己在实战里磨。",
        "我这儿没什么可教的了。去岛上找人打吧。",
        "课到这儿为止。以后想练哪一节，右键我复习。",
    };

    private static final class Lesson {
        final String role;
        final UUID target;
        final InstructorCourses.Course course;
        /** 复习局：进度照走，但不再记新课。 */
        final boolean review;
        /** 上一次看到的连招冷却：值变大就说明这一招刚放出来。 */
        final Map<String, Long> seen = new HashMap<>();
        final long until;
        int progress;
        long next;
        long click = Long.MIN_VALUE;
        long lastHit = Long.MIN_VALUE;
        boolean charged;
        /** 已经抬手、这一 tick 真正落下伤害：给学员看清前摇的时间。 */
        long strikeAt = 0L;
        /** 上一次进度的 tick：用来判断"学员是不是卡住了"，卡住就示范一次。 */
        long lastProgressAt;
        /** 上一次给提示的 tick（错招/示范都走这个限流）。 */
        long lastHintAt = Long.MIN_VALUE;
        /** 已经示范过几次（最多两次，免得一直刷屏）。 */
        int demos;

        Lesson(String role, InstructorEntity tutor, InstructorCourses.Course course, boolean review, long now) {
            this.role = role;
            this.target = tutor.getUUID();
            this.course = course;
            this.review = review;
            this.until = now + LESSON_TICKS;
            this.next = now + 50;
            this.lastProgressAt = now;
        }

        /** 这一课要按的键，教给学员看；没指定连招（架势课）就给个例子。 */
        String keys() {
            var skill = course.skill();
            if (skill != null) return skill.holdLabel();
            if (course.kind() == InstructorCourses.Kind.STANCE) {
                var sample = WarriorCombos.byId("sword_combo_4");
                return sample == null ? "任意架势招" : sample.holdLabel() + "（或任意架势招）";
            }
            return switch (course.kind()) {
                case SHIELD_BLOCK -> "举盾挡住我";
                case CHARGED_HIT -> "等蓄满再砍";
                case WIND_RANGED -> "三格外风刃";
                default -> "任意连招";
            };
        }
    }

    private static final class Target {
        final InstructorCourses.Course course;
        final Map<String, Long> seen = new HashMap<>();
        int progress;

        Target(InstructorCourses.Course course) {
            this.course = course;
        }
    }

    private SpecialistTrainers() {
    }

    static String name(String role) {
        return switch (role) {
            case "guard" -> "洛克 · 盾卫";
            case "blade" -> "莉娅 · 剑术";
            case "wind" -> "岚 · 风刃";
            case "counter" -> "演武陪练 · 守势反击";
            default -> role;
        };
    }

    /** 第一课的目标次数（旧调用方/旧测试的兼容口）。 */
    static int goal(String role) {
        var first = InstructorCourses.first(role);
        return first == null ? 1 : first.goal();
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
        LESSONS.remove(p.getUUID());
        TARGETS.remove(p.getUUID());
        if (notify) {
            p.sendSystemMessage(Component.literal("本次专项练习已结束，可在终端重新召出教官。"));
        }
        // 头顶/HUD/终端都读快照里的课程行，下课时要推一次，否则界面上还挂着上一课。
        TemperingEvents.sync(p);
    }

    /** 当前进度：专项课看这一节，陪练看连招靶。 */
    static int progress(ServerPlayer p) {
        var lesson = LESSONS.get(p.getUUID());
        if (lesson != null) return lesson.progress;
        var target = TARGETS.get(p.getUUID());
        return target == null ? 0 : target.progress;
    }

    static String diagnostic(ServerPlayer p) {
        var lesson = LESSONS.get(p.getUUID());
        if (lesson != null) {
            return lesson.role + "/" + lesson.course.id() + " progress=" + lesson.progress
                + " charged=" + lesson.charged + " hit=" + lesson.lastHit + " until=" + lesson.until
                + " review=" + lesson.review;
        }
        var target = TARGETS.get(p.getUUID());
        return target == null ? "no lesson" : "counter/" + target.course.id() + " progress=" + target.progress;
    }

    /**
     * 面板/图鉴用的紧凑出师进度：{@code guard:1/3|blade:0/4|wind:0/3|counter:0/1}。
     *
     * <p>用一条字符串而不是四个字段，是因为它只被"显示"用：客户端按 {@code role:done/total}
     * 拆开即可，协议不用为每多一位教官再加一个字段。
     */
    static String coursesLine(ServerPlayer p) {
        var data = SpecialistTrainingData.get(p.server);
        StringBuilder out = new StringBuilder();
        for (String role : InstructorCourses.roles()) {
            int total = InstructorCourses.count(role);
            if (total == 0) continue;
            int done = InstructorCourses.doneCount(role, c -> data.done(p.getUUID(), role, c.id()));
            if (out.length() > 0) out.append('|');
            out.append(role).append(':').append(done).append('/').append(total);
        }
        return out.toString();
    }

    /** 正在上的那一节（{@code 第二课 · 振刀 1/2}）；没在上课返回空串。 */
    static String activeCourse(ServerPlayer p) {
        var lesson = LESSONS.get(p.getUUID());
        if (lesson != null) {
            return lesson.course.progressText(lesson.progress) + (lesson.review ? "（复习）" : "");
        }
        var target = TARGETS.get(p.getUUID());
        return target == null ? "" : target.course.progressText(target.progress);
    }

    /** 教官的门派名，用于聊天里的「洛克 · 盾卫」。 */
    static String title(String role) {
        return switch (role == null ? "" : role) {
            case "guard" -> "盾卫";
            case "blade" -> "剑术";
            case "wind" -> "风刃";
            case "counter" -> "陪练";
            default -> "";
        };
    }

    /**
     * {@code /tempering courses}：把某位（不填就是全部）教官的课程表打到聊天栏，带自己的进度。
     *
     * <p>为什么不只靠终端面板：课程是"按顺序开"的，玩家常常想知道"后面还有什么"、
     * "我卡在哪一课"，而面板一次只显示选中那一位。聊天栏能一次看全，也方便截图问人。
     */
    static void listCourses(ServerPlayer p, String role) {
        var data = SpecialistTrainingData.get(p.server);
        String[] roles = role == null ? InstructorCourses.roles() : new String[]{role};
        for (String each : roles) {
            if (!InstructorCourses.known(each)) {
                p.sendSystemMessage(Component.literal("没有这位教官：" + each + "（可用 counter / guard / blade / wind）"));
                continue;
            }
            int total = InstructorCourses.count(each);
            int done = InstructorCourses.doneCount(each, c -> data.done(p.getUUID(), each, c.id()));
            p.sendSystemMessage(Component.literal("§6" + name(each) + " · " + title(each)
                + "§r  出师 " + done + "/" + total + (done >= total ? " §a（已出师）" : "")));
            int index = 1;
            for (var course : InstructorCourses.of(each)) {
                boolean ok = data.done(p.getUUID(), each, course.id());
                p.sendSystemMessage(Component.literal((ok ? "  §a✓ " : "  §7· ") + index + ". §r"
                    + course.title() + " §7（" + course.goal() + " 次）" + course.brief()));
                index++;
            }
        }
    }

    // ------------------------------------------------------------------
    //  开课
    // ------------------------------------------------------------------

    /**
     * 右键教官开下一节课。
     *
     * <p>下一节 = 课程表里第一节还没出师的课；全出师了就开最后一节的复习局。
     *
     * @return 是否真的开起来了（条件不满足会当场说明原因）
     */
    static boolean begin(ServerPlayer p, InstructorEntity teacher) {
        if (teacher == null || !TrainingGround.inside(p) || !TrainingGround.qualified(p)
            || !teacher.isAlive() || !teacher.getPersistentData().hasUUID(OWNER)
            || !teacher.getPersistentData().getUUID(OWNER).equals(p.getUUID())
            || p.distanceToSqr(teacher) > 16) {
            return false;
        }
        String role = teacher.getPersistentData().getString(ROLE);
        if (!InstructorCourses.known(role) || "counter".equals(role)) return false;
        var data = SpecialistTrainingData.get(p.server);
        var course = InstructorCourses.next(role, c -> data.done(p.getUUID(), role, c.id()));
        boolean review = course == null;
        // 复习不再"永远重开最后一课"：按已结课次数轮着上，见 InstructorCourses.review。
        if (review) course = InstructorCourses.review(role, data.count(p.getUUID(), role));
        if (course == null) return false;
        if (!ready(p, role, course)) return false;

        TemperingTraining.cancel(p);
        long now = now(p);
        var lesson = new Lesson(role, teacher, course, review, now);
        seed(lesson, p);
        LESSONS.put(p.getUUID(), lesson);
        teacher.playAnimation(role.equals("guard") ? "block" : role.equals("wind") ? "cast" : "greet");
        p.sendSystemMessage(Component.literal(name(role) + "：" + course.brief()
            + (review ? "（复习局，不再记新课。）" : "")
            + " 限时45秒，潜行右键取消。"));
        p.displayClientMessage(Component.literal(course.title() + " · 要按 " + lesson.keys()), true);
        if (review) {
            p.sendSystemMessage(Component.literal(name(role) + "：这套你已经学全了。复习轮着来，"
                + "这次是 " + course.title() + "，练完下次换下一节。"));
        }
        TemperingEvents.sync(p);
        return true;
    }

    /** 这节课的装备/状态条件；不满足时顺带把原因说清楚。 */
    private static boolean ready(ServerPlayer p, String role, InstructorCourses.Course course) {
        String problem = problem(p, course, true);
        if (problem == null) return true;
        p.sendSystemMessage(Component.literal(name(role) + "：" + problem));
        return false;
    }

    /**
     * 上这节课缺什么，缺就返回一句话；什么都不缺返回 {@code null}。
     *
     * @param entry {@code true} 表示开课入场检查（含磨砺门槛），{@code false} 表示上课途中检查
     */
    private static String problem(ServerPlayer p, InstructorCourses.Course course, boolean entry) {
        var skill = course.skill();
        if (skill != null) {
            var family = WarriorWeapons.family(p.getMainHandItem());
            return family == skill.family()
                ? null
                : course.title() + " 要拿" + skill.family().cn() + "，换好武器再来。";
        }
        return switch (course.kind()) {
            case SHIELD_BLOCK -> p.getOffhandItem().getItem() instanceof ShieldItem
                ? null : "请在副手装备盾牌，再来找我。";
            case CHARGED_HIT, STANCE -> TemperingEvents.weapon(p)
                ? null : "请在主手持剑或斧，再来找我。";
            case WIND_RANGED -> {
                if (!WindInfusion.isWind(p.getMainHandItem())) {
                    yield "需要风灌注剑斧和 200 磨砺，再来练风刃。";
                }
                boolean enough = TemperingData.get(p.server).points(p.getUUID()) >= 200;
                yield !entry || enough ? null : "需要风灌注剑斧和 200 磨砺，再来练风刃。";
            }
            case COMBO, ANY_COMBO -> null;
        };
    }

    // ------------------------------------------------------------------
    //  计数
    // ------------------------------------------------------------------

    /**
     * 过一次进度；够数就结课、记档，并当场开下一课。
     *
     * <p>注意换课时 {@link #LESSONS} 里换的是<b>新对象</b>，调用方手里的 {@code lesson}
     * 已经是旧课，之后不要再用它（事件里每次都会重新取）。
     */
    private static void advance(ServerPlayer p, Lesson lesson) {
        lesson.progress++;
        lesson.lastProgressAt = now(p);
        var course = lesson.course;
        p.displayClientMessage(Component.literal(name(lesson.role) + " · "
            + course.progressText(lesson.progress) + " " + PRAISE[Math.floorMod(lesson.progress, PRAISE.length)]), true);
        if (lesson.progress < course.goal()) return;

        long now = now(p);
        var data = SpecialistTrainingData.get(p.server);
        // 复习也记一次"结课次数"（不写课程键）：复习轮到哪一节就是靠这个数转的。
        if (!lesson.review) data.graduate(p.getUUID(), lesson.role, course.id());
        else data.complete(p.getUUID(), lesson.role);
        p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 12, .4, .6, .4, 0);
        // 过关有个"叮"，出师再响一次挑战完成：纯文字在演武场里太容易漏看。
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(),
            SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);

        var next = lesson.review ? null
            : InstructorCourses.next(lesson.role, c -> data.done(p.getUUID(), lesson.role, c.id()));
        if (next == null) {
            LESSONS.remove(p.getUUID());
            String tail = lesson.review
                ? "复习结束。"
                : FAREWELL[Math.floorMod(data.count(p.getUUID(), lesson.role) + lesson.role.hashCode(), FAREWELL.length)];
            p.sendSystemMessage(Component.literal(name(lesson.role) + "：" + course.title() + " 过关。" + tail
                + " 专项课共结 " + data.count(p.getUUID(), lesson.role) + " 次。"));
            if (!lesson.review) {
                p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
            }
            TemperingEvents.sync(p);
            return;
        }
        var tutor = tutor(p, lesson.target);
        if (tutor == null) {
            LESSONS.remove(p.getUUID());
            TemperingEvents.sync(p);
            return;
        }
        // 下一课要换武器（比如风刃课之后是长弓课）：装备不对就先停在这儿，
        // 由 ready() 当场说清楚要拿什么，而不是开一节永远不过的课。
        if (!ready(p, lesson.role, next)) {
            LESSONS.remove(p.getUUID());
            TemperingEvents.sync(p);
            return;
        }
        var fresh = new Lesson(lesson.role, tutor, next, false, now);
        seed(fresh, p);
        LESSONS.put(p.getUUID(), fresh);
        tutor.playAnimation(lesson.role.equals("guard") ? "block" : lesson.role.equals("wind") ? "cast" : "greet");
        p.sendSystemMessage(Component.literal(name(lesson.role) + "：" + course.title() + " 过关。接着来 "
            + next.title() + "——" + next.brief()));
        TemperingEvents.sync(p);
    }

    /** 记住"现在每条连招的冷却停在哪"，之后冷却变大 = 刚放出来一次。 */
    private static void seed(Lesson lesson, ServerPlayer p) {
        var state = WarriorComboService.state(p.getUUID());
        for (var skill : WarriorCombos.all()) {
            lesson.seen.put(skill.id(), state.cooldowns.getOrDefault(skill.id(), 0L));
        }
    }

    private static void seed(Target target, ServerPlayer p) {
        var state = WarriorComboService.state(p.getUUID());
        for (var skill : WarriorCombos.all()) {
            target.seen.put(skill.id(), state.cooldowns.getOrDefault(skill.id(), 0L));
        }
    }

    /**
     * 轮询连招冷却，看这节课要的那一招有没有放出来。
     *
     * <p>冷却被 {@code /tempering combo reset} 清零时会变<b>小</b>：那时只更新记录、不计数，
     * 免得被判成"放了一招"。
     */
    private static void pollCasts(ServerPlayer p, Lesson lesson) {
        if (!lesson.course.pollsCasts()) return;
        var state = WarriorComboService.state(p.getUUID());
        for (var skill : WarriorCombos.all()) {
            long ready = state.cooldowns.getOrDefault(skill.id(), 0L);
            long seen = lesson.seen.getOrDefault(skill.id(), 0L);
            if (ready == seen) continue;
            lesson.seen.put(skill.id(), ready);
            if (ready <= seen) continue;
            if (lesson.course.matches(skill)) {
                advance(p, lesson);
                return;
            }
            // 放错招也要说话：学员按了一套别的连招却毫无反馈，会以为自己按错了键位。
            long now = now(p);
            if (lesson.course.kind() == InstructorCourses.Kind.COMBO && now - lesson.lastHintAt >= 40) {
                lesson.lastHintAt = now;
                p.displayClientMessage(Component.literal("这一课要 " + lesson.keys() + "，刚才那下是 "
                    + skill.name() + "（" + skill.compactLabel() + "）"), true);
            }
        }
    }

    /**
     * 卡住了就示范一次。
     *
     * <p>连招课最怕的不是难，是"我按了但它没反应，我不知道哪一下错了"。所以连续两秒没进度时，
     * 教官把动作打一遍、把按键念一遍；最多两次，不然就成刷屏了。
     */
    private static void coach(ServerPlayer p, Lesson lesson, long now) {
        if (now - lesson.lastProgressAt < 120 || now - lesson.lastHintAt < 120) return;
        if (lesson.course.pollsCasts()) {
            if (lesson.demos >= 2) return;
            lesson.demos++;
            lesson.lastHintAt = now;
            var tutor = tutor(p, lesson.target);
            if (tutor != null) tutor.playAnimation("wind".equals(lesson.role) ? "cast" : "attack");
            p.displayClientMessage(Component.literal(name(lesson.role) + "：看一遍——" + lesson.keys()
                + (lesson.demos >= 2 ? "。慢一点也行。" : "")), true);
            return;
        }
        lesson.lastHintAt = now;
        p.displayClientMessage(Component.literal(name(lesson.role) + "：" + lesson.course.brief()), true);
    }

    // ------------------------------------------------------------------
    //  事件
    // ------------------------------------------------------------------

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
        long now = now(p);
        if (old != null && old.click != Long.MIN_VALUE && now - old.click < 10) return;
        if (begin(p, t)) {
            var started = LESSONS.get(p.getUUID());
            if (started != null) started.click = now;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void block(ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        var lesson = LESSONS.get(p.getUUID());
        if (lesson != null && lesson.course.kind() == InstructorCourses.Kind.SHIELD_BLOCK
            && event.getBlockedDamage() > 0
            && event.getDamageSource().getEntity() != null
            && lesson.target.equals(event.getDamageSource().getEntity().getUUID())) {
            long now = now(p);
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
        if (lesson != null && lesson.course.kind() == InstructorCourses.Kind.CHARGED_HIT
            && lesson.target.equals(event.getTarget().getUUID())) {
            lesson.charged = p.getAttackStrengthScale(.5f) >= .85f && TemperingEvents.weapon(p);
            lesson.lastHit = now(p);
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
        if (lesson != null && lesson.course.kind() == InstructorCourses.Kind.CHARGED_HIT
            && lesson.target.equals(event.getEntity().getUUID())) {
            if (lesson.charged && lesson.lastHit == now(p)) {
                lesson.charged = false;
                advance(p, lesson);
            } else {
                p.displayClientMessage(Component.literal("莉娅：这次太急了，等攻击指示器蓄满。"), true);
            }
        }
    }

    static void windHit(ServerPlayer p, LivingEntity target) {
        var lesson = LESSONS.get(p.getUUID());
        if (lesson != null && lesson.course.kind() == InstructorCourses.Kind.WIND_RANGED
            && lesson.target.equals(target.getUUID())) {
            if (p.distanceToSqr(target) >= 9) {
                advance(p, lesson);
            } else {
                p.displayClientMessage(Component.literal("岚：再退远一些，至少三格外命中才计数。"), true);
            }
        }
    }

    // ------------------------------------------------------------------
    //  每 tick：换课、数连招、转朝向、洛克挥击
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        long now = now(p);
        var lesson = LESSONS.get(p.getUUID());
        if (lesson != null && (!TrainingGround.qualified(p) || TemperingTraining.active(p) || now >= lesson.until)) {
            cancel(p, true);
            lesson = null;
        }
        // 上课途中把武器换了（或盾碎了）就别硬算了：这节课当场停，并说清为什么停。
        if (lesson != null) {
            String problem = problem(p, lesson.course, false);
            if (problem != null) {
                p.sendSystemMessage(Component.literal(name(lesson.role) + "：" + problem + " 这节课先停。"));
                cancel(p, true);
                lesson = null;
            }
        }
        if (lesson != null) {
            pollCasts(p, lesson);
            // 换课时 pollCasts 会把 LESSONS 换成新对象，这里重新取，别对着旧课讲道理。
            var current = LESSONS.get(p.getUUID());
            if (current != null) coach(p, current, now);
        }
        tickTarget(p, now);

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
            boolean guarding = active && role.equals("guard")
                && lesson.course.kind() == InstructorCourses.Kind.SHIELD_BLOCK;
            // 上盾挡课时一直架着盾（block_hold 循环），下课后自动回到 idle。
            tutor.setHolding(guarding);
            tutor.setCustomName(Component.literal(name(role) + " · " + (active
                ? lesson.course.progressText(lesson.progress) + " · 剩余"
                    + Math.max(0, (lesson.until - now) / 20) + "秒"
                : idleLabel(p, role))));
            if (!guarding) continue;
            if (p.distanceToSqr(tutor) > 12.25) {
                lesson.next = now + 50;
                lesson.strikeAt = 0L;
                if (now % 40 == 0) p.displayClientMessage(Component.literal("洛克：靠近我三格以内，正面举盾。"), true);
                continue;
            }
            if (now >= lesson.next) {
                lesson.next = now + 50;
                // 抬手 —— 动作先出来，伤害晚 8 tick 落地，前摇因此看得见。
                tutor.playAnimation("attack");
                lesson.strikeAt = now + 8;
                p.serverLevel().sendParticles(ParticleTypes.CRIT,
                    tutor.getX(), tutor.getY() + 1.8, tutor.getZ(), 10, .18, .18, .18, 0);
                p.serverLevel().playSound(null, tutor.getX(), tutor.getY(), tutor.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.55f, 1.15f);
            }
            if (lesson.strikeAt != 0L && now >= lesson.strikeAt) {
                lesson.strikeAt = 0L;
                if (p.distanceToSqr(tutor) <= 12.25) p.hurt(p.damageSources().mobAttack(tutor), 4);
            }
        }
    }

    /** 没在上课时头顶写什么：下一课是哪节、已经出师几节。 */
    private static String idleLabel(ServerPlayer p, String role) {
        var data = SpecialistTrainingData.get(p.server);
        int done = InstructorCourses.doneCount(role, c -> data.done(p.getUUID(), role, c.id()));
        int total = InstructorCourses.count(role);
        var next = InstructorCourses.next(role, c -> data.done(p.getUUID(), role, c.id()));
        if (next != null) {
            return next.title() + " · 右键开始（出师 " + done + "/" + total + "）";
        }
        // 全学完了：复习轮着来，头顶直接写"下一节是哪节"，省得玩家以为是随机。
        var review = InstructorCourses.review(role, data.count(p.getUUID(), role));
        return "已出师 " + done + "/" + total + " · 右键复习"
            + (review == null ? "" : "（下一节 " + review.title() + "）");
    }

    /**
     * 陪练的连招靶：陪练站着不动挨打，玩家在它身边打满三套连招就算过。
     *
     * <p>它和 {@link TemperingTraining} 的守势反击是两件事：守势课教"挡完再打"，
     * 连招靶只数连招，互不干扰（守势课超时也不会把连招靶清掉）。
     */
    private static void tickTarget(ServerPlayer p, long now) {
        var dummy = TrainingGround.teacher(p);
        if (dummy == null || !"counter".equals(dummy.getPersistentData().getString(ROLE))
            || !TrainingGround.inside(p) || p.distanceToSqr(dummy) > 64) {
            TARGETS.remove(p.getUUID());
            return;
        }
        var data = SpecialistTrainingData.get(p.server);
        var course = InstructorCourses.first("counter");
        if (course == null || data.done(p.getUUID(), "counter", course.id())) {
            TARGETS.remove(p.getUUID());
            return;
        }
        var target = TARGETS.computeIfAbsent(p.getUUID(), k -> {
            var fresh = new Target(course);
            seed(fresh, p);
            return fresh;
        });
        var state = WarriorComboService.state(p.getUUID());
        for (var skill : WarriorCombos.all()) {
            long ready = state.cooldowns.getOrDefault(skill.id(), 0L);
            long seen = target.seen.getOrDefault(skill.id(), 0L);
            if (ready == seen) continue;
            target.seen.put(skill.id(), ready);
            if (ready <= seen || !course.matches(skill)) continue;
            // 背对着陪练打空三套也算"练过连招"就太便宜了：要求朝向它（夹角 60° 内）。
            var toward = dummy.position().add(0, 1, 0).subtract(p.position());
            boolean facing = toward.lengthSqr() < 1e-4 || p.getLookAngle().dot(toward.normalize()) > 0.5;
            if (!facing) {
                if (now % 40 == 0) p.displayClientMessage(Component.literal("陪练：转过来对着我打。"), true);
                return;
            }
            target.progress++;
            p.displayClientMessage(Component.literal("陪练 · 连招靶 "
                + course.progressText(target.progress) + " " + skill.name()), true);
            if (target.progress >= course.goal()) {
                data.graduate(p.getUUID(), "counter", course.id());
                TARGETS.remove(p.getUUID());
                p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 12, .4, .6, .4, 0);
                p.sendSystemMessage(Component.literal("演武陪练：这三套打得干净，"
                    + course.title() + " 记下了。"));
            }
            return;
        }
    }

    private static InstructorEntity tutor(ServerPlayer p, UUID id) {
        for (var tutor : trainers(p)) {
            if (tutor.getUUID().equals(id) && !tutor.isRemoved()) return tutor;
        }
        return null;
    }

    private static long now(ServerPlayer p) {
        return p.server.overworld().getGameTime();
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        NPCS.clear();
        LESSONS.clear();
        TARGETS.clear();
    }
}
