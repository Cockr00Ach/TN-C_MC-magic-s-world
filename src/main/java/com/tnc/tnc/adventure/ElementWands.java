package com.tnc.tnc.adventure;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.*;
import com.tnc.tnc.travel.TravelForms;
import com.tnc.tnc.travel.TravelKeyHint;
import com.tnc.tnc.travel.TravelStaff;
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
    private static final String[] TIERS={"","冒险者法杖","精良法杖","王级法杖","传说法杖","神杖"};
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
    /**
     * 这根法杖能承载哪些已学法术。
     *
     * <h2>规则（2026-11-09 作者拍板）</h2>
     * <b>不再按元素过滤</b>：知识在魔法石，任何法杖都能放玩家已学的法术，
     * 法杖只限制<b>阶位</b>（冒险者杖只放 1 阶，神杖放 1~5 阶）。
     * 同元素不再决定"能不能放"，只决定"放得疼不疼"——见 {@link WandResonance} 的 +25% 共鸣。
     *
     * <p>为什么要改：作者实测拿暗杖时魔法石里 10 个风系法术一个都不显示，
     * 看起来像"法术绑在某一根杖上"✗。元素杖的意义现在体现在<b>共鸣加成</b>上。
     */
    public static List<ResourceLocation> supported(Collection<ResourceLocation> learned,Design design){
        // Recompute highest learned spell within the weapon's capacity, not the player's
        // globally highest spell. A lower-tier wand never erases a learned higher tier.
        var chosen=new LinkedHashMap<String,SpellCatalog.Entry>();
        for(var e:SpellCatalog.all())if(learned.contains(e.id())&&e.tier()<=design.tier){
            String key=e.independent()?e.id().toString():e.element().id()+":"+e.chain().name();var old=chosen.get(key);if(old==null||old.tier()<e.tier())chosen.put(key,e);
        }
        // 不分元素之后一阶能凑出 7 系 ×4 链 = 28 条，远超引擎那 8 个槽位
        // （见 SpellEngineBridge.WAND_CAPACITY）。所以排序后截断：
        //   本元素优先 → 阶位高的优先 → 目录顺序（稳定，槽位不会每次刷新乱跳）
        var order=new ArrayList<SpellCatalog.Entry>(chosen.values());
        order.sort(java.util.Comparator
                .<SpellCatalog.Entry>comparingInt(e->e.element()==design.element?0:1)
                .thenComparingInt(e->-e.tier()));
        var ids=new ArrayList<ResourceLocation>();
        for(var e:order){
            if(ids.size()>=8)break;   // = SpellEngineBridge.WAND_CAPACITY
            ids.add(e.id());
        }
        // Placeholder domains are not weapon slots until they have real catalogue entries.
        return ids;
    }
    public static final class Wand extends Item implements TravelStaff {
        public final Design design;
        Wand(Design d){super(new Properties().stacksTo(1).rarity(d.tier>=4?Rarity.EPIC:d.tier>=2?Rarity.RARE:Rarity.UNCOMMON));design=d;}
        @Override public Element travelElement(){return design.element;}
        @Override public int travelTier(){return design.tier;}
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer){
            consumer.accept(new com.tnc.tnc.equipment.client.LuminousStaffRenderer.Extension());
        }
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
            lines.add(Component.literal("承载 1～"+design.tier+" 阶已学法术（不分元素）；知识保存在魔法石"));
            lines.add(Component.literal("共鸣：用它放"+design.element.cn()+"系法术，伤害 +25%"));
            lines.add(Component.literal("前往铸器师打造；神杖不会自动授予神法"));
            if(TravelForms.supported(design.element)){
                String key=TravelKeyHint.key();
                lines.add(Component.literal("元素附身 · "+TravelForms.formName(design.element)+"（长按 "+key+"；松手退出）"));
                lines.add(Component.literal("附身中右键 = "+TravelForms.skillName(design.element)+"；短按 "+key+" 不触发任何动作"));
            }else{
                lines.add(Component.literal("元素附身 · 尚未开放（长按 "+TravelKeyHint.key()+" 查看提示）"));
            }
            if(stack.hasTag()&&stack.getTag().contains("SpiritMana"))lines.add(Component.literal("同源温养 "+stack.getTag().getInt("SpiritMana")+" /10000M"));if(stack.hasTag()&&stack.getTag().contains("SpiritAffix"))lines.add(Component.literal("器灵觉醒 · "+design.element.cn()+"元素神技"));
        }
    }
}
