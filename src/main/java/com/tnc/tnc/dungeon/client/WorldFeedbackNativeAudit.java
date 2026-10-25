package com.tnc.tnc.dungeon.client;

import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;

/** Opt-in sequential fixtures: one isolated full-pack boot, no simultaneous JVMs. */
public final class WorldFeedbackNativeAudit {
    private static final String[] SUITES={"equipment","botanical","leyline","feedback","naming"};
    private static Path root;
    private static int suite;
    public static void tick(TickEvent.ClientTickEvent event)throws Exception{
        if(event.phase!=TickEvent.Phase.END)return;
        if(root==null){root=Path.of(System.getProperty("tnc.dungeonAuditOutput"));select();}
        switch(suite){
            case 0 -> com.tnc.tnc.equipment.client.EquipmentNativeAudit.tick(event);
            case 1 -> com.tnc.tnc.life.botanical.client.BotanicalNativeAudit.tick(event);
            case 2 -> LeylineNativeAudit.tick(event);
            case 3 -> com.tnc.tnc.arcana.client.FeedbackNativeAudit.tick(event);
            case 4 -> com.tnc.tnc.journey.client.NamingNativeAudit.tick(event);
        }
    }
    private static void select(){System.setProperty("tnc.dungeonAuditOutput",root.resolve(SUITES[suite]).toString());}
    public static void finished(Minecraft mc)throws Exception{
        if(!Boolean.getBoolean("tnc.worldFeedbackAudit")){mc.stop();return;}
        mc.setScreen(null);mc.setCameraEntity(mc.player);
        com.tnc.tnc.mana.sight.client.ManaSightClient.setEnabled(false);
        if(++suite<SUITES.length){select();return;}
        var sha=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(mc.gameDirectory.toPath().resolve("mods/tnc-1.0.0.jar"))));
        Files.writeString(root.resolve("complete.json"),new com.google.gson.Gson().toJson(Map.of("jar_sha256",sha,"suites",SUITES)));
        mc.stop();
    }
    private WorldFeedbackNativeAudit(){}
}
