package com.tnc.tnc.warrior.client;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

/** Read the compiled material recipes without initializing RenderType or the game registries. */
class WarriorOrnamentDepthTest {
    private static final String ORNAMENT="com/tnc/tnc/warrior/client/WarriorWeaponOrnamentRenderType";
    private static final String TRAIL="com/tnc/tnc/warrior/client/WarriorTrailRenderType";
    private static final String RENDER_TYPE="net/minecraft/client/renderer/RenderType";
    private static final String STATE_SHARD="net/minecraft/client/renderer/RenderStateShard";
    private enum Colour { BACKGROUND, FRONT_ORNAMENT, BLADE, BACK_ORNAMENT }
    private record Samples(Colour front,Colour back) {}
    private static final class Pixel {
        double depth=1;
        Colour colour=Colour.BACKGROUND;
        void fragment(double z,Colour value,boolean writeDepth){
            // Both inspected recipes use LEQUAL. Physical ornament fragments have alpha one.
            if(z<=depth){colour=value;if(writeDepth)depth=z;}
        }
    }

    @Test void solidJewelleryReallyOwnsDepthAndUsesOpaqueLequalMainTarget() throws Exception {
        MethodNode recipe=method(read(ORNAMENT),"<clinit>");
        assertEquals("COLOR_DEPTH_WRITE",setting(recipe,"setWriteMaskState"));
        assertTrue(writesDepth(mask(recipe)),"Front jewellery must protect its pixel from a later blade batch");
        assertEquals("LEQUAL_DEPTH_TEST",setting(recipe,"setDepthTestState"));
        assertEquals("NO_TRANSPARENCY",setting(recipe,"setTransparencyState"));
        assertEquals("MAIN_TARGET",setting(recipe,"setOutputState"));
        assertEquals("OVERLAY",setting(recipe,"setOverlayState"),"The shader must have its overlay sampler bound");
        assertEquals("RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER",setting(recipe,"setShaderState"),"Opaque item fog must use the normal cutout shader");
        assertEquals("LIGHTMAP",setting(recipe,"setLightmapState"),"Full-bright vertex UVs still need the lightmap sampler");
    }

    @Test void frontAndBackJewelleryRemainCorrectInEitherDeferredBatchOrder() throws Exception {
        boolean ornamentDepth=writesDepth(mask(method(read(ORNAMENT),"<clinit>")));
        boolean bladeDepth=bladeDepth();
        assertTrue(bladeDepth,"The real vanilla solid entity factory must write depth in this fixture");
        Samples ornamentsFirst=render(ornamentDepth,bladeDepth,true);
        Samples bladeFirst=render(ornamentDepth,bladeDepth,false);
        assertEquals(Colour.FRONT_ORNAMENT,ornamentsFirst.front(),"z=.35 ring must survive the later z=.50 blade");
        assertEquals(Colour.BLADE,ornamentsFirst.back(),"z=.65 rear ring must be covered by the z=.50 blade");
        assertEquals(Colour.FRONT_ORNAMENT,bladeFirst.front(),"Near ring also passes when the blade was drawn first");
        assertEquals(Colour.BLADE,bladeFirst.back(),"Rear ring fails the already populated blade depth");
        assertEquals(ornamentsFirst,bladeFirst,"Flush order must not change solid jewellery visibility");
    }

    @Test void theFormerColourOnlyTrailMaterialReproducesTheLostFrontRing() throws Exception {
        // Verify the old TYPE assignment really calls the vanilla emissive factory.
        MethodNode trail=method(read(TRAIL),"<clinit>");
        FieldInsnNode assignment=null;
        for(AbstractInsnNode instruction:trail.instructions)if(instruction instanceof FieldInsnNode field
                &&field.getOpcode()==Opcodes.PUTSTATIC&&field.owner.equals(TRAIL)&&field.name.equals("TYPE"))assignment=field;
        assertNotNull(assignment,"Former ornament material must still expose its actual TYPE assignment");
        var call=assertInstanceOf(MethodInsnNode.class,previous(assignment));
        assertEquals(RENDER_TYPE,call.owner);assertEquals("entityTranslucentEmissive",call.name);
        // Identify its synthetic factory by the actual material-name constant, not a javac lambda index.
        MethodNode emissive=factory("entity_translucent_emissive");
        assertEquals("COLOR_WRITE",setting(emissive,"setWriteMaskState"));
        boolean oldDepth=writesDepth(mask(emissive)),bladeDepth=bladeDepth();
        assertFalse(oldDepth,"The former trail material is genuinely colour-only in vanilla bytecode");
        Samples ornamentsFirst=render(oldDepth,bladeDepth,true);
        Samples bladeFirst=render(oldDepth,bladeDepth,false);
        assertEquals(Colour.BLADE,ornamentsFirst.front(),"Old material loses the near ring when the blade flushes later");
        assertEquals(Colour.FRONT_ORNAMENT,bladeFirst.front(),"The same old ring appears when its batch happens to flush last");
        assertNotEquals(ornamentsFirst,bladeFirst,"This is the original order-dependent visibility bug");
    }

