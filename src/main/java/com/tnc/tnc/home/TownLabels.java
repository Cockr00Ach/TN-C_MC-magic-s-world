package com.tnc.tnc.home;
import com.google.gson.*;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.AdventureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Non-destructive door plates. No blocks are added to the source house preflight. */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class TownLabels {
    public record Label(String id,String number,String name,BlockPos entry){}
    public static final List<Label> ALL=read();
    private static List<Label> read(){try(var r=new InputStreamReader(Objects.requireNonNull(TownLabels.class.getResourceAsStream("/data/tnc/housing/labels.json")),StandardCharsets.UTF_8)){var list=new ArrayList<Label>();for(var e:JsonParser.parseReader(r).getAsJsonArray()){var o=e.getAsJsonObject();var p=o.getAsJsonArray("entry");list.add(new Label(o.get("id").getAsString(),o.get("number").getAsString(),o.get("name").getAsString(),new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt())));}return List.copyOf(list);}catch(IOException e){throw new ExceptionInInitializerError(e);}}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){if(event.phase!=TickEvent.Phase.END)return;var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null||server.getTickCount()%100!=0)return;var level=server.overworld();var origin=HousingService.sourceOrigin(level);if(origin==null||!com.tnc.tnc.npc.SkyIslandAnchors.isComplete(level))return;
        if(!com.tnc.tnc.world.SkyLandscapeUpgrade.complete(server))return;
        var store=AdventureSavedData.get(server);if(!store.housing.contains("DoorLabels",10))store.housing.put("DoorLabels",new CompoundTag());var records=store.housing.getCompound("DoorLabels");
        for(var label:ALL){var pos=origin.offset(label.entry());if(!level.hasChunkAt(pos))continue;var record=records.getCompound(label.id());
            if(record.hasUUID("UUID")){if(record.getInt("Format")<2&&level.getEntity(record.getUUID("UUID")) instanceof net.minecraft.world.entity.Display.TextDisplay display){configure(display,label);record.putInt("Format",2);records.put(label.id(),record);store.setDirty();}continue;}
            if(!level.isPositionEntityTicking(pos)||!level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(pos).toLong()))continue;
            var display=EntityType.TEXT_DISPLAY.create(level);if(display==null)continue;configure(display,label);display.moveTo(pos.getX()+.5,pos.getY()+2.2,pos.getZ()+.5);
            if(level.addFreshEntity(display)){record.putUUID("UUID",display.getUUID());record.putInt("Format",2);records.put(label.id(),record);store.setDirty();}
        }
    }
    static void configure(net.minecraft.world.entity.Display.TextDisplay display,Label label){
        var data=display.saveWithoutId(new CompoundTag());var json=new JsonObject();json.addProperty("text",label.number()+" · "+label.name());json.addProperty("color","gold");
        data.putString("text",json.toString());data.putString("billboard","center");data.putInt("line_width",180);data.putInt("background",0xcc142b38);data.putBoolean("shadow",true);data.putFloat("view_range",.35f);data.putBoolean("Invulnerable",true);
        var t=new CompoundTag();t.put("translation",floats(0,0,0));t.put("left_rotation",floats(0,0,0,1));t.put("scale",floats(.5f,.5f,.5f));t.put("right_rotation",floats(0,0,0,1));data.put("transformation",t);display.load(data);
    }
    private static ListTag floats(float...values){var a=new ListTag();for(float value:values)a.add(FloatTag.valueOf(value));return a;}
}
