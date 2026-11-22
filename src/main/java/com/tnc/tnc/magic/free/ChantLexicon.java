package com.tnc.tnc.magic.free;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authored invocation families: synonyms enrich the chant without multiplying one blessing. */
public final class ChantLexicon {
    public record Family(String name, int discoveryStage, List<String> words) {}
    private static final List<Family> FAMILIES = load();
    public static List<Family> families() { return FAMILIES; }
    public static int power(String text) {
        int power=0;
        for (var family:FAMILIES) if (family.words().stream().anyMatch(text::contains)) power++;
        return Math.min(5,power);
    }
    private static List<Family> load() {
        var resource=ChantLexicon.class.getResourceAsStream("/data/tnc/free_magic/water_chants.tsv");
        if(resource==null)throw new IllegalStateException("Missing water invocation lexicon");
        try(var reader=new BufferedReader(new InputStreamReader(resource,StandardCharsets.UTF_8))){
            return reader.lines().filter(s->!s.isBlank()&&!s.startsWith("#")).map(s->{
                String[] row=s.split("\t");
                return new Family(row[0],Integer.parseInt(row[1]),List.of(row[2].split(",")));
            }).toList();
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    private ChantLexicon(){}
}
