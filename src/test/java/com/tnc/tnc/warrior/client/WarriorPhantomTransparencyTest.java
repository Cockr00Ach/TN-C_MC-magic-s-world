package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

/** Frame ownership and actual alpha routes, without a level, registries or RenderType initialization. */
class WarriorPhantomTransparencyTest {
    private static final int OVERLAY=0x12345678,LIGHT=0x00f00080;

    @Test void submissionsNeedAnOpenWorldFrameAndCannotLeakAfterFlush(){
        var frame=new WarriorPhantomFrame<String>(2);var level=new Object();
        assertFalse(frame.add(level,"GUI submission"));assertTrue(frame.end(level).isEmpty());
        frame.begin(level);assertTrue(frame.add(level,"sword"));
        assertEquals(List.of("sword"),frame.end(level));
        assertFalse(frame.add(level,"late item pass"));assertTrue(frame.end(level).isEmpty());
        frame.begin(null);assertFalse(frame.add(level,"GUI after world"));assertFalse(frame.add(null,"no world"));
        assertTrue(frame.end(null).isEmpty());
    }
    @Test void beginAlwaysDropsPriorFrameAndFlushReturnsAnImmutableDetachedSnapshot(){
        var frame=new WarriorPhantomFrame<String>(3);var level=new Object();
        frame.begin(level);frame.add(level,"unflushed previous frame");frame.begin(level);
        assertTrue(frame.add(level,"current"));var snapshot=frame.end(level);
        assertEquals(List.of("current"),snapshot);assertThrows(UnsupportedOperationException.class,()->snapshot.add("mutation"));
        frame.begin(level);frame.add(level,"next");frame.clear();
        assertEquals(List.of("current"),snapshot);assertTrue(frame.end(level).isEmpty());assertFalse(frame.add(level,"after clear"));
    }
    @Test void levelIdentityMustMatchAndAnInvalidFlushClosesTheWindow(){
        var frame=new WarriorPhantomFrame<String>(3);Object level=new String("equal name"),other=new String("equal name");
        assertEquals(level,other);assertNotSame(level,other);
        frame.begin(level);assertTrue(frame.add(level,"old world"));assertFalse(frame.add(other,"different world"));
        assertTrue(frame.end(other).isEmpty());assertFalse(frame.add(level,"after wrong-world flush"));assertTrue(frame.end(level).isEmpty());
        frame.begin(level);frame.add(level,"discard on world change");frame.begin(other);
        assertFalse(frame.add(level,"stale capture"));assertTrue(frame.add(other,"new world"));
        assertEquals(List.of("new world"),frame.end(other));
    }
    @Test void perFrameLimitKeepsFirstDrawsAndResetsNextFrame(){
        var frame=new WarriorPhantomFrame<String>(2);var level=new Object();frame.begin(level);
        assertTrue(frame.add(level,"first"));assertTrue(frame.add(level,"second"));assertFalse(frame.add(level,"overflow"));
        assertEquals(List.of("first","second"),frame.end(level));
        frame.begin(level);assertTrue(frame.add(level,"fresh capacity"));assertEquals(List.of("fresh capacity"),frame.end(level));
        var none=new WarriorPhantomFrame<String>(0);none.begin(level);assertFalse(none.add(level,"disabled budget"));assertTrue(none.end(level).isEmpty());
        assertThrows(IllegalArgumentException.class,()->new WarriorPhantomFrame<String>(-1));
    }
    @Test void opacityRemainsASubtleSilhouetteAcrossSmallAndLargeWeapons(){
        for(double scale:new double[]{Double.NEGATIVE_INFINITY,-100,0,1,3,5.5,8,10,100000,Double.POSITIVE_INFINITY}){
            float normal=WarriorPhantomRenderer.opacity(scale,false),combo=WarriorPhantomRenderer.opacity(scale,true);
            assertTrue(Float.isFinite(normal)&&normal>=.07f&&normal<=.10f,"normal opacity at "+scale+" = "+normal);
            assertTrue(Float.isFinite(combo)&&combo>=.10f&&combo<=.14f,"combo opacity at "+scale+" = "+combo);
            assertTrue(combo>=normal,"A finisher can brighten the silhouette without making it opaque");
        }
        assertEquals(.07f,WarriorPhantomRenderer.opacity(3,false),.000001f);
        assertEquals(.10f,WarriorPhantomRenderer.opacity(10,false),.000001f);
        assertEquals(.14f,WarriorPhantomRenderer.opacity(10,true),.000001f);
    }
    @Test void intFloatPackedAndFullVertexRoutesMultiplyAlphaExactlyOnce(){
        float factor=.37f;
        for(int route=0;route<4;route++){
            var sink=new Recording();var out=new WarriorPhantomRenderer.Alpha(sink,factor);
            if(route==3){
                out.vertex(1.25f,-2.5f,3.75f,.2f,.4f,.6f,.8f,.125f,.875f,OVERLAY,LIGHT,.25f,-.5f,.75f);
            }else{
                VertexConsumer vertex=out.vertex(1.25,-2.5,3.75);
                assertSame(out,vertex,"Position chaining must retain the alpha wrapper");
                if(route==0)vertex.color(40,80,120,201);
                if(route==1)vertex.color(.2f,.4f,.6f,.8f);
                if(route==2)vertex.color(0xc9285078);
                vertex.uv(.125f,.875f).overlayCoords(OVERLAY).uv2(LIGHT).normal(.25f,-.5f,.75f).endVertex();
            }
            String label="colour route "+route;
            assertEquals(1,sink.colours,label+" must not double-submit colour");assertEquals(1,sink.ends,label+" emits one vertex");
            float inputAlpha=route==0||route==2?201/255f:.8f;
            float[] rgb=route==0||route==2?new float[]{40/255f,80/255f,120/255f}:new float[]{.2f,.4f,.6f};
            assertEquals(inputAlpha*factor,sink.a,1/255f,label+" alpha should be scaled once, neither omitted nor squared");
            assertEquals(rgb[0],sink.r,1/255f,label+" red");assertEquals(rgb[1],sink.g,1/255f,label+" green");assertEquals(rgb[2],sink.b,1/255f,label+" blue");
            assertAttributes(sink,label);
        }
    }
    @Test void defaultColourScalesOnceAndStillForwardsItsResetAndVertexAttributes(){
        var sink=new Recording();var out=new WarriorPhantomRenderer.Alpha(sink,.37f);
        out.defaultColor(40,80,120,201);
        out.vertex(1.25,-2.5,3.75).uv(.125f,.875f).overlayCoords(OVERLAY).uv2(LIGHT).normal(.25f,-.5f,.75f).endVertex();
        assertEquals(1,sink.defaults);assertEquals(1,sink.ends);assertEquals(Math.round(201*.37f)/255f,sink.a,.000001f);
        assertAttributes(sink,"default colour");out.unsetDefaultColor();assertEquals(1,sink.unsets);
    }
    @Test void firstPersonScalesTheAlreadySubtleOpacityByPointSixFiveOnlyOnce(){
        float opacity=WarriorPhantomRenderer.opacity(10,true);var third=new Recording();var first=new Recording();
        new WarriorPhantomRenderer.Alpha(third,opacity).color(1f,1f,1f,1f);
        new WarriorPhantomRenderer.Alpha(first,opacity*.65f).color(1f,1f,1f,1f);
        assertEquals(opacity,third.a,.000001f);assertEquals(opacity*.65f,first.a,.000001f);
        assertEquals(.65f,first.a/third.a,.000001f);assertEquals(1,first.colours);
    }
    @Test void theCompiledPhantomMaterialIsTexturedTranslucentAndNeverWritesDepth() throws Exception {
        MethodNode recipe=method(read("com/tnc/tnc/warrior/client/WarriorPhantomRenderType"),"<clinit>");
        assertEquals("RENDERTYPE_ENTITY_TRANSLUCENT_SHADER",setting(recipe,"setShaderState"));
        assertEquals("TRANSLUCENT_TRANSPARENCY",setting(recipe,"setTransparencyState"));
        assertEquals("CULL",setting(recipe,"setCullState"));assertEquals("LEQUAL_DEPTH_TEST",setting(recipe,"setDepthTestState"));
        assertEquals("COLOR_WRITE",setting(recipe,"setWriteMaskState"),"A translucent phantom must not become an invisible occluder");
        assertEquals("LIGHTMAP",setting(recipe,"setLightmapState"));assertEquals("OVERLAY",setting(recipe,"setOverlayState"));
        assertEquals("MAIN_TARGET",setting(recipe,"setOutputState"));
        boolean atlas=false;
        for(AbstractInsnNode instruction:recipe.instructions)if(instruction instanceof FieldInsnNode field
                &&field.getOpcode()==Opcodes.GETSTATIC&&field.owner.equals("net/minecraft/client/renderer/texture/TextureAtlas")&&field.name.equals("LOCATION_BLOCKS"))atlas=true;
        assertTrue(atlas,"The phantom must retain the actual item atlas rather than become a flat white shape");
    }
    @Test void theGhostPassDrawsEachBakedModelWithoutReplayingFoilOrOpaqueItemPasses() throws Exception {
        MethodNode ghost=method(read("com/tnc/tnc/warrior/client/WarriorPhantomRenderer"),"renderGhost");int modelDraws=0;
        for(AbstractInsnNode instruction:ghost.instructions)if(instruction instanceof MethodInsnNode call){
            if(call.owner.equals("net/minecraft/client/renderer/entity/ItemRenderer")&&call.name.equals("renderModelLists"))modelDraws++;
            assertFalse(call.name.contains("FoilBuffer"),"Foil replay can submit duplicate phantom geometry");
            assertFalse(call.owner.equals("net/minecraft/client/renderer/entity/ItemRenderer")&&call.name.equals("render"),"The full item render path replays its material/glint passes");
        }
        assertEquals(1,modelDraws,"One geometry submission site per baked pass prevents enchanted swords becoming opaque");
    }

