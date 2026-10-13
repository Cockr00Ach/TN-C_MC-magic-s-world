package com.tnc.tnc.life.routes;

import com.tnc.tnc.TNMod;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.*;
import net.minecraftforge.registries.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.*;
import java.util.*;

/** All factories run during registry events, never against frozen registries. */
public final class RouteContent {
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"tnc");
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"tnc");
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPES=DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS,"tnc");
    public static final RegistryObject<net.minecraft.world.item.crafting.RecipeSerializer<RouteCraftRecipe>> CRAFT_SERIALIZER=RECIPES.register("mana_container_crafting",()->new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(RouteCraftRecipe::new));
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"tnc");
    public static final DeferredRegister<FluidType> FLUID_TYPES=DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES,"tnc");
    public static final DeferredRegister<Fluid> FLUIDS=DeferredRegister.create(ForgeRegistries.FLUIDS,"tnc");
    public static final RegistryObject<FluidType> MANA_TYPE=FLUID_TYPES.register("flowing_mana",()->new FluidType(FluidType.Properties.create().density(1000).viscosity(1000).lightLevel(3).canSwim(true).canDrown(false)) {
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer){consumer.accept(new net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions(){
            public net.minecraft.resources.ResourceLocation getStillTexture(){return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft","block/water_still");}
            public net.minecraft.resources.ResourceLocation getFlowingTexture(){return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft","block/water_flow");}
            public int getTintColor(){return 0xAA52DBC8;}
        });}
    });
    public static final RegistryObject<FlowingFluid> MANA=FLUIDS.register("flowing_mana",()->new ManaFluid.Source(fluidProperties()));
    public static final RegistryObject<FlowingFluid> MANA_FLOW=FLUIDS.register("flowing_mana_flow",()->new ManaFluid.Flowing(fluidProperties()));
    public static final RegistryObject<LiquidBlock> MANA_LIQUID=BLOCKS.register("flowing_mana",()->new LiquidBlock(MANA,net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(Blocks.WATER).lightLevel(s->3)));
    public static final RegistryObject<Item> MANA_BUCKET=ITEMS.register("flowing_mana_bucket",ManaBucketItem::new);
    private static ForgeFlowingFluid.Properties fluidProperties(){return new ForgeFlowingFluid.Properties(MANA_TYPE,MANA,MANA_FLOW).bucket(MANA_BUCKET).block(MANA_LIQUID).slopeFindDistance(4).levelDecreasePerBlock(1).tickRate(5);}
    public static final RegistryObject<Block> SOIL=BLOCKS.register("magic_soil",()->new MagicSoilBlock(false));
    public static final RegistryObject<Block> SOIL_SOURCE=BLOCKS.register("magic_soil_source",()->new MagicSoilBlock(true));
    public static final RegistryObject<BlockEntityType<MagicSoilEntity>> SOIL_ENTITY=ENTITIES.register("magic_soil_source",()->BlockEntityType.Builder.of(MagicSoilEntity::new,SOIL_SOURCE.get()).build(null));
    public static final Map<String,RegistryObject<? extends Block>> NODES=new LinkedHashMap<>();
    public static final Map<String,RegistryObject<? extends Block>> PLANTS=new LinkedHashMap<>();
    public static final Map<String,RegistryObject<Item>> GOODS=new LinkedHashMap<>();
    static {
        ITEMS.register("magic_soil",()->new BlockItem(SOIL.get(),new Item.Properties()));
        ITEMS.register("magic_soil_source",()->new BlockItem(SOIL_SOURCE.get(),new Item.Properties()));
        for(RouteKind kind:RouteKind.values()){
            var block=BLOCKS.register(kind.id,()->new RouteNodeBlock(kind));NODES.put(kind.id,block);
            GOODS.put(kind.id,ITEMS.register(kind.id,()->new BlockItem(block.get(),new Item.Properties())));
        }
        for(NewPlantKind kind:NewPlantKind.values()){
            var block=BLOCKS.register(kind.id,()->new RoutePlantBlock(kind));PLANTS.put(kind.id,block);
            GOODS.put(kind.id+"_seed",ITEMS.register(kind.id+"_seed",()->new ItemNameBlockItem(block.get(),new Item.Properties())));
            GOODS.put(kind.product,ITEMS.register(kind.product,()->new Item(new Item.Properties())));
        }
        for(String id:new String[]{"spirit_iron","radiant_gold","star_marrow"})GOODS.put(id,ITEMS.register(id,()->new Item(new Item.Properties())));
        GOODS.put("packed_meal",ITEMS.register("packed_meal",()->new Item(new Item.Properties().food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(8).saturationMod(.6f).build()))));
        for(RouteAccessory.Kind kind:RouteAccessory.Kind.values())GOODS.put(kind.id,ITEMS.register(kind.id,()->kind==RouteAccessory.Kind.MECH?new RouteMechItem():new RouteAccessory(kind)));
        GOODS.put("mana_meter",ITEMS.register("mana_meter",RouteMeterItem::new));
        for(String id:new String[]{"season_handbook","magic_garden_book","beast_ranch_book","mana_workshop_book"})GOODS.put(id,ITEMS.register(id,()->new RouteBookItem(id)));
    }
    public static final RegistryObject<BlockEntityType<RouteNodeEntity>> NODE_ENTITY=ENTITIES.register("mana_route_node",()->BlockEntityType.Builder.of(RouteNodeEntity::new,NODES.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<RoutePlantEntity>> PLANT_ENTITY=ENTITIES.register("mana_route_plant",()->BlockEntityType.Builder.of(RoutePlantEntity::new,PLANTS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,"tnc");
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<RouteMenu>> MENU=MENUS.register("mana_route",()->net.minecraftforge.common.extensions.IForgeMenuType.create(RouteMenu::new));
    public static void register(IEventBus bus){RouteInputNetwork.register();BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);FLUID_TYPES.register(bus);FLUIDS.register(bus);MENUS.register(bus);RECIPES.register(bus);bus.addListener(RouteContent::creative);}
    private static void creative(net.minecraftforge.event.BuildCreativeModeTabContentsEvent e){if(e.getTabKey().equals(TNMod.TNC_TAB.getKey())){ITEMS.getEntries().forEach(i->e.accept(i.get()));}}
    public static Item item(String id){var own=GOODS.get(id);return own!=null?own.get():ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.tryParse(id.contains(":")?id:"tnc:"+id));}
    private RouteContent(){}
}
