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
    @Test void everyPackagedDialogueLoadsWithItsFullRelativeId() throws Exception {
        var root=java.nio.file.Path.of("src/main/resources/data/tnc/dialogues");
        var method=DialogueLoader.class.getDeclaredMethod("parse",ResourceLocation.class,BufferedReader.class);
        method.setAccessible(true);
        try (var paths=java.nio.file.Files.walk(root)) {
            for (var path:paths.filter(p->p.toString().endsWith(".txt")).toList()) {
                String relative=root.relativize(path).toString().replace('\\','/');
                var id=ResourceLocation.fromNamespaceAndPath("tnc",relative.substring(0,relative.length()-4));
                try (var reader=java.nio.file.Files.newBufferedReader(path,java.nio.charset.StandardCharsets.UTF_8)) {
                    var script=(DialogueScript)method.invoke(null,id,reader);
                    assertEquals(id,script.id(),path.toString());
                    assertFalse(script.lines().isEmpty(),path.toString());
                }
            }
        }
    }
}
