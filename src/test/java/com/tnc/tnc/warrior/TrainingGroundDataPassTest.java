package com.tnc.tnc.warrior;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 调试指令 {@code /tempering complete} 的数据层：一次把三档练习都写成"已通过"。
 *
 * <p>这里的断言盯的是"补完记录后图鉴能读到通过状态"，不是命令解析 —— 命令本身
 * 只是把这三个数字塞进台账。
 */
class TrainingGroundDataPassTest {

    @Test
    void passOverwritesTheRecordInsteadOfIncrementing() {
        var data = new TrainingGroundData();
        UUID id = UUID.randomUUID();
        data.pass(id, 0, 99, 100, 80);
        var record = data.record(id);
        assertEquals(99, record.completions());
        assertEquals(100, record.score());
        assertEquals(80, record.reaction());

        // 再补一次是覆盖，不是累加：指令应当可重复执行
        data.pass(id, 0, 3, 90, 40);
        assertEquals(3, data.record(id).completions());
    }

    @Test
    void allThreeDifficultiesCanBeMarkedPassed() {
        var data = new TrainingGroundData();
        UUID id = UUID.randomUUID();
        for (int mode = 0; mode < 3; mode++) {
            data.pass(id, mode, 99, 100, 80);
        }
        // record 只读当前 Mode；轮换三档，每一档都应读到自己那份"已通过"
        for (int mode = 0; mode < 3; mode++) {
            var record = data.record(id);
            assertEquals(99, record.completions(), "第 " + mode + " 档应已通过");
            assertEquals(100, record.score());
            data.cycle(id);
        }
    }
}
