package com.tnc.tnc.boss;

import com.tnc.tnc.boss.client.DarkGiantArmorMesh;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.cache.object.*;
import static org.junit.jupiter.api.Assertions.*;

class DarkGiantArmorMeshTest {
    @Test void productionModelBakesWithGeckoLibAndSharesTheChamferedArmor() throws Exception {
        String json=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/assets/tnc/geo/entity/dark_giant.geo.json"));
        var raw=software.bernie.geckolib.util.JsonUtil.GEO_GSON.fromJson(json,software.bernie.geckolib.loading.json.raw.Model.class);
        var baked=new software.bernie.geckolib.loading.object.BakedModelFactory.Builtin().constructGeoModel(
                software.bernie.geckolib.loading.object.GeometryTree.fromModel(raw));
        DarkGiantArmorMesh.apply(baked);
        assertTrue(baked.getBone("shinright").isPresent());assertTrue(baked.getBone("footleft").isPresent());
        assertTrue(baked.getBone("body").orElseThrow().getCubes().stream().anyMatch(c->c.quads().length==20));
        DarkGiantArmorMesh.apply(baked);
        for(var cube:baked.getBone("body").orElseThrow().getCubes())
            for(var face:cube.quads())if(face!=null)assertTrue(face.normal().isFinite());
    }
    @Test void armorHasActualCutCornersAndFiniteNormals(){
        int[][] faces={{0,1,3,2},{4,6,7,5},{0,2,6,4},{1,5,7,3},{2,3,7,6},{0,4,5,1}};
        Direction[] directions={Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST,Direction.UP,Direction.DOWN};
        var quads=new GeoQuad[6];
        for(int face=0;face<6;face++){
            var vertices=new GeoVertex[4];
            for(int i=0;i<4;i++){
                int p=faces[face][i];vertices[i]=new GeoVertex(new Vector3f(p&1,(p>>1)&1,(p>>2)&1),i%2,i/2);
            }
            var d=directions[face];quads[face]=new GeoQuad(vertices,new Vector3f(d.getStepX(),d.getStepY(),d.getStepZ()),d);
        }
        var source=new GeoCube(quads,Vec3.ZERO,Vec3.ZERO,new Vec3(1,1,1),0,false);
        var result=DarkGiantArmorMesh.chamfer(source);
        assertEquals(20,result.quads().length);
        for(var face:result.quads()){
            assertTrue(face.normal().isFinite());assertEquals(1,face.normal().length(),.001);
            for(var v:face.vertices()){
                var p=v.position();
                boolean cornerX=p.x==0||p.x==1,cornerY=p.y==0||p.y==1;
                assertFalse(cornerX&&cornerY,"The original rectangular corners must actually be removed");
                assertTrue(v.texU()>=0&&v.texU()<=1&&v.texV()>=0&&v.texV()<=1);
            }
        }
        assertSame(result,DarkGiantArmorMesh.chamfer(result));
    }
}
