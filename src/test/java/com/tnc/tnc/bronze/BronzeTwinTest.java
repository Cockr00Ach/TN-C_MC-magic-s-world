package com.tnc.tnc.bronze;

import com.google.gson.JsonParser;
import java.nio.file.*;
import net.minecraft.client.renderer.block.model.BlockModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BronzeTwinTest {
    private static final Path ASSETS=Path.of("src/main/resources/assets/tnc");
    @Test void pairedWeaponNeedsTheMainHandAndAnActuallyEmptyOffhand(){
        assertTrue(BronzeTwinWeapon.permits(BronzeProfile.HALBERD,true,true));
        assertFalse(BronzeTwinWeapon.permits(BronzeProfile.HALBERD,true,false),"A shield, block or any other offhand item must disable the pair");
        assertFalse(BronzeTwinWeapon.permits(BronzeProfile.HALBERD,false,true),"The pair cannot be used independently in the offhand");
        assertFalse(BronzeTwinWeapon.permits(BronzeProfile.HALBERD,false,false));
    }
    @Test void originalFourWeaponsKeepTheirOffhandCompatibility(){
        for(var profile:new BronzeProfile[]{BronzeProfile.GREATSWORD,BronzeProfile.SPEAR,BronzeProfile.DAO,BronzeProfile.DAGGER})assertTrue(BronzeTwinWeapon.permits(profile,true,false));
    }
    @Test void bothMeshesAreVanillaLoadableAndTheirGripsShareTheHandPivot() throws Exception {
        for(String mesh:new String[]{"bronze_twin_main","bronze_twin_off"}){
            Path path=ASSETS.resolve("models/item/"+mesh+".json");
            try(var reader=Files.newBufferedReader(path)){assertFalse(BlockModel.fromStream(reader).getElements().isEmpty());}
            var model=JsonParser.parseString(Files.readString(path)).getAsJsonObject();boolean grip=false;
            for(var raw:model.getAsJsonArray("elements")){
                var e=raw.getAsJsonObject();if(!e.get("name").getAsString().equals("ancient_wood_core"))continue;
                var from=e.getAsJsonArray("from");var to=e.getAsJsonArray("to");
                assertEquals(8,(from.get(0).getAsDouble()+to.get(0).getAsDouble())/2,.0001);
                assertEquals(8,(from.get(2).getAsDouble()+to.get(2).getAsDouble())/2,.0001);
                assertTrue(from.get(1).getAsDouble()<8&&to.get(1).getAsDouble()>8);grip=true;
            }
            assertTrue(grip,"Each blade must attach at its handle, not its blade or pommel");
        }
    }
    @Test void fusedItemRoutesToPairedRenderingAndOnlyTheSwordFamily() throws Exception {
        var model=JsonParser.parseString(Files.readString(ASSETS.resolve("models/item/bronze_halberd.json"))).getAsJsonObject();
        assertEquals("builtin/entity",model.get("parent").getAsString());
        Path tags=Path.of("src/main/resources/data/tnc/tags/items");
        var swords=JsonParser.parseString(Files.readString(tags.resolve("longswords.json"))).getAsJsonObject().getAsJsonArray("values");
        var spears=JsonParser.parseString(Files.readString(tags.resolve("spears.json"))).getAsJsonObject().getAsJsonArray("values");
        assertTrue(swords.asList().stream().anyMatch(v->v.getAsString().equals("tnc:bronze_halberd")));
        assertFalse(spears.asList().stream().anyMatch(v->v.getAsString().equals("tnc:bronze_halberd")));
    }
}
