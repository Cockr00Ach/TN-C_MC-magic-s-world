package com.tnc.tnc.sanctum;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Per-tick rigid SCM2 contact regions relative to the real carrier feet, with local +Z forward. */
final class FalseGodContacts {
    static final String[] ROLES={"head","chest","hips","forearm_l","forearm_r","hand_l","hand_r",
            "wing_l","wing_r","foot_l","foot_r"};
    private static final Map<String,float[][][]> POSES=load();
    private static Map<String,float[][][]> load() {
        var stream=FalseGodContacts.class.getResourceAsStream("/data/tnc/sanctum/false_god_contacts.bin.gz");
        if(stream==null)throw new IllegalStateException("Missing false god contact poses");
        try(var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(stream)))){
            if(in.readInt()!=0x46474331 || in.readUnsignedByte()!=ROLES.length)throw new IOException("Unknown contact format");
            for(var role:ROLES)if(!role.equals(in.readUTF()))throw new IOException("Unexpected contact role");
            var result=new HashMap<String,float[][][]>();
            int count=in.readUnsignedByte();
            for(int i=0;i<count;i++){
                var key=in.readUTF();int ticks=in.readUnsignedShort();
                if(ticks<1||ticks>2048)throw new IOException("Invalid contact duration "+key);
                var poses=new float[ticks][ROLES.length][6];
                for(var frame:poses)for(var box:frame)for(int k=0;k<6;k++)box[k]=in.readShort()/128F;
                if(result.put(key,poses)!=null)throw new IOException("Duplicate contact move "+key);
            }
            if(!result.containsKey("idle")||in.read()!=-1)throw new IOException("Incomplete contact resource");
            return Map.copyOf(result);
        }catch(IOException e){throw new IllegalStateException("Invalid false god contact poses",e);}
    }
    /** Read-only rows are center XYZ and half-extents XYZ. A zero width suppresses a target-steered limb. */
    static float[][] pose(String action,int age) {
        var move=action==null?null:POSES.get(action);
        if(move==null)move=POSES.get("idle");
        return move[Math.max(0,Math.min(age,move.length-1))];
    }
    private FalseGodContacts(){}
}
