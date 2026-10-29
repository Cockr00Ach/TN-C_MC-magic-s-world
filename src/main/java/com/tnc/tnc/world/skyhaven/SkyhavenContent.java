package com.tnc.tnc.world.skyhaven;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.item.*;
import net.minecraftforge.fluids.*;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Separate sea fluid. Never connected to the irrigation-mana registry or energy network. */
@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class SkyhavenContent {
 public static final ResourceKey<Level> KEY=ResourceKey.create(Registries.DIMENSION,id("skyhaven"));
 public static FluidType TYPE;public static FlowingFluid SOURCE,FLOWING;public static LiquidBlock SEA;public static Item BUCKET;
 private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("tnc",name);}
 private static ForgeFlowingFluid.Properties properties(){return new ForgeFlowingFluid.Properties(()->TYPE,()->SOURCE,()->FLOWING).block(()->SEA).bucket(()->BUCKET).tickRate(5).slopeFindDistance(4).levelDecreasePerBlock(1);}
 @SubscribeEvent public static void register(RegisterEvent e){
  e.register(net.minecraftforge.registries.ForgeRegistries.Keys.FLUID_TYPES,h->{TYPE=new FluidType(FluidType.Properties.create().density(1200).viscosity(1200).canSwim(true).canDrown(true).lightLevel(0)){
   @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer){consumer.accept(new net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions(){
    public ResourceLocation getStillTexture(){return ResourceLocation.fromNamespaceAndPath("minecraft","block/water_still");}
    public ResourceLocation getFlowingTexture(){return ResourceLocation.fromNamespaceAndPath("minecraft","block/water_flow");}
    public int getTintColor(){return 0xF2080B12;}
   });}
  };h.register(id("abyssal_mana_water"),TYPE);});
  e.register(Registries.FLUID,h->{SOURCE=new ForgeFlowingFluid.Source(properties());FLOWING=new ForgeFlowingFluid.Flowing(properties());h.register(id("abyssal_mana_water"),SOURCE);h.register(id("abyssal_mana_water_flowing"),FLOWING);});
  e.register(Registries.BLOCK,h->{SEA=new LiquidBlock(()->SOURCE,net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(Blocks.WATER));h.register(id("abyssal_mana_water"),SEA);});
  e.register(Registries.ITEM,h->{BUCKET=new BucketItem(()->SOURCE,new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1));h.register(id("abyssal_mana_water_bucket"),BUCKET);});
 }
 @Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
 public static class Client {
  @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->{net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(SOURCE,net.minecraft.client.renderer.RenderType.translucent());net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(FLOWING,net.minecraft.client.renderer.RenderType.translucent());});}
 }
 private SkyhavenContent(){}
}

