package com.tnc.tnc.adventure;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;
import java.util.*;

/** Seven independent silhouettes per tier; item identity is never inferred from client NBT. */
public final class ElementWands {
    public record Design(String id,Element element,int tier,String name,long fee,int seconds,List<ContractCatalog.Material> materials){}
    public static final List<Design> ALL=new ArrayList<>();
    public static final Map<String,RegistryObject<Item>> ITEMS=new LinkedHashMap<>();
    private static final String[] TIERS={"","冒险者法阵","精良法杖","王级法杖","传说法杖","神杖"};
    static {
        var gods=Map.of(Element.WATER,"傲慢的水龙王",Element.FIRE,"冠烬烈阳",Element.LIGHTNING,"审判的天穹",Element.WIND,"无拘的长风",Element.EARTH,"不动的山君",Element.LIGHT,"不灭的晨星",Element.DARK,"吞夜的君主");
        var gems=Map.of(Element.WATER,"prismarine_shard",Element.FIRE,"blaze_powder",Element.LIGHTNING,"amethyst_shard",Element.WIND,"feather",Element.EARTH,"quartz",Element.LIGHT,"glowstone_dust",Element.DARK,"ender_pearl");
        long[] fees={0,30,250,1500,8000,50000};int[] times={0,20,35,60,90,120};
        for(var e:Element.values())for(int tier=1;tier<=5;tier++){
            String id=e.id()+"_wand_"+tier;String cn=e==Element.DARK?"暗":e.cn();
            var mats=new ArrayList<ContractCatalog.Material>();mats.add(new ContractCatalog.Material("minecraft:stick",false,4));mats.add(new ContractCatalog.Material("minecraft:copper_ingot",false,2));
            if(tier>=2)mats.add(new ContractCatalog.Material("minecraft:"+gems.get(e),false,tier*2));
            if(tier>=3)mats.add(new ContractCatalog.Material("minecraft:diamond",false,tier-1));
            if(tier>=4)mats.add(new ContractCatalog.Material("minecraft:netherite_ingot",false,tier-3));
            if(tier==5)mats.add(new ContractCatalog.Material("minecraft:nether_star",false,1));
            var d=new Design(id,e,tier,tier==5?gods.get(e):cn+" · "+TIERS[tier],fees[tier],times[tier],List.copyOf(mats));ALL.add(d);
            ITEMS.put(id,TNMod.ITEMS.register(id,()->new Wand(d)));
        }
    }
    public static void register(){} // Called while TNMod's DeferredRegister is being populated.
    public static Design find(String id){return ALL.stream().filter(d->d.id.equals(id)).findFirst().orElse(null);}
    public static ItemStack stack(Design d){return new ItemStack(ITEMS.get(d.id).get());}
    public static List<ResourceLocation> supported(Collection<ResourceLocation> learned,Design design){
        // Recompute highest learned spell within the weapon's capacity, not the player's
        // globally highest spell. A lower-tier wand never erases a learned higher tier.
        var chosen=new LinkedHashMap<String,SpellCatalog.Entry>();
        for(var e:SpellCatalog.all())if(learned.contains(e.id())&&(e.element()==design.element||e.chain()==SpellCatalog.Chain.INDEPENDENT)&&e.tier()<=design.tier){
            String key=e.independent()?e.id().toString():e.element().id()+":"+e.chain().name();var old=chosen.get(key);if(old==null||old.tier()<e.tier())chosen.put(key,e);
        }
        var ids=new ArrayList<ResourceLocation>();chosen.values().forEach(e->ids.add(e.id()));
        // Placeholder domains are not weapon slots until they have real catalogue entries.
        return ids;
    }
    public static final class Wand extends Item {
        public final Design design;
        Wand(Design d){super(new Properties().stacksTo(1).rarity(d.tier>=4?Rarity.EPIC:d.tier>=2?Rarity.RARE:Rarity.UNCOMMON));design=d;}
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
            lines.add(Component.literal("承载同元素1～"+design.tier+"阶已学法术；知识保存在魔法石"));
            lines.add(Component.literal("前往铸器师打造；神杖不会自动授予神法"));
        }
    }
}
