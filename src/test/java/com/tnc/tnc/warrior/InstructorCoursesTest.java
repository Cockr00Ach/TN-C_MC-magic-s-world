package com.tnc.tnc.warrior;

import com.tnc.tnc.warrior.InstructorCourses.Course;
import com.tnc.tnc.warrior.InstructorCourses.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 教官课程表的不变量。
 *
 * <p>这里钉的不是文案，而是三件会悄悄坏掉的事：
 *
 * <ol>
 *   <li><b>第一课不能变</b>——老存档里"已完成 N 次"记的是它，旧的服务端测试也按它跑；
 *       有人把第一课换成振刀，老进度就全错了。</li>
 *   <li><b>课程 id 不能撞</b>——存档键是 {@code role/courseId}，撞了等于两节课共享进度。</li>
 *   <li><b>连招课必须指向真连招</b>——{@code skillId} 是字符串，写错了只会静默永不达成，
 *       玩家练一辈子也不过关。</li>
 * </ol>
 */
class InstructorCoursesTest {

    @Test
    void everyRoleHasCoursesWithReadableText() {
        for (String role : InstructorCourses.roles()) {
            List<Course> list = InstructorCourses.of(role);
            assertFalse(list.isEmpty(), role + " 至少要有一节课");
            for (Course course : list) {
                assertEquals(role, course.role(), "课程表的角色要和它的位置一致：" + course.id());
                assertFalse(course.title().isBlank(), course.id() + " 缺标题");
                assertFalse(course.brief().isBlank(), course.id() + " 缺要求说明");
                assertTrue(course.goal() > 0, course.id() + " 的目标次数必须为正");
                assertTrue(course.title().contains("课") || course.title().contains("靶"),
                    "标题要让人看出这是第几节：" + course.title());
            }
        }
        assertEquals(4, InstructorCourses.roles().length, "四位教官：洛克/莉娅/岚/陪练");
    }

    @Test
    void courseIdsAreUniqueAcrossAllRoles() {
        Set<String> ids = new HashSet<>();
        for (String role : InstructorCourses.roles()) {
            for (Course course : InstructorCourses.of(role)) {
                assertTrue(ids.add(course.id()), "课程 id 重复（存档键会撞）：" + course.id());
            }
        }
    }

    /** 老存档与旧 GameTest 都按第一课判定，这里把它钉死。 */
    @Test
    void firstCoursesKeepTheLegacyBehaviour() {
        Course guard = InstructorCourses.first("guard");
        assertEquals(Kind.SHIELD_BLOCK, guard.kind());
        assertEquals(5, guard.goal());

        Course blade = InstructorCourses.first("blade");
        assertEquals(Kind.CHARGED_HIT, blade.kind());
        assertEquals(5, blade.goal());

        Course wind = InstructorCourses.first("wind");
        assertEquals(Kind.WIND_RANGED, wind.kind());
        assertEquals(3, wind.goal());

        assertEquals(guard.goal(), SpecialistTrainers.goal("guard"), "goal() 要回落到第一课");
        assertEquals(blade.goal(), SpecialistTrainers.goal("blade"));
        assertEquals(wind.goal(), SpecialistTrainers.goal("wind"));
    }

    @Test
    void comboCoursesPointAtRealCombos() {
        for (String role : InstructorCourses.roles()) {
            for (Course course : InstructorCourses.of(role)) {
                if (course.kind() != Kind.COMBO) {
                    assertTrue(course.skillId().isBlank(), "只有连招课带 skillId：" + course.id());
                    continue;
                }
                WarriorSkill skill = WarriorCombos.byId(course.skillId());
                assertNotNull(skill, course.id() + " 指向了不存在的连招：" + course.skillId());
                assertEquals(skill, course.skill(), "course.skill() 要和 byId 一致");
                // 标题里应出现招式名，玩家才知道自己在练什么
                assertTrue(course.title().contains(skill.name()) || course.brief().contains(skill.name()),
                    course.id() + " 的文案没提到招式名 " + skill.name());
            }
        }
    }

    /** 洛克教盾、莉娅教剑、岚教远——课程用的武器要和教官的人设对得上。 */
    @Test
    void coursesStayWithinEachInstructorsWeaponFamily() {
        for (Course course : InstructorCourses.of("blade")) {
            var skill = course.skill();
            if (skill != null) assertEquals(WarriorWeaponFamily.LONGSWORD, skill.family(), course.id() + " 是长剑教官的课");
        }
        for (Course course : InstructorCourses.of("guard")) {
            var skill = course.skill();
            if (skill != null) assertEquals(WarriorWeaponFamily.GREATSWORD, skill.family(), course.id() + " 是盾卫的破架课");
        }
        for (Course course : InstructorCourses.of("wind")) {
            var skill = course.skill();
            if (skill != null) assertEquals(WarriorWeaponFamily.BOW, skill.family(), course.id() + " 是岚的远程课");
        }
    }

    @Test
    void stanceCourseAcceptsAnyStanceSkill() {
        Course stance = null;
        for (Course course : InstructorCourses.of("guard")) {
            if (course.kind() == Kind.STANCE) stance = course;
        }
        assertNotNull(stance, "洛克要有一节架势课");
        assertTrue(stance.matches(WarriorCombos.byId("sword_combo_4")), "振刀算");
        assertTrue(stance.matches(WarriorCombos.byId("greatsword_combo_4")), "壁立也算");
        assertFalse(stance.matches(WarriorCombos.byId("sword_combo_1")), "破锋式不是架势");
    }

