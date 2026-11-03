package com.tnc.tnc.warrior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * 教官课程进度是随 {@link TemperingPacket} 一起下发的两条字符串。
 *
 * <p>协议里多一个字段最容易出的错不是"显示不对"，而是<b>两边长度不一致</b>：编码写了、
 * 解码漏读，或者解码多读一次，客户端就会把后面的字节读串，表现是"面板数字乱跳"，
 * 而不是报错。所以这里只钉三件事：写了能读回来、老构造器不下发脏数据、真实最长的那条放得下。
 */
class TemperingPacketCourseTest {

    private static TemperingPacket sample(String courses, String course) {
        return new TemperingPacket(true, 120, 3, false, false, 2, 0, 40, false, true,
            1, 5, 90, 12, "blade", courses, course);
    }

    private static TemperingPacket roundTrip(TemperingPacket packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(buffer);
        TemperingPacket decoded = TemperingPacket.decode(buffer);
        assertEquals(0, buffer.readableBytes(), "解码后不应留下没读的字节");
        return decoded;
    }

    @Test
    void courseTextSurvivesTheRoundTrip() {
        TemperingPacket packet = sample("guard:1/3|blade:0/4|wind:2/3|counter:0/1", "第三课 · 旋转斩击 1/2");
        TemperingPacket decoded = roundTrip(packet);
        assertEquals(packet.courses(), decoded.courses());
        assertEquals(packet.course(), decoded.course());
        assertEquals("blade", decoded.role());
        assertEquals(5, decoded.completions());
        assertEquals(1, decoded.mode());
    }

    @Test
    void shortConstructorsLeaveTheCourseTextEmpty() {
        TemperingPacket decoded = roundTrip(new TemperingPacket(true, 0, 2));
        assertEquals("", decoded.courses());
        assertEquals("", decoded.course());
        assertEquals("", decoded.role());
    }

    @Test
    void theLongestRealCourseLineFitsTheField() {
        // coursesLine 的形状是 role:done/total 用 | 连接，角色数由 InstructorCourses 决定。
        int worst = InstructorCourses.roles().length * 14;
        assertTrue(worst < 96, "课程进度串会超过 96 字符上限：" + worst);
    }

    @Test
    void everyCourseProgressTextFitsTheField() {
        for (String role : InstructorCourses.roles()) {
            for (InstructorCourses.Course course : InstructorCourses.of(role)) {
                String text = course.progressText(99) + "（复习）";
                assertTrue(text.length() < 48, role + "/" + course.id() + " 的进度串太长：" + text);
            }
        }
    }
}
