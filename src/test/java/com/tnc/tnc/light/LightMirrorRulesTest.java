package com.tnc.tnc.light;

import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class LightMirrorRulesTest {
    @Test void learnableMirrorChainHasAllFiveResourcesAndPreservesOldOrdinals() throws Exception {
        var chain=com.tnc.tnc.magic.SpellCatalog.Chain.LIGHT_MIRROR;
        assertEquals(com.tnc.tnc.magic.SpellCatalog.Chain.WIND_WEAPON.ordinal()+1,chain.ordinal());
        var entries=com.tnc.tnc.magic.SpellCatalog.of(com.tnc.tnc.magic.Element.LIGHT,chain);
        assertEquals(5,entries.size());
        String pool=Files.readString(Path.of("src/main/resources/data/tnc/spell_pools/tnc_light.json"));
        for(var entry:entries) {
            assertEquals(entry.tier(),LightMirrorSpells.tier(entry.id().getPath()));
            assertTrue(pool.contains(entry.id().toString()));
            assertTrue(Files.isRegularFile(Path.of("src/main/resources/data/tnc/spells/"+entry.id().getPath()+".json")));
            assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/tnc/textures/spell/"+entry.id().getPath()+".png")));
        }
        assertEquals(0,LightMirrorSpells.tier("light_beam"));
    }
    @Test void higherTiersIncreaseCountPaletteLifetimeAndAfterimage() {
        for(int tier=1;tier<=5;tier++) {
            assertEquals(tier+2, LightMirrorRules.rays(tier));
            assertEquals(tier+2, java.util.stream.IntStream.range(0,tier+2).map(LightMirrorRules::color).distinct().count());
            assertTrue(LightMirrorRules.lifetime(tier)>LightMirrorRules.lifetime(tier-1)||tier==1);
            if(tier>1) assertTrue(LightMirrorRules.afterimageTicks(tier)>LightMirrorRules.afterimageTicks(tier-1));
        }
    }
    @Test void splitDirectionsRemainNormalizedDistinctAndPointForward() {
        for(Vec3 axis:List.of(new Vec3(0,0,1),new Vec3(1,0,0),new Vec3(-.4,0,.8).normalize()))
            for(int tier=1;tier<=5;tier++) {
                var split=LightMirrorRules.split(axis,tier);
                assertEquals(tier+2,split.size());
                for(Vec3 d:split) { assertEquals(1,d.length(),1E-6);assertTrue(d.dot(axis)>.7); }
                assertEquals(split.size(),new HashSet<>(split).size());
            }
    }
    @Test void skyBurstAlwaysHasSixEquallySpacedHorizontalSpokes() {
        for(float yaw:new float[]{0,35,180,-90}) {
            var directions=LightMirrorRules.sixDirections(yaw);
            assertEquals(6,directions.size());
            for(int i=0;i<6;i++) { assertEquals(0,directions.get(i).y,1E-6);
                assertEquals(.5,directions.get(i).dot(directions.get((i+1)%6)),1E-5); }
        }
    }
    @Test void segmentHitsUseGeometryNotTheEntireDiagonalAabb() {
        Vec3 from=Vec3.ZERO,to=new Vec3(10,0,10);
        assertTrue(LightMirrorRules.intersects(from,to,new AABB(4,-.5,4,6,.5,6),.1));
        assertFalse(LightMirrorRules.intersects(from,to,new AABB(0,-.5,8,1,.5,9),.1));
        assertTrue(LightMirrorRules.intersects(from,to,new AABB(-1,-1,-1,1,1,1),0));
    }
    @Test void playerForwardSpellsHaveNoChargeAndUseFixedPulseStyle() throws Exception {
        for(String spell:List.of("light_beam","great_light_beam","giant_light_beam")) {
            var json=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/tnc/spells/"+spell+".json"))).getAsJsonObject();
            assertEquals(0,json.getAsJsonObject("cast").get("duration").getAsDouble());
        }
        String source=Files.readString(Path.of("src/main/java/com/tnc/tnc/light/TNLightBeamMechanics.java"));
        assertTrue(source.contains("dirs.get(i),spell,TNLightBeamEntity.STYLE_PULSE"));
    }
    @Test void mirrorMeshesHaveRealThicknessAndTierDetail() {
        var first=com.tnc.tnc.light.client.LightPrismModels.mirror(1);
        var last=com.tnc.tnc.light.client.LightPrismModels.mirror(5);
        assertTrue(last.size()>first.size());
        assertTrue(first.stream().anyMatch(f->f.a().z()>.1));
        assertTrue(first.stream().anyMatch(f->f.a().z()<-.1));
        assertTrue(last.size()<8000,"Detailed mirror still has a bounded, cached mesh");
        assertTrue(last.stream().map(com.tnc.tnc.light.client.LightPrismModels.Face::color).distinct().count()>40,"Glass and metal must not be a handful of flat colour slabs");
    }
}
