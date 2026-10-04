package com.tnc.tnc.life.ecology;

import com.tnc.tnc.TNMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Registers only species whose source, harvest and consumer have been implemented. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class EcologyContent {
    public static VerdantVeinBlock VERDANT;
    public static Item SEED,BRANCH,INSULATING_CLAY;
    public static BlockEntityType<VerdantVeinBlockEntity> VERDANT_ENTITY;
    private EcologyContent(){}
    public static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(TNMod.MODID,path);}
    @SubscribeEvent public static void register(RegisterEvent event){
        event.register(Registries.BLOCK,h->{if(VERDANT==null){VERDANT=new VerdantVeinBlock();h.register(id("verdant_vein"),VERDANT);}});
        event.register(Registries.ITEM,h->{if(SEED==null){SEED=new ItemNameBlockItem(VERDANT,new Item.Properties()){
            @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack,net.minecraft.world.level.Level level,java.util.List<net.minecraft.network.chat.Component> text,net.minecraft.world.item.TooltipFlag flags){
                text.add(net.minecraft.network.chat.Component.literal("普通野外土壤可种；根旁一格水、白天日光充足才长。"));
                text.add(net.minecraft.network.chat.Component.literal("成熟储魔40，每秒供1；留株发电，用剪刀取导魔枝则重新生长。"));
                text.add(net.minecraft.network.chat.Component.literal("湿林野株可取种；旧地图用剪刀右键水边蕨，十次采样必得。"));
            }
        };BRANCH=new Item(new Item.Properties());INSULATING_CLAY=new Item(new Item.Properties());h.register(id("verdant_vein_seed"),SEED);h.register(id("verdant_branch"),BRANCH);h.register(id("insulating_forge_clay"),INSULATING_CLAY);}});
        event.register(Registries.BLOCK_ENTITY_TYPE,h->{if(VERDANT_ENTITY==null){VERDANT_ENTITY=BlockEntityType.Builder.of(VerdantVeinBlockEntity::new,VERDANT).build(null);h.register(id("verdant_vein"),VERDANT_ENTITY);}});
    }
    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent event){if(event.getTabKey().equals(TNMod.TNC_TAB.getKey())){event.accept(SEED);event.accept(BRANCH);event.accept(INSULATING_CLAY);}}
}
