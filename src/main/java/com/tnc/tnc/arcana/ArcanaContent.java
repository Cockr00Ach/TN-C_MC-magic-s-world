package com.tnc.tnc.arcana;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraftforge.registries.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class ArcanaContent {
    public static net.minecraft.world.level.block.Block TOWER_BRICK,RED_GRASS;
    public static EntityType<VoidDevourer> DEVOURER;public static EntityType<DimensionalSpirit> SPIRIT;
    public static EntityType<ArcanaWasteFlask> WASTE_FLASK;
    public static net.minecraft.world.effect.MobEffect CORROSION;
    public static final String[] GOODS={"sealing_lid","strange_waste","overdraft_elixir","gigantism_elixir","miniature_elixir","flame_twist_elixir","fission_elixir","capture_shrink_elixir","soothing_bath_salt","mana_ash","collapsed_mana_singularity","abyss_tower_brick","symbiosis_marker","giant_mana_fruit","slime_pudding","moon_honey_cake","forbidden_scroll","void_parasitic_seed","aurora_wings","astral_wand_core","shadow_mana_crystal"};
    public static Item item(String id){return ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id.contains(":")?id:"tnc:"+id));}
    @SubscribeEvent public static void register(RegisterEvent e){
        e.register(Registries.MOB_EFFECT,h->{CORROSION=new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL,0x84b956){};CORROSION.addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,"74687d4c-319f-488f-8c27-310533be950f",-.5,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL);h.register(ResourceLocation.parse("tnc:mana_corrosion"),CORROSION);});
        e.register(Registries.ENTITY_TYPE,h->{WASTE_FLASK=EntityType.Builder.<ArcanaWasteFlask>of(ArcanaWasteFlask::new,MobCategory.MISC).sized(.25f,.25f).clientTrackingRange(4).updateInterval(10).build("tnc:arcana_waste_flask");h.register(ResourceLocation.parse("tnc:arcana_waste_flask"),WASTE_FLASK);});
        e.register(Registries.BLOCK,h->{TOWER_BRICK=new net.minecraft.world.level.block.Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.DEEPSLATE_BRICKS).strength(5));RED_GRASS=new net.minecraft.world.level.block.Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.CRIMSON_NYLIUM));h.register(ResourceLocation.parse("tnc:abyss_tower_brick"),TOWER_BRICK);h.register(ResourceLocation.parse("tnc:enchanted_red_grass"),RED_GRASS);});
        e.register(Registries.ENTITY_TYPE,h->{DEVOURER=EntityType.Builder.of(VoidDevourer::new,MobCategory.MONSTER).sized(3.6f,4.2f).clientTrackingRange(12).fireImmune().build("tnc:void_devourer");h.register(ResourceLocation.parse("tnc:void_devourer"),DEVOURER);SPIRIT=EntityType.Builder.of(DimensionalSpirit::new,MobCategory.MISC).sized(2,2.8f).clientTrackingRange(12).fireImmune().build("tnc:dimensional_spirit");h.register(ResourceLocation.parse("tnc:dimensional_spirit"),SPIRIT);});
        e.register(Registries.ITEM,h->{for(String id:GOODS)if(!java.util.Arrays.stream(com.tnc.tnc.alchemy.Elixir.values()).anyMatch(q->q.agricultural()&&q.outputId().equals(id)))h.register(ResourceLocation.parse("tnc:"+id),id.equals("abyss_tower_brick")?new BlockItem(TOWER_BRICK,new Item.Properties()):new ArcanaItem(id));h.register(ResourceLocation.parse("tnc:enchanted_red_grass"),new BlockItem(RED_GRASS,new Item.Properties()));for(String id:new String[]{"thorn_tyrant_vine","emerald_lifemoss","predator_bud"})h.register(ResourceLocation.parse("tnc:"+id),new LivingArmor(id));h.register(ResourceLocation.parse("tnc:tailwind_boots"),new ArcanaItem("tailwind_boots"));});
    }
    @SubscribeEvent public static void attributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent e){e.put(DEVOURER,VoidDevourer.attributes().build());e.put(SPIRIT,DimensionalSpirit.attributes().build());}
    @SubscribeEvent public static void creative(net.minecraftforge.event.BuildCreativeModeTabContentsEvent e){if(e.getTabKey().equals(com.tnc.tnc.TNMod.TNC_TAB.getKey())){for(String id:GOODS)e.accept(item(id));for(String id:new String[]{"thorn_tyrant_vine","emerald_lifemoss","predator_bud","tailwind_boots"})e.accept(item(id));}}
    public static final class LivingArmor extends ArmorItem {
        public final String id;public LivingArmor(String id){super(ArmorMaterials.NETHERITE,Type.CHESTPLATE,new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));this.id=id;}
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer){consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions(){@Override public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(net.minecraft.world.entity.LivingEntity living,ItemStack stack,EquipmentSlot slot,net.minecraft.client.model.HumanoidModel<?> original){return com.tnc.tnc.arcana.client.ArcanaArmorModels.get(id,original);}});}
        @Override public String getArmorTexture(ItemStack stack,net.minecraft.world.entity.Entity entity,EquipmentSlot slot,String type){return "tnc:textures/block/art_leaf.png";}
    }
}
