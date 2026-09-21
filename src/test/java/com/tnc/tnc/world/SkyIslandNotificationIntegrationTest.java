package com.tnc.tnc.world;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyIslandNotificationIntegrationTest {
    @Test
    void loginCompatibilityFlagIsWrittenBeforeYsjxmodelsNormalPriorityListener() throws Exception {
        Method method = SkyIslandEvents.class.getDeclaredMethod(
                "onPlayerLogin", PlayerEvent.PlayerLoggedInEvent.class);
        SubscribeEvent annotation = method.getAnnotation(SubscribeEvent.class);

        assertNotNull(annotation);
        assertEquals(EventPriority.HIGHEST, annotation.priority());
        assertTrue(classBytes(SkyIslandPlayerNotifications.class)
                .contains("ysjxmodelFirstJoinDialogShown"));
    }

    @Test
    void managerNoLongerContainsPlayerFacingGenerationProgressMessages() throws IOException {
        String bytes = classBytes(SkyIslandManager.class);

        assertFalse(bytes.contains("天空岛生成进度"));
        assertFalse(bytes.contains("仍在生成，进度"));
        assertFalse(bytes.contains("位置勘测完成，开始分批生成"));
    }

    @Test
    void persistedGenerationErrorsRemainVisibleWhenLoginStartsRecovery() throws IOException {
        assertTrue(classBytes(SkyIslandManager.class).contains("天空岛上次生成失败"));
    }

    @Test
    void awakeningPersistsItsResumableSequenceAndUsesBothSoundCues() throws IOException {
        String bytes = classBytes(SkyIslandPlayerNotifications.class);

        assertTrue(bytes.contains("tncSkyIslandAwakeningRemainingTicks"));
        assertTrue(bytes.contains("tncSkyIslandCompletionDelayTicks"));
        assertTrue(bytes.contains("tncSkyIslandImpactDelayTicks"));
        assertTrue(bytes.contains("PORTAL_TRIGGER"));
        assertTrue(bytes.contains("WARDEN_SONIC_BOOM"));
        assertTrue(bytes.contains("saveAll"));
    }

    private static String classBytes(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream stream = type.getResourceAsStream(resource)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
