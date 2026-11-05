package com.tnc.tnc.warrior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

/**
 * 专项课存档的两条规矩：<b>复习要轮着来</b>、<b>认不出来的课程键要扔掉</b>。
 *
 * <p>复习以前永远是"最后一课"重开，可教官说的是"想复习哪一节就练哪一节"——玩家点不到别的课。
 * 现在按已结课次数轮转，所以这个轮转必须被钉住：{@link InstructorCourses#review} 转错一格，
 * 玩家就会连续两次上同一节课。
 *
 * <p>课程键是 {@code role/courseId}，改名会留下一堆死键。死键不只是占地方：
 * 将来若有新课程恰好用了同一个 id，老进度会被当成"已出师"，玩家直接跳过一课。
 */
class InstructorSaveTest {

    /**
     * 课程 id 就是存档键，**改一个字母等于把练过这一课的人进度清零**。
     *
     * <p>所以这里把 11 个 id 原样钉死：想改名就得先改这个测试，改的人自然会去看存档怎么办
     * （保进度就加一层旧 id → 新 id 的迁移，而不是默默清空）。
     */
    @Test
    void courseIdsArePinnedBecauseTheyAreSaveKeys() {
        List<String> ids = new java.util.ArrayList<>();
        for (String role : InstructorCourses.roles()) {
            for (var course : InstructorCourses.of(role)) {
                ids.add(course.id());
            }
        }
        assertEquals(List.of(
            "guard_block", "guard_stance", "guard_breaker",
            "blade_charged", "blade_break", "blade_spin", "blade_reverse",
            "wind_blade", "wind_pierce", "wind_leap",
            "counter_combo"), ids);
    }

    @Test
    void reviewRotatesThroughEveryCourseAndWrapsAround() {
        for (String role : InstructorCourses.roles()) {
            List<InstructorCourses.Course> courses = InstructorCourses.of(role);
            assertEquals(courses.size(), InstructorCourses.count(role), role + " 的课程数和 of() 对不上");
            for (int index = 0; index < courses.size(); index++) {
                assertEquals(courses.get(index), InstructorCourses.review(role, index),
                    role + " 第 " + index + " 次复习该轮到 " + courses.get(index).title());
            }
            // 一轮之后回到第一节，多转几圈也不能越界
            assertEquals(courses.get(0), InstructorCourses.review(role, courses.size()));
            assertNotNull(InstructorCourses.review(role, courses.size() * 7L + 3));
        }
    }

    @Test
    void reviewToleratesUnknownRoles() {
        assertNull(InstructorCourses.review("nobody", 0));
        assertNull(InstructorCourses.review("", 5));
    }

    @Test
    void everyComboCourseIsFoundByItsSkillAndNoSkillIsTaughtTwice() {
        Map<String, InstructorCourses.Course> taught = new HashMap<>();
        for (String role : InstructorCourses.roles()) {
            for (var course : InstructorCourses.of(role)) {
                if (course.skillId().isEmpty()) {
                    continue;
                }
                assertNull(taught.put(course.skillId(), course),
                    "两条课都教 " + course.skillId() + "：图鉴会指向错的那条");
                assertEquals(course, InstructorCourses.teaching(course.skillId()),
                    "图鉴要按连招找到教它的那一节：" + course.skillId());
            }
        }
        assertFalse(taught.isEmpty(), "一条连招课都没有，课程表坏了");
        assertNull(InstructorCourses.teaching(null));
        assertNull(InstructorCourses.teaching(""));
        assertNull(InstructorCourses.teaching("no_such_combo"));
    }

    @Test
    void deadCourseKeysAreDroppedButCountsSurvive() {
        UUID id = UUID.randomUUID();
        var lessons = new CompoundTag();
        lessons.putInt("guard", 3);
        lessons.putInt("guard/guard_block", 1);
        lessons.putInt("guard/guard_removed", 1);
        lessons.putInt("nobody/nobody_x", 1);
        var record = new CompoundTag();
        record.putUUID("Id", id);
        record.put("Lessons", lessons);
        var records = new ListTag();
        records.add(record);
        var root = new CompoundTag();
        root.put("Records", records);

        var data = SpecialistTrainingData.load(root);

        assertTrue(data.done(id, "guard", "guard_block"), "真课的出师记录不能被扔掉");
        assertEquals(3, data.count(id, "guard"), "总次数不是课程键，必须留下");
        assertFalse(data.done(id, "guard", "guard_removed"), "认不出来的课不该再算已出师");

        String saved = data.save(new CompoundTag()).toString();
        assertFalse(saved.contains("guard_removed"), "死键应该在存档里消失：" + saved);
        assertFalse(saved.contains("nobody_x"), "未知角色的键也一并清掉：" + saved);
        assertTrue(saved.contains("guard_block"), "真课要留着：" + saved);
    }
}
