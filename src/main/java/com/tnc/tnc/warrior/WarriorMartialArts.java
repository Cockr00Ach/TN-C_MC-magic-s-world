package com.tnc.tnc.warrior;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.LinkedHashMap;

/** Shared names, hit sectors and clip identity; stable catalog IDs retain progression and cooldowns. */
public final class WarriorMartialArts {
    private static final Map<String,JsonObject> PROFILES=load();
    private WarriorMartialArts() {}
    private static Map<String,JsonObject> load() {
        String path="assets/tnc/warrior/traditional_martial_arts.json";
        try {
            InputStream stream=WarriorMartialArts.class.getResourceAsStream("/"+path);
            if(stream==null) {
                var container=net.minecraftforge.fml.ModList.get().getModContainerById("tnc").orElseThrow();
                stream=java.nio.file.Files.newInputStream(container.getModInfo().getOwningFile().getFile().findResource(path));
            }
            try(var input=stream) {
                var json=JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                Map<String,JsonObject> out=new LinkedHashMap<>();
                json.entrySet().forEach(e->out.put(e.getKey(),e.getValue().getAsJsonObject()));
                return Map.copyOf(out);
            }
        } catch(Exception error) {throw new IllegalStateException("Cannot load traditional martial arts",error);}
    }
    private static String local(String id) {
        if(id==null || !id.startsWith("tnc:"))return "";
        return id.substring(4);
    }
    public static boolean supports(String id){return PROFILES.containsKey(local(id));}
    public static java.util.Set<String> ids(){return PROFILES.keySet();}
    public static WarriorWeaponFamily family(String id) {
        var p=PROFILES.get(local(id));
        return p==null?null:WarriorWeaponFamily.valueOf(p.get("family").getAsString());
    }
    public static WarriorSkill adapt(WarriorSkill s,String id) {
        if(s==null)return null;
        var p=PROFILES.get(local(id));
        if(p==null || family(id)!=s.family())return s;
        var moves=p.getAsJsonArray("moves");
        if(s.slot()>=moves.size())return s;
        var move=moves.get(s.slot()).getAsJsonObject();
        return new WarriorSkill(s.id(),s.codexId(),move.get("name").getAsString(),s.sequence(),s.slot(),s.cooldownTicks(),
                s.damageMultiplier(),move.get("radius").getAsDouble(),s.slot()==1?120:s.slot()==2?100:move.get("arc").getAsInt(),
                s.knockback(),s.durabilityCost(),move.get("text").getAsString(),s.family(),s.chain(),
                s.slot()==0 || s.backsteps()?0:move.get("dash").getAsDouble(),s.ranged(),s.interrupt(),s.staggerTicks(),s.animation(),
                s.holds(),s.guardTicks(),s.evadeTicks(),s.backstep());
    }
    public static String clipKey(WarriorAnimation animation,String id) {
        var family=family(id);
        if(family==null || animation==null)return null;
        String basic=switch(animation){case SLASH_LEFT->"q";case SLASH_RIGHT->"e";case SLASH_HORIZONTAL->"z";default->null;};
        if(basic!=null)return local(id)+"_basic_"+basic;
        for(var s:WarriorCombos.forFamily(family)) {
            if(s.animation()==animation)return local(id)+"_"+s.slot();
        }
        return null;
    }
}