    private static boolean bladeDepth() throws Exception {
        MethodNode solid=factory("entity_solid");
        assertNull(setting(solid,"setWriteMaskState"),"Vanilla solid material currently relies on its real builder default");
        String mask=mask(solid);assertEquals("COLOR_DEPTH_WRITE",mask);return writesDepth(mask);
    }
    private static Samples render(boolean ornamentDepth,boolean bladeDepth,boolean ornamentsFirst){
        var front=new Pixel();var back=new Pixel();
        if(ornamentsFirst)ornaments(front,back,ornamentDepth);
        front.fragment(.50,Colour.BLADE,bladeDepth);back.fragment(.50,Colour.BLADE,bladeDepth);
        if(!ornamentsFirst)ornaments(front,back,ornamentDepth);
        return new Samples(front.colour,back.colour);
    }
    private static void ornaments(Pixel front,Pixel back,boolean writeDepth){
        front.fragment(.35,Colour.FRONT_ORNAMENT,writeDepth);back.fragment(.65,Colour.BACK_ORNAMENT,writeDepth);
    }

    private static String mask(MethodNode recipe) throws Exception {
        String explicit=setting(recipe,"setWriteMaskState");if(explicit!=null)return explicit;
        MethodInsnNode builder=null;
        for(AbstractInsnNode instruction:recipe.instructions)if(instruction instanceof MethodInsnNode call&&call.name.equals("builder"))builder=call;
        assertNotNull(builder,"Material must construct a composite-state builder");
        // The nested builder owner comes from the public builder() descriptor. No private field names.
        ClassNode type=read(Type.getReturnType(builder.desc).getInternalName());
        MethodNode setter=method(type,"setWriteMaskState");String shard=Type.getArgumentTypes(setter.desc)[0].getDescriptor();
        MethodNode constructor=method(type,"<init>");
        for(AbstractInsnNode instruction:constructor.instructions)if(instruction instanceof FieldInsnNode field
                &&field.getOpcode()==Opcodes.PUTFIELD&&field.desc.equals(shard)){
            var value=assertInstanceOf(FieldInsnNode.class,previous(field));assertEquals(Opcodes.GETSTATIC,value.getOpcode());return value.name;
        }
        throw new AssertionError("Builder constructor did not initialize a write-mask state");
    }
    private static boolean writesDepth(String constant) throws Exception {
        // Read the boolean arguments passed to the actual vanilla WriteMaskStateShard constructor:
        // colour writes first, depth writes second. Nothing in the game is initialized or executed.
        MethodNode initializer=method(read(STATE_SHARD),"<clinit>");
        for(AbstractInsnNode instruction:initializer.instructions)if(instruction instanceof FieldInsnNode field
                &&field.getOpcode()==Opcodes.PUTSTATIC&&field.name.equals(constant)){
            var constructor=assertInstanceOf(MethodInsnNode.class,previous(field));
            assertEquals("<init>",constructor.name);assertEquals("(ZZ)V",constructor.desc);
            assertTrue(constructor.owner.endsWith("$WriteMaskStateShard"));
            int value=previous(constructor).getOpcode();assertTrue(value==Opcodes.ICONST_0||value==Opcodes.ICONST_1);
            return value==Opcodes.ICONST_1;
        }
        throw new AssertionError("Vanilla write-mask constant missing: "+constant);
    }
    private static String setting(MethodNode recipe,String setter){
        String selected=null;
        for(AbstractInsnNode instruction:recipe.instructions)if(instruction instanceof MethodInsnNode call&&call.name.equals(setter)){
            var value=assertInstanceOf(FieldInsnNode.class,previous(call));assertEquals(Opcodes.GETSTATIC,value.getOpcode());selected=value.name;
        }
        return selected;
    }
    private static MethodNode factory(String name) throws Exception {
        List<MethodNode> matches=new ArrayList<>();
        for(MethodNode method:read(RENDER_TYPE).methods)for(AbstractInsnNode instruction:method.instructions)
            if(instruction instanceof LdcInsnNode literal&&name.equals(literal.cst)){matches.add(method);break;}
        assertEquals(1,matches.size(),"Expected one vanilla material factory for "+name);return matches.get(0);
    }
    private static AbstractInsnNode previous(AbstractInsnNode instruction){
        AbstractInsnNode previous=instruction.getPrevious();while(previous!=null&&previous.getOpcode()<0)previous=previous.getPrevious();
        assertNotNull(previous,"Expected a preceding executable instruction");return previous;
    }
    private static MethodNode method(ClassNode type,String name){
        var matches=type.methods.stream().filter(method->method.name.equals(name)).toList();
        assertEquals(1,matches.size(),"Expected one "+name+" on "+type.name);return matches.get(0);
    }
    private static ClassNode read(String name) throws Exception {
        try(InputStream input=WarriorOrnamentDepthTest.class.getResourceAsStream("/"+name+".class")){
            assertNotNull(input,"Compiled class missing from test classpath: "+name);var type=new ClassNode();
            new ClassReader(input).accept(type,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);return type;
        }
    }
}
