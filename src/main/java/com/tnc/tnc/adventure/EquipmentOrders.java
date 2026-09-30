package com.tnc.tnc.adventure;
import com.tnc.tnc.equipment.MageGear;
import net.minecraft.world.item.*;
import java.util.*;
import java.util.function.Supplier;
/** Old paid orders remain resolvable; the new catalogue uses two equipment slots. */
public final class EquipmentOrders {
    public record Recipe(String id,Supplier<Item> output,int ore,int fee,int level,int seconds,List<ContractCatalog.Material> materials){public Item item(){return output.get();}}
    public static final List<Recipe> ALL=new ArrayList<>();
    private static ContractCatalog.Material mat(String id,int n){return new ContractCatalog.Material(id.contains(":")?id:"minecraft:"+id,false,n);}
    static {
        legacy("helmet",Items.IRON_HELMET,5,20);legacy("chestplate",Items.IRON_CHESTPLATE,8,30);legacy("leggings",Items.IRON_LEGGINGS,7,25);legacy("boots",Items.IRON_BOOTS,4,15);legacy("sword",Items.IRON_SWORD,2,10);
        for(var d:MageGear.ALL){
            var m=new LinkedHashMap<String,Integer>();
            if(d.divine()){
                m.put(d.hat()?"tnc:broken_divine_hat":"tnc:broken_divine_robe",1);m.put("nether_star",1);m.put("echo_shard",d.hat()?4:8);m.put("netherite_ingot",d.hat()?2:4);m.put("diamond",d.hat()?4:8);
            }else{
                switch(d.style()){
                    case "bastion"->{m.put("raw_iron",d.hat()?4:12);m.put("coal",d.hat()?1:2);}
                    case "astral"->{m.put("leather",d.hat()?3:8);m.put("string",d.hat()?5:12);m.put("lapis_lazuli",d.hat()?3:4);}
                    case "runic"->{if(d.hat()){m.put("copper_ingot",4);m.put("amethyst_shard",2);m.put("string",3);}else{m.put("raw_iron",8);m.put("string",8);m.put("copper_ingot",4);}}
                    case "wanderer"->{m.put("leather",d.hat()?4:10);m.put("feather",d.hat()?3:6);m.put("string",d.hat()?3:6);}
                }
                double factor=new double[]{0,1,1.5,2,2.5}[d.tier()];m.replaceAll((id,n)->(int)Math.ceil(n*factor));
                if(d.tier()==2)m.merge("amethyst_shard",4,Integer::sum);
                if(d.tier()==3){m.merge("amethyst_shard",8,Integer::sum);m.put("diamond",3);}
                if(d.tier()==4){m.put("diamond",6);m.put("netherite_ingot",1);m.put("echo_shard",2);}
            }
            ALL.add(new Recipe(d.id(),()->MageGear.ITEMS.get(d.id()).get(),0,d.fee(),d.level(),d.seconds(),m.entrySet().stream().map(e->mat(e.getKey(),e.getValue())).toList()));
        }
    }
    private static void legacy(String id,Item item,int ore,int fee){ALL.add(new Recipe("equipment_"+id,()->item,ore,fee,1,20,List.of(mat("raw_iron",ore),mat("coal",1))));}
    public static Recipe find(String id){return ALL.stream().filter(r->r.id.equals(id)).findFirst().orElse(null);}
}
