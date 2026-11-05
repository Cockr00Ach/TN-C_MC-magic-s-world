package com.tnc.tnc.bronze;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.bronze.client.BronzePuppetModel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.block.model.BlockModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Asset deserialization and the new high-density UV path, without a game or graphics context. */
class BronzeArtResourcesTest {
    private static final Path ASSETS=Path.of("src/main/resources/assets/tnc");
    @Test void everyWeaponIsAcceptedByTheActualVanillaModelDeserializer() throws Exception {
        for(var kind:BronzeProfile.values()){
            assertTrue(Files.readString(ASSETS.resolve("models/item/"+kind.id()+".json")).length()<=2_097_152,"Keep dense item assets within the existing Windows installer JSON parser limit");
            try(var reader=Files.newBufferedReader(ASSETS.resolve("models/item/"+kind.id()+".json"),StandardCharsets.UTF_8)){
                var model=BlockModel.fromStream(reader);
                assertFalse(model.getElements().isEmpty());
                for(var element:model.getElements())assertEquals(6,element.faces.size());
            }
        }
    }
    @Test void puppetBakesWithUnstretchedHighDensityUVsAndRotatedFaceDetails() throws Exception {
        var metadata=JsonParser.parseString(Files.readString(ASSETS.resolve("geo/entity/bronze_puppet_parts.json"))).getAsJsonObject();
        var texture=ImageIO.read(ASSETS.resolve("textures/entity/bronze_puppet.png").toFile());
        assertEquals(metadata.get("texture_width").getAsInt(),texture.getWidth());
        assertEquals(metadata.get("texture_height").getAsInt(),texture.getHeight());
        var root=BronzePuppetModel.layer().bakeRoot();
        for(String name:new String[]{"head","hat","body","right_arm","left_arm","right_leg","left_leg"})assertNotNull(root.getChild(name));
        assertNotNull(root.getChild("head").getChild("detail_0"));
        var sink=new Vertices();root.getChild("head").render(new PoseStack(),sink,0,0);
        assertTrue(sink.values.size()>=24);
        for(var v:sink.values){assertTrue(v[3]>=0&&v[3]<=1);assertTrue(v[4]>=0&&v[4]<=1);}
        var first=metadata.getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonArray("boxes").get(0).getAsJsonObject().getAsJsonArray("box");
        double w=first.get(3).getAsDouble(),d=first.get(5).getAsDouble();
        var vertices=sink.values.subList(0,24);
        double minU=vertices.stream().mapToDouble(v->v[3]).min().orElseThrow(),maxU=vertices.stream().mapToDouble(v->v[3]).max().orElseThrow();
        double minX=vertices.stream().mapToDouble(v->v[0]).min().orElseThrow(),maxX=vertices.stream().mapToDouble(v->v[0]).max().orElseThrow();
        assertEquals(2*(w+d)*metadata.get("texel_density").getAsInt(),(maxU-minU)*texture.getWidth(),.02,"UV density must scale texels, not stretch or shrink the head");
        assertEquals(w,(maxX-minX)*16,.001,"Authored dimensions must remain in normal model units");
    }
    private static final class Vertices implements VertexConsumer {
        final ArrayList<double[]> values=new ArrayList<>();double x,y,z;float u,v;
        public VertexConsumer vertex(double x,double y,double z){this.x=x;this.y=y;this.z=z;return this;}
        public VertexConsumer color(int r,int g,int b,int a){return this;}
        public VertexConsumer uv(float u,float v){this.u=u;this.v=v;return this;}
        public VertexConsumer overlayCoords(int u,int v){return this;}
        public VertexConsumer uv2(int u,int v){return this;}
        public VertexConsumer normal(float x,float y,float z){return this;}
        public void endVertex(){values.add(new double[]{x,y,z,u,v});}
        public void defaultColor(int r,int g,int b,int a){}
        public void unsetDefaultColor(){}
    }
}
