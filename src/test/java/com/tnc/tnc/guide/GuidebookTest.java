package com.tnc.tnc.guide;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.nio.file.*;
import com.google.gson.*;
import static org.junit.jupiter.api.Assertions.*;

class GuidebookTest {
    private GuidebookContent content() throws IOException {
        var stream=getClass().getResourceAsStream("/assets/tnc/guide/gameplay.json");
        assertNotNull(stream);
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){return GuidebookContent.read(reader);}
    }
    @Test void firstFivePagesPreserveTheRequestedOrderAndAllNineArePresent() throws IOException {
        assertEquals(List.of("world","weapons","growth","ranks","contracts","money","food","home","first_steps"),
                content().pages().stream().map(GuidebookContent.Page::id).toList());
    }
    @Test void conceptsHaveRealTransparentThirtyTwoPixelAssets() throws IOException {
        var images=content().pages().get(1).illustrations();assertEquals(5,images.size());
        assertEquals("傲慢的水龙王",images.get(4).title());
        for(var image:images) {
            String resource="/assets/"+image.texture().replace(":","/");
            try(var stream=getClass().getResourceAsStream(resource)) {
                assertNotNull(stream,resource);var pixels=javax.imageio.ImageIO.read(stream);
                assertEquals(32,pixels.getWidth());assertEquals(32,pixels.getHeight());
                assertEquals(0,pixels.getRGB(0,0)>>>24);
                int visible=0;for(int x=0;x<32;x++)for(int y=0;y<32;y++)if((pixels.getRGB(x,y)>>>24)>0)visible++;
                assertTrue(visible>40&&visible<800,"Recognizable silhouette on a transparent background");
            }
        }
    }
    @Test void unimplementedSystemsAreClearlyLabelledWithoutFakeQuestObjectives() throws IOException {
        for(var page:content().pages()) {
            assertFalse(page.status().isBlank());
            if(!page.id().equals("world"))assertTrue(page.status().contains("筹备")||page.status().contains("逐批开放"));
        }
        String home=content().pages().get(7).sections().toString();
        assertTrue(home.contains("住宅必须由玩家购买"));assertTrue(home.contains("没有家具"));
        assertTrue(home.contains("5银"));
    }
    @Test void duplicatePageIdentifiersAreRejected() {
        String page="{\"id\":\"same\",\"title\":\"t\",\"subtitle\":\"s\",\"status\":\"planned\",\"sections\":[{\"heading\":\"h\",\"text\":\"x\"}]}";
        assertThrows(IllegalArgumentException.class,()->GuidebookContent.read(new StringReader("{\"pages\":["+page+","+page+"]}")));
    }
    @Test void nativeFtbChaptersContainEverySectionAndNoFakeGameplayRewards() throws IOException {
        Set<String> ids=new HashSet<>();
        var pages=content().pages();
        for(int i=0;i<pages.size();i++) {
            var page=pages.get(i);
            Path file=Path.of("questbook","ftbquests","chapters",String.format("tnc_guide_%02d_%s.snbt",i+1,page.id()));
            var chapter=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            assertEquals("544E434755494445",chapter.get("group").getAsString());
            assertEquals(-100+i,chapter.get("order_index").getAsInt());
            assertTrue(ids.add(chapter.get("id").getAsString()));
            var nodes=chapter.getAsJsonArray("quests");
            assertEquals(1+page.sections().size()+page.illustrations().size(),nodes.size());
            Set<String> titles=new HashSet<>();
            for(var value:nodes) {
                var node=value.getAsJsonObject();titles.add(node.get("title").getAsString());
                assertTrue(ids.add(node.get("id").getAsString()));
                assertEquals(0,node.getAsJsonArray("rewards").size());
                assertEquals(0,node.getAsJsonArray("dependencies").size());
                for(var t:node.getAsJsonArray("tasks")) {
                    var task=t.getAsJsonObject();assertEquals("checkmark",task.get("type").getAsString());
                    assertTrue(ids.add(task.get("id").getAsString()));
                    assertTrue(task.get("title").getAsString().contains("阅读"));
                }
            }
            for(var section:page.sections())assertTrue(titles.contains(section.heading()));
            assertEquals(page.illustrations().size(),chapter.getAsJsonArray("images").size());
        }
    }
    @Test void rejectedStandaloneGuideDoesNotInterceptTheStoryOrRegisterAKey() throws IOException {
        Path client=Path.of("src","main","java","com","tnc","tnc","client");
        assertFalse(Files.exists(client.resolve("GuidebookScreen.java")));
        assertFalse(Files.exists(client.resolve("GuidebookClientEvents.java")));
        assertFalse(Files.readString(Path.of("src/main/java/com/tnc/tnc/TNMod.java")).contains("GuidebookClientEvents"));
    }
}
