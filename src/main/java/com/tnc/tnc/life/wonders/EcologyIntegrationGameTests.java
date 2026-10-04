package com.tnc.tnc.life.wonders;

import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class EcologyIntegrationGameTests {
    @GameTest(template="building_test_empty",batch="ecology_integration",timeoutTicks=30)
    public static void manualCreativeKitsReferToActualRegisteredItems(GameTestHelper h)throws Exception{
        var itemPattern=Pattern.compile("run give @s ([a-z_]+:[a-z_]+) ");
        int checked=0;
        for(String kind:new String[]{"plant","animal","workshop"}){
            var id=ResourceLocation.fromNamespaceAndPath("tnc","functions/ecology_"+kind+"_kit.mcfunction");
            var resource=h.getLevel().getServer().getResourceManager().getResource(id).orElseThrow();
            String body;try(var input=resource.open()){body=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
            for(String line:body.split("\\R"))if(!line.startsWith("#")&&line.contains("run give @s ")){
                h.assertTrue(line.startsWith("execute if entity @s[type=minecraft:player,gamemode=creative] "),"Manual gifts remain creative-only");
                var match=itemPattern.matcher(line);h.assertTrue(match.find(),"Valid test kit command");
                var item=ResourceLocation.tryParse(match.group(1));h.assertTrue(ForgeRegistries.ITEMS.containsKey(item),"Actually registered kit item: "+item);checked++;
            }
        }
        h.assertTrue(checked>=90,"The three kits cover the full plant, animal and workshop systems");h.succeed();
    }
}
