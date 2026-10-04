package com.tnc.tnc.life.pasture.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.life.botanical.BotanicalContent;
import com.tnc.tnc.life.pasture.PastureRegistry;
import com.tnc.tnc.life.wonders.WonderContent;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Opt-in development smoke check after real client resource loading; never runs in normal play. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT)
public final class EcologyClientAudit {
    private static final List<String> failures=new ArrayList<>();
    private static int renderers,frames;
    private static boolean finished;
    @Mod.EventBusSubscriber(modid=TNMod.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Models {
        @SubscribeEvent public static void renderers(EntityRenderersEvent.AddLayers event){
            if(!Boolean.getBoolean("tnc.ecologyClientAudit"))return;
            PastureRegistry.TYPES.forEach((id,type)->{if(event.getRenderer(type)==null)failures.add("Missing animal renderer: "+id);else renderers++;});
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception{
        if(!Boolean.getBoolean("tnc.ecologyClientAudit")||finished||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.screen==null||mc.getOverlay()!=null||++frames<20)return;
        finished=true;
        if(renderers!=23)failures.add("Expected 23 constructed animal renderers, found "+renderers);
        var models=mc.getModelManager();var missing=models.getMissingModel();
        Set<Item> items=new LinkedHashSet<>(PastureRegistry.ITEMS.values());items.addAll(BotanicalContent.SEEDS.values());items.addAll(BotanicalContent.PRODUCTS.values());items.addAll(BotanicalContent.TOOLS.values());
        items.addAll(List.of(WonderContent.SKY_VINE_SEED,WonderContent.SKY_FIBER,WonderContent.SKY_ROPE,WonderContent.SURVEY,WonderContent.RELAY_ITEM));
        for(Item item:items){var id=ForgeRegistries.ITEMS.getKey(item);if(models.getModel(new ModelResourceLocation(id,"inventory"))==missing)failures.add("Missing baked item: "+id);}
        for(String id:List.of("dance_bell_joint","dance_bell_head","dawn_disk_head","mist_cotton_ring","botanical_sprite_body","botanical_sprite_wing"))if(models.getModel(ResourceLocation.fromNamespaceAndPath("tnc","block/"+id))==missing)failures.add("Missing additional plant model: "+id);
        BotanicalContent.BLOCKS.forEach((id,block)->{for(var state:block.getStateDefinition().getPossibleStates())if(mc.getBlockRenderer().getBlockModel(state)==missing)failures.add("Missing baked plant state: "+state);});
        var gearMesh=com.tnc.tnc.equipment.client.GearModels.class.getDeclaredMethod("mesh",com.tnc.tnc.equipment.MageGear.Design.class);gearMesh.setAccessible(true);
        for(var design:com.tnc.tnc.equipment.MageGear.ALL){
            try{var root=((net.minecraft.client.model.geom.builders.LayerDefinition)gearMesh.invoke(null,design)).bakeRoot();for(var bone:List.of("head","hat","body","right_arm","left_arm","right_leg","left_leg"))root.getChild(bone);}
            catch(Throwable failure){failures.add("Worn gear failed to load/bake: "+design.id()+" "+failure);}
        }
        for(String id:PastureRegistry.TYPES.keySet())for(String suffix:new String[]{"","_glow"})if(mc.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/pasture/"+id+suffix+".png")).isEmpty())failures.add("Missing animal texture: "+id+suffix);
        var report=new LinkedHashMap<String,Object>();report.put("registered_animal_renderers",renderers);report.put("baked_items",items.size());report.put("baked_plant_states",BotanicalContent.BLOCKS.values().stream().mapToInt(b->b.getStateDefinition().getPossibleStates().size()).sum());report.put("failures",failures);report.put("scope","Real client resource reload and renderer construction; not a gameplay screenshot or live animation review");
        Files.writeString(Path.of(System.getProperty("tnc.ecologyClientAuditOutput")),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("TN-C ECOLOGY CLIENT AUDIT: "+(failures.isEmpty()?"PASS":"FAIL")+" "+report);
        mc.stop();
    }
}
