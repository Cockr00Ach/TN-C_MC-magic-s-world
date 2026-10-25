package com.tnc.tnc.life.botanical.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Opt-in native world photographs of all botanical species, each at four ages. */
public final class BotanicalNativeAudit {
    private static List<String> species;
    private static final List<String> failures=new ArrayList<>(),shots=new ArrayList<>();
    private static int startup,index,ticks;
    private static volatile boolean ready;
    private static boolean saving;
    private static Path folder(){return Path.of(System.getProperty("tnc.dungeonAuditOutput"));}
    public static void tick(TickEvent.ClientTickEvent event)throws Exception{
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        if(species==null){
            if(++startup<150)return;
            try(var reader=mc.getResourceManager().getResourceOrThrow(ResourceLocation.parse("tnc:plant_palettes.json")).openAsReader()){
                species=new ArrayList<>(JsonParser.parseReader(reader).getAsJsonObject().keySet());Collections.sort(species);
            }
            Files.createDirectories(folder());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);
            mc.options.enableVsync().set(false);mc.getWindow().updateVsync(false);mc.options.framerateLimit().set(60);
            mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.fov().set(38);
            mc.options.cloudStatus().set(CloudStatus.OFF);
            prepare(mc);return;
        }
        if(!ready||saving)return;
        mc.setScreen(null);mc.player.setYRot(0);mc.player.setXRot(14);
        if(++ticks==60){
            String name=String.format(Locale.ROOT,"%02d-%s.png",index,species.get(index));saving=true;
            Screenshot.grab(mc.gameDirectory,"botanical-"+name,mc.getMainRenderTarget(),message->{
                try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/botanical-"+name),folder().resolve(name),StandardCopyOption.REPLACE_EXISTING);shots.add(name);}
                catch(Exception e){failures.add(e.toString());}finally{saving=false;}
            });return;
        }
        if(ticks<65)return;
        if(++index==species.size()){
            Files.writeString(folder().resolve("report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("failures",failures,"screenshots",shots,"species",species.size(),"ages",4)));
            com.tnc.tnc.dungeon.client.WorldFeedbackNativeAudit.finished(mc);return;
        }
        ticks=0;prepare(mc);
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static BlockState age(BlockState state,int age){
        for(Property property:state.getProperties()){
            if(property.getName().equals("age")){
                int maximum=(int)Collections.max(property.getPossibleValues());state=state.setValue(property,Math.round(age*maximum/3f));
            }else if(property.getName().equals("blooming"))state=state.setValue(property,age==3);
            else if(property.getName().equals("wild"))state=state.setValue(property,true);
        }
        return state;
    }
    private static void prepare(Minecraft mc){
        ready=false;mc.getSingleplayerServer().execute(()->{try{
            var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var level=player.server.overworld();
            level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,player.server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,player.server);
            level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
            var soil=ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse("tnc:magic_soil"));
            var plant=ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse("tnc:"+species.get(index)));
            if(plant==null||plant==Blocks.AIR)throw new IllegalStateException("Missing plant "+species.get(index));
            for(var pos:BlockPos.betweenClosed(23988,189,23990,24012,198,24012))
                level.setBlock(pos,pos.getY()==189?Blocks.STONE.defaultBlockState():pos.getY()==190?Blocks.GRASS_BLOCK.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            for(int i=0;i<4;i++){
                var pos=new BlockPos(23996+i*2,191,24001);level.setBlock(pos.below(),soil.defaultBlockState(),2);
                level.setBlock(pos,age(plant.defaultBlockState(),i),2);
                // AGE is a projection of server growth, not an independent cheat.
                // Seed both so the live ticker does not reset stages to seedlings.
                if(level.getBlockEntity(pos) instanceof com.tnc.tnc.life.botanical.BotanicalPlantEntity be){
                    be.growth=be.species().firstTicks*i/3;be.owner=player.getUUID();be.ready=i==3;be.changed();
                }else if(level.getBlockEntity(pos) instanceof com.tnc.tnc.life.routes.RoutePlantEntity be){
                    be.growth=18000*i/3;be.owner=player.getUUID();be.setChanged();
                }
                var model=mc.getBlockRenderer().getBlockModel(age(plant.defaultBlockState(),i));
                if(model.getParticleIcon().contents().name().getPath().contains("missing"))failures.add("Missing texture "+species.get(index)+"/"+i);
            }
            player.setGameMode(GameType.SPECTATOR);player.removeAllEffects();player.getInventory().clearContent();
            for(var drop:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(23985,185,23985,24015,205,24015)))drop.discard();
            player.teleportTo(level,23999.5,192,23994.5,0,14);ready=true;
        }catch(Throwable e){failures.add(e.toString());ready=true;}});
    }
}
