package com.tnc.tnc.dialogue;
import java.io.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DialogueDirectiveTest {
    private DialogueScript parse(String text) throws Exception {
        var method=DialogueLoader.class.getDeclaredMethod("parse",ResourceLocation.class,BufferedReader.class);method.setAccessible(true);
        try{return (DialogueScript)method.invoke(null,ResourceLocation.parse("tnc:test"),new BufferedReader(new StringReader(text)));}
        catch(java.lang.reflect.InvocationTargetException e){throw (Exception)e.getCause();}
    }
    @Test void activateDoesNotCollideWithActAndTabsAreAccepted() throws Exception {
        var script=parse("@activate\ttnc:main/s1_self\n@act wave\nSelf|你好\n");
        assertEquals(ResourceLocation.parse("tnc:main/s1_self"),script.activate());assertEquals("wave",script.lines().get(0).action());
    }
    @Test void unknownActPrefixIsNotAcceptedAsAction() {
        assertThrows(IllegalStateException.class,()->parse("@action wave\nSelf|你好\n"));
    }
}
