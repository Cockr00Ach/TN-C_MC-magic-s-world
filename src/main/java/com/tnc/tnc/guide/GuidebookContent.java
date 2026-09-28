package com.tnc.tnc.guide;

import com.google.gson.*;
import java.io.Reader;
import java.util.*;

/** Read-only guide content, independent of client classes and quest progress. */
public record GuidebookContent(List<Page> pages) {
    public record Section(String heading, String text) {}
    public record Illustration(String texture, String title, String description) {}
    public record Page(String id, String title, String subtitle, String status,
                       List<Section> sections, List<Illustration> illustrations) {}

    public static GuidebookContent read(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        List<Page> pages = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement element : root.getAsJsonArray("pages")) {
            JsonObject page = element.getAsJsonObject();
            String id = required(page,"id");
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate guide page: " + id);
            List<Section> sections = new ArrayList<>();
            for (JsonElement item : page.getAsJsonArray("sections")) {
                JsonObject section = item.getAsJsonObject();
                sections.add(new Section(required(section,"heading"),required(section,"text")));
            }
            if (sections.isEmpty()) throw new IllegalArgumentException("Empty guide page: " + id);
            List<Illustration> illustrations = new ArrayList<>();
            if (page.has("illustrations")) for (JsonElement item : page.getAsJsonArray("illustrations")) {
                JsonObject image = item.getAsJsonObject();
                String texture = required(image,"texture");
                if (!texture.matches("tnc:textures/guide/[a-z0-9_]+\\.png"))
                    throw new IllegalArgumentException("Invalid guide illustration: " + texture);
                illustrations.add(new Illustration(texture,required(image,"title"),required(image,"description")));
            }
            pages.add(new Page(id,required(page,"title"),required(page,"subtitle"),required(page,"status"),
                    List.copyOf(sections),List.copyOf(illustrations)));
        }
        if (pages.isEmpty()) throw new IllegalArgumentException("Guide has no pages");
        return new GuidebookContent(List.copyOf(pages));
    }

    private static String required(JsonObject object, String key) {
        String value = object.get(key).getAsString().strip();
        if (value.isEmpty()) throw new IllegalArgumentException("Empty guide field: " + key);
        return value;
    }
}
