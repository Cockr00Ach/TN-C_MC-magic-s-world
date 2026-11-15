package com.tnc.tnc.warrior;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.renderer.block.model.BlockModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the same deserializer that rejected the four detailed weapons in the client log. */
class WarriorWeaponModelValidityTest {
    @Test void everyAuthoredWarriorWeaponIsAcceptedByVanilla() throws Exception {
        Path models=Path.of("src/main/resources/assets/tnc/models/item");
        Set<String> checked=new HashSet<>();
        try(var paths=Files.list(models)){
            for(var path:paths.filter(p->p.toString().endsWith(".json")).toList()){
                var raw=JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
                if(!raw.has("tnc_grip"))continue;
                try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
                    var model=BlockModel.fromStream(reader);
                    assertFalse(model.getElements().isEmpty(),path+" has no visible weapon geometry");
                    for(var element:model.getElements())
                        assertTrue(element.faces.size()>=1&&element.faces.size()<=6,
                                path+" contains an element rejected by vanilla");
                }
                checked.add(path.getFileName().toString());
            }
        }
        assertTrue(checked.containsAll(Set.of("dragon_sword.json","crimson_warblade.json",
                "doom_scythe.json","sky_halberd.json")),"All four previously missing weapons must be validated");
        assertTrue(checked.size()>=7,"Validate the complete authored warrior batch");
    }
}
