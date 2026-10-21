package com.tnc.tnc.arcana;

import com.tnc.tnc.life.routes.*;
import com.tnc.tnc.production.*;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** Conditions are checked both while working and at the actual material transaction. */
public final class ArcanaCrafting {
    public static boolean celestial(ManaForgeRecipe recipe){return recipe.getId().getPath().equals("arcana/aurora_wings")||recipe.getId().getPath().equals("arcana/astral_wand_core");}
    public static boolean forbidden(ManaForgeRecipe recipe){return celestial(recipe)||recipe.getId().getPath().equals("arcana/abyss_mana_tower")||recipe.getId().getPath().equals("arcana/singularity_mana_core");}
    private static RouteNodeEntity feed(ServerLevel l,MagicForgeBlockEntity f){for(var side:Direction.values()){var q=f.getBlockPos().relative(side);if(l.hasChunkAt(q)&&l.getBlockEntity(q) instanceof RouteNodeEntity n&&f.owner()!=null&&f.owner().equals(n.owner)&&ArcanaEnergy.amount(n,"astral")>=200)return n;}return null;}
    public static boolean allowed(ServerLevel l,MagicForgeBlockEntity f,ManaForgeRecipe r){
        if(forbidden(r)){var data=com.tnc.tnc.journey.JourneyData.get(l.getServer()).player(f.owner()).getCompound("DeepArcana");if(!data.getBoolean("ForbiddenTech"))return false;}
        return !celestial(r)||ArcanaSky.aurora(l)&&l.canSeeSky(f.getBlockPos().above(2))&&feed(l,f)!=null;
    }
    public static boolean pay(ServerLevel l,MagicForgeBlockEntity f,ManaForgeRecipe r){if(!allowed(l,f,r))return false;if(!celestial(r))return true;var n=feed(l,f);return n!=null&&ArcanaEnergy.spend(n,"astral",200);}
    private ArcanaCrafting(){}
}
