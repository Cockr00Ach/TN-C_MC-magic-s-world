package com.tnc.tnc.equipment;

import com.tnc.tnc.TNMod;
import com.google.common.collect.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.sounds.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;
import java.util.*;

/** Two equipment slots, independent of elemental affinity. Identity comes from registered items. */
public final class MageGear {
    public record Design(String id,String name,String style,int tier,boolean hat,int armor,float toughness,
                         int mana,int regen,double speed,float knockback,boolean divine) {
        public EquipmentSlot slot(){return hat?EquipmentSlot.HEAD:EquipmentSlot.CHEST;}
        public int level(){return new int[]{0,1,20,45,70,85}[tier];}
        public int seconds(){return new int[]{0,20,40,70,120,300}[tier];}
        public int fee(){return divine?(hat?8000:12000):new int[]{0,40,220,1200,6000}[tier];}
        public String detail(){return "防御 "+armor+(toughness>0?" · 韧性 "+toughness:"")+(mana>0?" · 魔力 +"+mana:"")+(regen>0?" · 每2秒回魔 +"+regen:"")+(speed!=0?" · 移速 "+(speed>0?"+":"")+Math.round(speed*100)+"%":"")+(knockback>0?" · 抗击退 +"+Math.round(knockback*100)+"%":"")+(divine&&hat?" · 耗蓝 −8%":"");}
    }
    public static final List<Design> ALL=new ArrayList<>();
    public static final Map<String,RegistryObject<Item>> ITEMS=new LinkedHashMap<>();
    public static final String[] RANKS={"","冒险者","精练","大师","传说","神级"};
    static {
        for(int t=1;t<=4;t++){
            add("bastion",t,false,"壁垒战甲",at(t,8,11,13,15),at(t,0,1,2,3),0,0,-at(t,4,3,2,1)/100.0,0,false);
            add("astral",t,false,"星织长袍",at(t,3,5,7,9),0,at(t,30,65,110,170),0,0,0,false);
            add("runic",t,false,"秘纹锁甲",at(t,7,9,12,14),at(t,0,0,1,2),at(t,15,35,65,100),0,0,0,false);
            add("wanderer",t,false,"远行外衣",at(t,4,6,8,10),0,at(t,0,10,20,35),0,at(t,3,5,7,9)/100.0,0,false);
            add("bastion",t,true,"守望宽檐帽",at(t,2,3,4,5),0,0,0,0,at(t,0,5,10,15)/100f,false);
            add("astral",t,true,"观星尖帽",at(t,1,1,2,2),0,at(t,15,30,50,80),0,0,0,false);
            add("runic",t,true,"回响法冠",at(t,1,2,2,3),0,0,t,0,0,false);
            add("wanderer",t,true,"行旅兜帽",at(t,1,2,3,3),0,0,0,at(t,2,3,4,6)/100.0,0,false);
        }
        add("divine",5,false,"归墟神袍",16,4,200,4,.04,0,true);
        add("divine",5,true,"归墟星冠",4,0,100,3,0,0,true);
    }
    public static final RegistryObject<Item> BROKEN_ROBE=TNMod.ITEMS.register("broken_divine_robe",()->new RelicItem("破损的归墟神袍"));
    public static final RegistryObject<Item> BROKEN_HAT=TNMod.ITEMS.register("broken_divine_hat",()->new RelicItem("断辉星冠"));
    private static int at(int t,int...v){return v[t-1];}
    private static void add(String s,int t,boolean hat,String name,int armor,float tough,int mana,int regen,double speed,float knock,boolean god){
        String id=s+(hat?"_hat_":"_outfit_")+t;
        var d=new Design(id,RANKS[t]+" · "+name,s,t,hat,armor,tough,mana,regen,speed,knock,god);ALL.add(d);
        ITEMS.put(id,TNMod.ITEMS.register(id,()->new GearItem(d)));
    }
    public static void register(){}
    public static Design find(String id){return ALL.stream().filter(d->d.id.equals(id)).findFirst().orElse(null);}
    public static Design worn(LivingEntity p,EquipmentSlot slot){var item=p.getItemBySlot(slot).getItem();return item instanceof GearItem g?g.design:null;}
    public static int mana(LivingEntity p){return value(p,true);}
    public static int regen(LivingEntity p){return value(p,false);}
    private static int value(LivingEntity p,boolean mana){int n=0;for(var s:List.of(EquipmentSlot.CHEST,EquipmentSlot.HEAD)){var d=worn(p,s);if(d!=null)n+=mana?d.mana:d.regen;}return n;}
    public static int spellCost(LivingEntity p,int base){var d=worn(p,EquipmentSlot.HEAD);return d!=null&&d.divine&&base>0?Math.max(1,(int)Math.ceil(base*.92)):base;}
    public static ItemStack stack(Design d){return new ItemStack(ITEMS.get(d.id).get());}
    public static final class RelicItem extends Item {
        private final String name;RelicItem(String name){super(new Properties().stacksTo(1).rarity(Rarity.EPIC));this.name=name;}
        @Override public Component getName(ItemStack stack){return Component.literal(name);}
        @Override public void appendHoverText(ItemStack s,Level l,List<Component> text,TooltipFlag f){text.add(Component.literal("未修复遗物 · 带到炉石铁匠铺交给铎恩"));}
    }
    public static final class GearItem extends ArmorItem {
        public final Design design;
        GearItem(Design d){super(new Material(d),d.hat?Type.HELMET:Type.CHESTPLATE,new Properties().rarity(d.tier>=4?Rarity.EPIC:d.tier>=2?Rarity.RARE:Rarity.UNCOMMON));design=d;}
        @Override public Component getName(ItemStack stack){return Component.literal(design.name);}
        @Override public Multimap<Attribute,AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot){
            var base=super.getDefaultAttributeModifiers(slot);if(slot!=design.slot()||design.speed==0)return base;
            var builder=ImmutableMultimap.<Attribute,AttributeModifier>builder().putAll(base);
            builder.put(Attributes.MOVEMENT_SPEED,new AttributeModifier(UUID.nameUUIDFromBytes(("tnc_gear_speed_"+slot).getBytes(java.nio.charset.StandardCharsets.UTF_8)),"TN-C equipment speed",design.speed,AttributeModifier.Operation.MULTIPLY_BASE));return builder.build();
        }
        @Override public String getArmorTexture(ItemStack s,Entity entity,EquipmentSlot slot,String type){return "tnc:textures/models/armor/"+design.id+".png";}
        @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level l,net.minecraft.world.entity.player.Player p,net.minecraft.world.InteractionHand hand){
            if(!design.hat&&(!p.getItemBySlot(EquipmentSlot.LEGS).isEmpty()||!p.getItemBySlot(EquipmentSlot.FEET).isEmpty())){
                if(!l.isClientSide())p.displayClientMessage(Component.literal("身甲已包含裤鞋，请先卸下这两件装备。"),true);
                return net.minecraft.world.InteractionResultHolder.fail(p.getItemInHand(hand));
            }return super.use(l,p,hand);
        }
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer){consumer.accept(new com.tnc.tnc.equipment.client.GearModels.Extension());}
        @Override public void appendHoverText(ItemStack s,Level l,List<Component> text,TooltipFlag f){text.add(Component.literal(design.hat?"魔法帽 · 头部":"完整身甲 · 胸甲槽穿戴，包含腿部与靴面"));text.add(Component.literal(design.detail()));if(design.divine)text.add(Component.literal("两件归墟遗物：致命伤保留1生命并回复20%魔力，冷却8分钟"));}
    }
    private record Material(Design d) implements ArmorMaterial {
        public int getDurabilityForType(ArmorItem.Type type){return new int[]{0,600,1000,1600,2400,4000}[d.tier];}
        public int getDefenseForType(ArmorItem.Type type){return d.armor;}
        public int getEnchantmentValue(){return 12+d.tier*2;}
        public SoundEvent getEquipSound(){return d.style.equals("bastion")||d.style.equals("runic")?SoundEvents.ARMOR_EQUIP_CHAIN:SoundEvents.ARMOR_EQUIP_LEATHER;}
        public Ingredient getRepairIngredient(){return Ingredient.of(d.style.equals("bastion")||d.style.equals("runic")?Items.IRON_INGOT:Items.LEATHER);}
        public String getName(){return "tnc:"+d.id;}
        public float getToughness(){return d.toughness;}
        public float getKnockbackResistance(){return d.knockback;}
    }
}
