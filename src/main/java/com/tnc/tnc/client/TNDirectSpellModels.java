package com.tnc.tnc.client;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;

/** Plain resource keys load models/*.json; variant keys trigger ModernFix's blockstate loader. */
public final class TNDirectSpellModels {
    public static final List<String> PATHS=List.of("projectile/earth_clod","projectile/earth_orb",
            "projectile/earth_shell","projectile/earth_salvo","projectile/earth_spear",
            "projectile/earth_pillar","projectile/earth_mud_plate","projectile/earth_root",
            "projectile/wind_tornado","projectile/windblade","projectile/earth_spear_sharp","projectile/earth_spike_sharp","projectile/earth_seal");
    private TNDirectSpellModels() {}
    public static boolean contains(String path) {return PATHS.contains(path);}
    public static void register(ModelEvent.RegisterAdditional event) {
        for(String path:PATHS)event.register(ResourceLocation.fromNamespaceAndPath("tnc",path));
    }
}
