package com.tnc.tnc.world.stonecrest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Explicit dev-only GameTests, not run during ordinary gameplay. */
@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class BuildingGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=400)
    public static void structureTemplatesLoadOnRealServer(GameTestHelper helper) throws Exception {
        for (String name:new String[]{"roadside_ruin","fantasy_tavern","gothic_castle","dark_fantasy_castle","abyss_citadel"}) {
            try (var stream=BuildingGameTests.class.getResourceAsStream("/data/tnc/buildings/"+name+".json")) {
                if (stream==null) throw new IllegalStateException(name);
                var root=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),JsonObject.class);
                for (var e:root.getAsJsonArray("pieces")) {
                    var p=e.getAsJsonObject();
                    var id=ResourceLocation.tryParse(p.get("resource").getAsString());
                    var template=helper.getLevel().getStructureManager().get(id).orElseThrow();
                    if (template.getSize().getY()<1) throw new IllegalStateException("Empty "+id);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template="building_test_empty",timeoutTicks=12000)
    public static void abyssTicketsMakeProgressWithoutBlocking(GameTestHelper helper) {
        // Dedicated GameTest world only. Progress assertion stops before excavation.
        var level=helper.getLevel();
        var origin=new BlockPos(2048,-64,2048);
        AbyssCitadelJobs.request(level,origin);
        helper.succeedWhen(()->{
            var j=AbyssCitadelData.get(level).jobs.get(origin.asLong());
            if (j==null || j.tile<32) throw new net.minecraft.gametest.framework.GameTestAssertException(
                    "Waiting for nonblocking chunk preflight: "+(j==null ? "not queued" : "tile="+j.tile+" phase="+j.phase+" "+j.error));
            j.phase=-1; j.error="GameTest stopped before excavation";
            AbyssCitadelData.get(level).setDirty();
        });
    }
}
