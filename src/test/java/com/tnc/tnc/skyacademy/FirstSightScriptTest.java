package com.tnc.tnc.skyacademy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FirstSightScriptTest {
    @Test void musicStartsBeforeTheInvitationAndLeavesRoomForPlayerResponse(){
        assertEquals(580,FirstSightScript.duration("旅人"));
        double invitation=FirstSightScript.duration("旅人")/20.;
        assertEquals(35,invitation+3+3,.001);
    }
    @Test void twoSeparateCardsHaveAnEmptyBeatBetweenThem(){
        assertEquals("",FirstSightScript.blackoutCaption(0));
        assertEquals("那就",FirstSightScript.blackoutCaption(.5));
        assertEquals("",FirstSightScript.blackoutCaption(1.1));
        assertEquals("让我好好看看这个世界吧",FirstSightScript.blackoutCaption(2));
        assertEquals("",FirstSightScript.blackoutCaption(3));
    }
    @Test void customNameIsSpokenAndFinalLineStaysInBounds(){
        assertTrue(FirstSightScript.lines("星河").get(0).text().contains("星河"));
        assertEquals(FirstSightScript.lines("星河").size()-1,FirstSightScript.lineAt("星河",10000));
        assertEquals(0,FirstSightScript.lineAt("星河",0));
    }
}
