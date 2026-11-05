package com.tnc.tnc.warrior;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 教官的课程表：一位教官一套课，按顺序开，做完一课才开下一课。
 *
 * <p>为什么要分成"课"而不是像以前那样一位教官一节课：连招系统上线后，
 * 同一位教官手里的东西已经不止一件事可教（洛克既教举盾又教振刀和破架），
 * 一节课塞进去就变成"打满五下再说"，学员学不到顺序。分成课后：
 *
 * <ul>
 *   <li><b>每节课只考一件事</b>，判定和提示都能说人话（"再退远一些"）；</li>
 *   <li><b>进度记在课上</b>（{@code role/courseId}），出师之后不会因为再练一次就重置；</li>
 *   <li><b>第一课刻意保持与旧版完全一致</b>——老存档里"已完成 N 次"的含义不变，
 *       旧的服务端测试也照样能跑（见 {@code InstructorCoursesTest}）。</li>
 * </ul>
 *
 * <p>课程里的 {@link Course#skillId()} 指向 {@link WarriorCombos} 里的连招 id，
 * 不是另抄一份连招表：连招改名字、改门槛、改顺序，这里跟着变，不会对不上。
 */
public final class InstructorCourses {

    /** 这门课怎么算"做到了"。 */
    public enum Kind {
        /** 用盾挡下教官的一次挥击。 */
        SHIELD_BLOCK,
        /** 攻击指示器蓄满（≥85%）后命中教官。 */
        CHARGED_HIT,
        /** 三格外风刃命中。 */
        WIND_RANGED,
        /** 放出任意一条架势招（振刀 / 壁立 / 沉渊 / 横栏）。 */
        STANCE,
        /** 放出指定的一条连招。 */
        COMBO,
        /** 放出任意一条连招（陪练当靶子时用）。 */
        ANY_COMBO;

        /** 靠"轮询连招冷却"来判定，而不是靠事件。 */
        public boolean pollsCasts() {
            return this == STANCE || this == COMBO || this == ANY_COMBO;
        }
    }

    /**
     * 一节课。
     *
     * @param id      课程 id，存档键的一半（{@code role/id}），改名会让进度归零，别动
     * @param role    教官角色：guard / blade / wind / counter
     * @param title   列表和头顶显示用的短标题，含课序号
     * @param brief   开课时教官说的那句要求
     * @param goal    要做到几次
     * @param kind    判定方式
     * @param skillId COMBO 课要求的连招 id；其余为空串
     */
    public record Course(String id, String role, String title, String brief, int goal, Kind kind, String skillId) {

        public String brief() {
            if (kind == Kind.STANCE) return "统一第二槽：E Q 后长按 E，架住攻击，完成两次振刀架势。";
            if (kind == Kind.COMBO) {
                var s=WarriorCombos.byId(skillId);
                if(s!=null)return "持"+s.family().cn()+"按 "+s.compactLabel()+"，放出 "+s.name()+"。";
            }
            return brief;
        }

        public Course {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("course id is required");
            if (role == null || role.isBlank()) throw new IllegalArgumentException("course role is required: " + id);
            if (title == null || title.isBlank()) throw new IllegalArgumentException("course title is required: " + id);
            if (brief == null || brief.isBlank()) throw new IllegalArgumentException("course brief is required: " + id);
            if (goal <= 0) throw new IllegalArgumentException("course goal must be positive: " + id);
            if (kind == null) throw new IllegalArgumentException("course kind is required: " + id);
            skillId = skillId == null ? "" : skillId;
            if (kind == Kind.COMBO && skillId.isBlank()) {
                throw new IllegalArgumentException("COMBO course needs a skill id: " + id);
            }
            if (kind != Kind.COMBO && !skillId.isBlank()) {
                throw new IllegalArgumentException("only COMBO courses carry a skill id: " + id);
            }
        }

        public boolean pollsCasts() {
            return kind.pollsCasts();
        }

        /** 这一下连招算不算这节的进度。 */
        public boolean matches(WarriorSkill skill) {
            if (skill == null) return false;
            return switch (kind) {
                case STANCE -> skill.role() == WarriorComboRole.STANCE;
                case COMBO -> skill.id().equals(skillId);
                case ANY_COMBO -> true;
                default -> false;
            };
        }

        /** 课里提到的那条连招；ANY_COMBO / 非连招课返回 null。 */
        public WarriorSkill skill() {
            return kind == Kind.COMBO ? WarriorCombos.byId(skillId) : null;
        }

        /** 头顶/提示里的进度文本。 */
        public String progressText(int done) {
            return title + " " + Math.max(0, Math.min(done, goal)) + "/" + goal;
        }
    }

    private static final Map<String, List<Course>> BY_ROLE;

    static {
        Map<String, List<Course>> map = new LinkedHashMap<>();

        // 洛克：盾。举盾 → 振刀（守）→ 破架（重剑铁壁）。三课正好是"防守三件套"。
        map.put("guard", List.of(
            new Course("guard_block", "guard", "第一课 · 盾挡",
                "面向我，举盾挡下五次挥击。看到我抬手就往我这边压半步。",
                5, Kind.SHIELD_BLOCK, ""),
            new Course("guard_stance", "guard", "第二课 · 振刀",
                "右斩、左斩，最后一下按住右斩半秒（E Q E·长按），架出振刀两次。",
                2, Kind.STANCE, ""),
            new Course("guard_breaker", "guard", "第三课 · 铁壁",
                "换重剑，按 E Q Z 放出一次铁壁：它专破振刀。",
                1, Kind.COMBO, "greatsword_combo_3")));

        // 莉娅：长剑。蓄满 → 三课连招，按段数从短到长，正好是玩家自己会摸到的顺序。
        map.put("blade", List.of(
            new Course("blade_charged", "blade", "第一课 · 蓄满",
                "等攻击指示器蓄满，再持剑命中我五次。急着挥不算。",
                5, Kind.CHARGED_HIT, ""),
            new Course("blade_break", "blade", "第二课 · 破锋式",
                "按 Q Z E，放出两次破锋式。三段，别多按。",
                2, Kind.COMBO, "sword_combo_1"),
            new Course("blade_spin", "blade", "第三课 · 旋转斩击",
                "按 Q E Q Z Z Z，放出两次旋转斩击。它会先突进再扫一圈。",
                2, Kind.COMBO, "sword_combo_2"),
            new Course("blade_reverse", "blade", "第四课 · 逆鳞斩",
                "按 Z Z Q Q E E，放出一次逆鳞斩。六段，最难的一条。",
                1, Kind.COMBO, "sword_combo_3")));

        // 岚：风。风刃 → 长弓两课（同是"远程元素打击"，弓是最接近风刃的武器）。
        map.put("wind", List.of(
            new Course("wind_blade", "wind", "第一课 · 风刃",
                "退到至少三格外，面向我，持风灌注武器潜行对空右键：三次风刃命中。",
                3, Kind.WIND_RANGED, ""),
            new Course("wind_pierce", "wind", "第二课 · 穿云箭",
                "换长弓，按 Q Z 放出两次穿云箭。它不耗背包里的箭。",
                2, Kind.COMBO, "bow_combo_1"),
            new Course("wind_leap", "wind", "第三课 · 后跃射",
                "持长弓按 Z E 放一次后跃射：先吃半秒无敌，再往后弹开。",
                1, Kind.COMBO, "bow_combo_3")));

        // 陪练：只有一件事——当连招靶。它不纠正你，只负责挨打和计数。
        map.put("counter", List.of(
            new Course("counter_combo", "counter", "连招靶 · 任意连招",
                "在我身边打满三套连招，哪一套都行。",
                3, Kind.ANY_COMBO, "")));

        BY_ROLE = Map.copyOf(map);
    }

    private InstructorCourses() {
    }

    /** 有课程的角色（counter 也有，只是它的课不通过 {@code begin} 开）。 */
    public static String[] roles() {
        return new String[]{"guard", "blade", "wind", "counter"};
    }

    public static boolean known(String role) {
        return role != null && BY_ROLE.containsKey(role);
    }

    public static List<Course> of(String role) {
        return BY_ROLE.getOrDefault(role, List.of());
    }

    public static Course course(String role, String id) {
        for (Course course : of(role)) {
            if (course.id().equals(id)) return course;
        }
        return null;
    }

    public static Course first(String role) {
        List<Course> list = of(role);
        return list.isEmpty() ? null : list.get(0);
    }

    public static Course last(String role) {
        List<Course> list = of(role);
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    /**
     * 下一节该上的课；全上完了返回 {@code null}（调用方据此开"复习"）。
     *
     * @param completed 这门课是否已出师
     */
    public static Course next(String role, Predicate<Course> completed) {
        for (Course course : of(role)) {
            if (!completed.test(course)) return course;
        }
        return null;
    }

    /**
     * 哪节课教这条连招；没有教官教就返回 {@code null}。
     *
     * <p>图鉴里"这条找谁练"一直只写到教官名，可一位教官手下有好几节课，玩家回到岛上还得再猜。
     * 这里给出具体的课（课名里带序号），图鉴就能直接说"莉娅的第三课"。
     */
    public static Course teaching(String skillId) {
        if (skillId == null || skillId.isBlank()) return null;
        for (List<Course> list : BY_ROLE.values()) {
            for (Course course : list) {
                if (skillId.equals(course.skillId())) return course;
            }
        }
        return null;
    }

    public static int count(String role) {
        return of(role).size();
    }

    /**
     * 复习局上哪一节：按次数轮流，四节课轮着来。
     *
     * <p>以前复习永远重开<b>最后一课</b>，可台词却说"想复习哪一节就练哪一节"——
     * 玩家其实点不到别的课。终端又不能选课（那是另一件事），所以最省事又不说谎的做法是
     * 让他们轮着过一遍：每完成一次复习，次数 +1，下次就换下一节。
     */
    public static Course review(String role, long seed) {
        List<Course> list = of(role);
        if (list.isEmpty()) return null;
        return list.get((int) Math.floorMod(seed, list.size()));
    }

    /** 已出师的课数，用于"出师 2/3"这类显示。 */
    public static int doneCount(String role, Predicate<Course> completed) {
        int n = 0;
        for (Course course : of(role)) {
            if (completed.test(course)) n++;
        }
        return n;
    }
}
