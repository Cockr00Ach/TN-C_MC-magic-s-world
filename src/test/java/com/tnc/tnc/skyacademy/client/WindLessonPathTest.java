package com.tnc.tnc.skyacademy.client;

import com.tnc.tnc.skyacademy.AcademyRules;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindLessonPathTest {
    private final WindLessonPath path = new WindLessonPath(new BlockPos[] {
            new BlockPos(55,219,-158), new BlockPos(62,219,-162), new BlockPos(69,219,-158)});

    @Test void audibleCuesCoincideWithTheCorrectBellMouths() {
        for (int step=0; step<3; step++)
            assertEquals(0, path.wind(WindLessonPath.STRIKES[step])
                    .distanceTo(path.bell(AcademyRules.runeAtStep(step))), 1.e-7);
    }

    @Test void closeupHoldsTheMovingHeadInsideTheLensAndStaysInFrontOfTheBell() {
        for (double t=0; t<=WindLessonPath.LENGTH; t+=.25) {
            var shot=path.shot(t);
            var axis=shot.subject().subtract(shot.eye()).normalize();
            var toHead=path.wind(t).subtract(shot.eye()).normalize();
            assertTrue(axis.dot(toHead)>Math.cos(Math.toRadians(22)), "off-screen at tick "+t);
            assertTrue(shot.eye().z-shot.subject().z>=2.6);
            assertTrue(shot.clipStart().z-shot.subject().z>=1);
        }
    }
}
