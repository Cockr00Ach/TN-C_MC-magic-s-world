package com.tnc.tnc.world.ink;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class InkBossAudioResourcesTest {
    private static final Path ROOT=Path.of("src/main/resources/assets/tnc");
    @Test void threeFullSongsShareMeasuredLoudnessAndStreamingEvents()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(ROOT.resolve("sounds/music/ink/crane.ogg")),"Author-supplied songs are distributed locally");
        var report=JsonParser.parseString(Files.readString(ROOT.resolve("ink_boss_audio.json"))).getAsJsonObject();
        assertEquals(-18,report.get("targetLUFS").getAsInt());assertEquals(3,report.getAsJsonArray("songs").size());
        var sounds=JsonParser.parseString(Files.readString(ROOT.resolve("sounds.json"))).getAsJsonObject();
        int phase=0;for(var raw:report.getAsJsonArray("songs")){
            var song=raw.getAsJsonObject();assertEquals(++phase,song.get("phase").getAsInt());
            assertTrue(song.get("duration").getAsDouble()>90,"Keep the complete song, not a short sample");
            assertEquals(-18,song.get("outputLUFS").getAsDouble(),.5);assertTrue(song.get("truePeakDBTP").getAsDouble()<=-1);
            var event=sounds.getAsJsonObject(song.get("event").getAsString().substring(4));
            var sound=event.getAsJsonArray("sounds").get(0).getAsJsonObject();assertTrue(sound.get("stream").getAsBoolean());
            var bytes=Files.readAllBytes(ROOT.resolve("sounds/"+sound.get("name").getAsString().substring(4)+".ogg"));
            assertEquals("OggS",new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII));
        }
    }
    @Test void everyBossHasARealNormalizedCastClipAndCredits()throws Exception{
        var report=JsonParser.parseString(Files.readString(ROOT.resolve("ink_boss_audio.json"))).getAsJsonObject();
        assertEquals(3,report.getAsJsonArray("roars").size());
        var credits=JsonParser.parseString(Files.readString(ROOT.resolve("ink_audio_credits.json"))).getAsJsonObject();
        for(String slug:new String[]{"crane","tiger","dragon"}){
            assertTrue(Files.size(ROOT.resolve("sounds/ink/"+slug+"_cast.ogg"))>1000);
            assertEquals("CC0-1.0",credits.getAsJsonObject(slug+"_cast").get("license").getAsString());
        }
    }
}
