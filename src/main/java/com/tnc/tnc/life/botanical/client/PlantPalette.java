package com.tnc.tnc.life.botanical.client;
import java.util.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class PlantPalette {
 private static final Map<String,int[]> COLORS=load();
 private static Map<String,int[]> load(){try(var in=PlantPalette.class.getResourceAsStream("/assets/tnc/plant_palettes.json")){var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();Map<String,int[]> result=new HashMap<>();for(var e:json.entrySet())result.put(e.getKey(),java.util.stream.StreamSupport.stream(e.getValue().getAsJsonArray().spliterator(),false).mapToInt(com.google.gson.JsonElement::getAsInt).toArray());return Map.copyOf(result);}catch(java.io.IOException e){throw new IllegalStateException(e);}}
 public static int color(BlockState state,int tint){var id=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());var palette=id==null?null:COLORS.get(id.getPath());return palette==null||tint<0||tint>=palette.length?0xffffff:palette[tint];}
 @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Block e){for(var id:COLORS.keySet()){var b=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc",id));if(b!=null)e.register((s,l,p,t)->color(s,t),b);}}
}
