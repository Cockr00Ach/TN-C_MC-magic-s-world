package com.tnc.tnc.bronze;

import java.util.EnumMap;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.entity.*;
import net.minecraftforge.registries.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class BronzeWeapons {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"tnc");
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,"tnc");
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES=DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS,"tnc");
    public static final EnumMap<BronzeProfile,RegistryObject<BronzeWeaponItem>> WEAPONS=new EnumMap<>(BronzeProfile.class);
    static {for(var profile:BronzeProfile.values())WEAPONS.put(profile,ITEMS.register(profile.id(),()->new BronzeWeaponItem(profile)));}
    public static final RegistryObject<EntityType<BronzeDaggerEntity>> DAGGER=ENTITIES.register("bronze_dagger_flight",()->EntityType.Builder.<BronzeDaggerEntity>of(BronzeDaggerEntity::new,MobCategory.MISC).sized(.25f,.25f).clientTrackingRange(10).updateInterval(1).build("tnc:bronze_dagger_flight"));
    public static final RegistryObject<RecipeSerializer<BronzeUpgradeRecipe>> UPGRADE=RECIPES.register("bronze_upgrade",()->new SimpleCraftingRecipeSerializer<>(BronzeUpgradeRecipe::new));
    public static void register(IEventBus bus){ITEMS.register(bus);ENTITIES.register(bus);RECIPES.register(bus);}
    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent event){
        if(event.getTabKey()==CreativeModeTabs.COMBAT)for(var item:WEAPONS.values())event.accept(item.get());
    }
}