    @Test
    void comboCourseOnlyAcceptsItsOwnSkill() {
        Course breakStyle = InstructorCourses.course("blade", "blade_break");
        assertNotNull(breakStyle);
        assertTrue(breakStyle.matches(WarriorCombos.byId("sword_combo_1")));
        assertFalse(breakStyle.matches(WarriorCombos.byId("sword_combo_2")), "第二课不该被旋转斩击点亮");
        assertFalse(breakStyle.matches(null), "空技能不算");
    }

    @Test
    void counterIsASingleAnyComboTarget() {
        List<Course> counter = InstructorCourses.of("counter");
        assertEquals(1, counter.size(), "陪练只当连招靶，不该排一串课");
        assertEquals(Kind.ANY_COMBO, counter.get(0).kind());
        assertEquals(3, counter.get(0).goal());
        assertTrue(counter.get(0).matches(WarriorCombos.byId("sword_combo_1")));
        assertTrue(counter.get(0).matches(WarriorCombos.byId("bow_combo_1")), "哪一套都算");
    }

    @Test
    void nextAdvancesOnlyPastCompletedCourses() {
        String role = "blade";
        List<Course> list = InstructorCourses.of(role);
        Set<String> done = new HashSet<>();

        assertEquals(list.get(0), InstructorCourses.next(role, c -> done.contains(c.id())));
        for (int i = 0; i < list.size(); i++) {
            Course expected = list.get(i);
            assertEquals(expected, InstructorCourses.next(role, c -> done.contains(c.id())),
                "第 " + (i + 1) + " 节该开了");
            done.add(expected.id());
        }
        assertNull(InstructorCourses.next(role, c -> done.contains(c.id())), "全上完了就该开复习局");
        assertEquals(list.size(), InstructorCourses.doneCount(role, c -> done.contains(c.id())));
    }

    @Test
    void lastIsTheFinalCourse() {
        // 复习不再固定重开最后一课（现在是轮转，见 InstructorSaveTest），这里只钉 last() 本身。
        for (String role : Set.of("guard", "blade", "wind")) {
            assertEquals(InstructorCourses.of(role).get(InstructorCourses.count(role) - 1),
                InstructorCourses.last(role));
        }
    }

    @Test
    void unknownRolesAreEmptyNotBroken() {
        assertFalse(InstructorCourses.known("nobody"));
        assertFalse(InstructorCourses.known(null));
        assertTrue(InstructorCourses.of("nobody").isEmpty());
        assertNull(InstructorCourses.first("nobody"));
        assertNull(InstructorCourses.next("nobody", c -> false));
        assertEquals(0, InstructorCourses.count("nobody"));
    }

    @Test
    void malformedCoursesAreRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class,
            () -> new Course("", "guard", "第一课", "说明", 1, Kind.SHIELD_BLOCK, ""));
        assertThrows(IllegalArgumentException.class,
            () -> new Course("x", "guard", "第一课", "说明", 0, Kind.SHIELD_BLOCK, ""));
        assertThrows(IllegalArgumentException.class,
            () -> new Course("x", "guard", "第一课", "说明", 1, Kind.COMBO, ""));
        assertThrows(IllegalArgumentException.class,
            () -> new Course("x", "guard", "第一课", "说明", 1, Kind.SHIELD_BLOCK, "sword_combo_1"));
    }

    @Test
    void progressTextCountsUpButNeverOverflows() {
        Course course = InstructorCourses.first("guard");
        assertEquals("第一课 · 盾挡 0/5", course.progressText(0));
        assertEquals("第一课 · 盾挡 3/5", course.progressText(3));
        assertEquals("第一课 · 盾挡 5/5", course.progressText(9), "多出来的次数不该溢出显示");
        assertEquals("第一课 · 盾挡 0/5", course.progressText(-1));
    }

    /** 每节课都要能被"数出来"，否则课永远结不了。 */
    @Test
    void everyCourseHasSomebodyCountingIt() {
        for (String role : InstructorCourses.roles()) {
            for (Course course : InstructorCourses.of(role)) {
                switch (course.kind()) {
                    case SHIELD_BLOCK, CHARGED_HIT, WIND_RANGED ->
                        assertFalse(course.pollsCasts(), course.id() + " 走事件判定，不该轮询冷却");
                    case STANCE, COMBO, ANY_COMBO -> {
                        assertTrue(course.pollsCasts(), course.id() + " 走冷却轮询");
                        assertEquals("counter".equals(role), course.kind() == Kind.ANY_COMBO,
                            course.id() + " 的判定方式和人设不符");
                    }
                }
            }
        }
    }

    /** 课程表里不该出现没人教的角色。 */
    @Test
    void everyRoleIsTaughtByAKnownInstructor() {
        List<String> names = new ArrayList<>();
        for (String role : InstructorCourses.roles()) {
            names.add(SpecialistTrainers.name(role));
            assertFalse(SpecialistTrainers.name(role).isBlank(), role + " 没有名字");
        }
        assertEquals(names.size(), new HashSet<>(names).size(), "教官名字不能重复");
    }
}
