package com.tnc.tnc.world.ink;

/** Per-feather opacity; applied to cubes only, never inherited by child bones. */
public final class InkWingOpacity {
    private InkWingOpacity() {}
    public static float alpha(String bone) {
        if(bone.startsWith("folded_wing_")&&bone.contains("_feather_")){
            int feather;
            try{feather=Integer.parseInt(bone.substring(bone.lastIndexOf('_')+1));}
            catch(NumberFormatException renamedFeather){return 1F;}
            return feather>=10?.5F:feather>=5?.25F:1F;
        }
        if (!bone.startsWith("wing_")) return 1F;
        if (bone.contains("_primary_") || bone.contains("_covert_2_")) return .5F;
        if (bone.endsWith("_fore") || bone.contains("_covert_1_")) return .25F;
        return 1F;
    }
}
