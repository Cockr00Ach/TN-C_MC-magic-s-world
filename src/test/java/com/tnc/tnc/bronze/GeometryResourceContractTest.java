package com.tnc.tnc.bronze;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** AzureLib/GeckoLib discover every JSON below geo, even assets we load ourselves. */
class GeometryResourceContractTest {
    @Test void autoDiscoveredGeoFilesContainNonEmptyBedrockGeometry() throws Exception {
        int count=0;
        try(var namespaces=Files.list(Path.of("src/main/resources/assets"))) {
            for(var namespace:namespaces.filter(Files::isDirectory).toList()) {
                var geo=namespace.resolve("geo");
                if(!Files.isDirectory(geo))continue;
                try(var paths=Files.walk(geo)) {
                    for(var path:paths.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".json")).toList()) {
                        count++;
                        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
                            var document=JsonParser.parseReader(reader).getAsJsonObject();
                            assertTrue(document.has("minecraft:geometry")&&document.get("minecraft:geometry").isJsonArray(),
                                path+": custom metadata must live outside the auto-scanned geo directory");
                            var models=document.getAsJsonArray("minecraft:geometry");
                            assertFalse(models.isEmpty(),path+": AzureLib reads geometry[0]");
                            for(var raw:models) {
                                var model=raw.getAsJsonObject();
                                assertTrue(model.has("description"),path+": missing model description");
                                assertTrue(model.has("bones")&&model.get("bones").isJsonArray(),path+": missing model bones");
                            }
                        }
                    }
                }
            }
        }
        assertTrue(count>0,"Expected actual packaged geometry resources");
    }
}
