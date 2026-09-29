package com.tnc.tnc.life;

import com.tnc.tnc.TNMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.List;

@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class BossCuisine {
    public static final List<String> BOSSES=List.of("minecraft:ender_dragon","minecraft:wither","cataclysm:ignis","cataclysm:netherite_monstrosity","cataclysm:ender_guardian","cataclysm:the_leviathan","cataclysm:ancient_remnant","cataclysm:maledictus");
    @SubscribeEvent public static void drops(LivingDropsEvent e){
        if(e.getEntity().level().isClientSide())return;
        var key=ForgeRegistries.ENTITY_TYPES.getKey(e.getEntity().getType());int index=key==null?-1:BOSSES.indexOf(key.toString());
        if(index>=0)e.getDrops().add(new ItemEntity(e.getEntity().level(),e.getEntity().getX(),e.getEntity().getY(),e.getEntity().getZ(),new ItemStack(TNMod.BOSS_INGREDIENTS.get(index).get())));
    }
}