    private static void assertAttributes(Recording sink,String label){
        assertEquals(1.25,sink.x,0,label+" x");assertEquals(-2.5,sink.y,0,label+" y");assertEquals(3.75,sink.z,0,label+" z");
        assertEquals(.125f,sink.u,0,label+" u");assertEquals(.875f,sink.v,0,label+" v");
        assertEquals(OVERLAY&65535,sink.overlayU,label+" overlay u");assertEquals(OVERLAY>>>16,sink.overlayV,label+" overlay v");
        assertEquals(LIGHT&65535,sink.lightU,label+" light u");assertEquals(LIGHT>>>16,sink.lightV,label+" light v");
        assertEquals(.25f,sink.nx,0,label+" normal x");assertEquals(-.5f,sink.ny,0,label+" normal y");assertEquals(.75f,sink.nz,0,label+" normal z");
    }
    private static final class Recording implements VertexConsumer {
        double x,y,z;float r,g,b,a,u,v,nx,ny,nz;int overlayU,overlayV,lightU,lightV,colours,ends,defaults,unsets;
        @Override public VertexConsumer vertex(double x,double y,double z){this.x=x;this.y=y;this.z=z;return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){colours++;this.r=r/255f;this.g=g/255f;this.b=b/255f;this.a=a/255f;return this;}
        @Override public VertexConsumer color(float r,float g,float b,float a){colours++;this.r=r;this.g=g;this.b=b;this.a=a;return this;}
        @Override public VertexConsumer color(int packed){return color(packed>>>16&255,packed>>>8&255,packed&255,packed>>>24);}
        @Override public VertexConsumer uv(float u,float v){this.u=u;this.v=v;return this;}
        @Override public VertexConsumer overlayCoords(int u,int v){overlayU=u;overlayV=v;return this;}
        @Override public VertexConsumer uv2(int u,int v){lightU=u;lightV=v;return this;}
        @Override public VertexConsumer normal(float x,float y,float z){nx=x;ny=y;nz=z;return this;}
        @Override public void endVertex(){ends++;}
        @Override public void defaultColor(int r,int g,int b,int a){defaults++;this.r=r/255f;this.g=g/255f;this.b=b/255f;this.a=a/255f;}
        @Override public void unsetDefaultColor(){unsets++;}
        @Override public void vertex(float x,float y,float z,float r,float g,float b,float a,float u,float v,int overlay,int light,float nx,float ny,float nz){
            vertex((double)x,y,z).color(r,g,b,a).uv(u,v).overlayCoords(overlay).uv2(light).normal(nx,ny,nz).endVertex();
        }
    }
    private static String setting(MethodNode recipe,String setter){
        String selected=null;
        for(AbstractInsnNode instruction:recipe.instructions)if(instruction instanceof MethodInsnNode call&&call.name.equals(setter)){
            var value=assertInstanceOf(FieldInsnNode.class,previous(call));assertEquals(Opcodes.GETSTATIC,value.getOpcode());selected=value.name;
        }
        return selected;
    }
    private static AbstractInsnNode previous(AbstractInsnNode instruction){
        AbstractInsnNode value=instruction.getPrevious();while(value!=null&&value.getOpcode()<0)value=value.getPrevious();assertNotNull(value);return value;
    }
    private static MethodNode method(ClassNode type,String name){
        var matches=type.methods.stream().filter(m->m.name.equals(name)).toList();assertEquals(1,matches.size(),type.name+" "+name);return matches.get(0);
    }
    private static ClassNode read(String name) throws Exception {
        try(InputStream input=WarriorPhantomTransparencyTest.class.getResourceAsStream("/"+name+".class")){
            assertNotNull(input,"Compiled class missing: "+name);var type=new ClassNode();new ClassReader(input).accept(type,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);return type;
        }
    }
}
