package com.tnc.tnc.inkshader;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.util.Optional;

/** Own tiny files only. Oculus' settings are changed through its API, never by rewriting arbitrary properties. */
public final class InkShaderFiles implements InkShaderSession.Journal {
    private final Path policy,journal;
    public InkShaderFiles(Path config){policy=config.resolve("tnc-ink-shader.json");journal=config.resolve("tnc-ink-shader-session.json");}
    public InkShaderSession.Mode mode() throws IOException {
        if(!Files.exists(policy))return InkShaderSession.Mode.OFF;
        var json=read(policy);String mode=json.has("mode")?json.get("mode").getAsString():"off";
        return mode.equalsIgnoreCase("ink")?InkShaderSession.Mode.INK:InkShaderSession.Mode.OFF;
    }
    public void mode(InkShaderSession.Mode mode) throws IOException {var json=new JsonObject();json.addProperty("mode",mode.name().toLowerCase(java.util.Locale.ROOT));write(policy,json);}
    public Optional<InkShaderSession.State> saved() throws IOException {
        if(!Files.exists(journal))return Optional.empty();var json=read(journal);
        if(!json.has("version")||json.get("version").getAsInt()!=1||!json.has("enabled"))throw new IOException("Invalid ink shader recovery record");
        String pack=json.has("pack")&&!json.get("pack").isJsonNull()?json.get("pack").getAsString():null;
        return Optional.of(new InkShaderSession.State(json.get("enabled").getAsBoolean(),pack));
    }
    @Override public void save(InkShaderSession.State state) throws IOException {
        var json=new JsonObject();json.addProperty("version",1);json.addProperty("enabled",state.enabled());json.addProperty("pack",state.pack());write(journal,json);
    }
    @Override public void clear() throws IOException {Files.deleteIfExists(journal);}
    public boolean journalExists(){return Files.exists(journal);}
    private static JsonObject read(Path path) throws IOException {
        if(Files.size(path)>16384)throw new IOException("Ink shader config exceeds its size limit");
        try{return JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();}
        catch(RuntimeException e){throw new IOException("Cannot read "+path.getFileName(),e);}
    }
    private static void write(Path path,JsonObject json) throws IOException {
        Files.createDirectories(path.getParent());Path pending=path.resolveSibling(path.getFileName()+".pending");
        Files.writeString(pending,new GsonBuilder().setPrettyPrinting().create().toJson(json),StandardCharsets.UTF_8);
        try{Files.move(pending,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(pending,path,StandardCopyOption.REPLACE_EXISTING);}
    }
}
