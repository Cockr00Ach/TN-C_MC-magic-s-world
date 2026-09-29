package com.tnc.tnc.adventure;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ServicePanelTest {
    @Test void eachClickedWorkerSelectsTheirOwnScreenAndUnknownRolesRemainPersonal(){
        assertEquals(ServicePanel.BANK,ServicePanel.fromRole("broker"));
        assertEquals(ServicePanel.WANDS,ServicePanel.fromRole("smith"));
        assertEquals(ServicePanel.ARMOR,ServicePanel.fromRole("armorer"));
        assertEquals(ServicePanel.GUILD,ServicePanel.fromRole("guild"));
        assertEquals(ServicePanel.PROFILE,ServicePanel.fromRole("profile"));
        assertEquals(ServicePanel.PROFILE,ServicePanel.fromRole("self"));
        assertEquals(ServicePanel.PROFILE,ServicePanel.fromRole(null));
    }
}
