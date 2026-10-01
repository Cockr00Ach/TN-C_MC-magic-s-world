package com.tnc.tnc.adventure;

import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.*;
import java.util.*;

/** Uses the installed furniture mod's serializer, seats, inventory and light implementation. */
public final class FurnitureCompat {
    private static final String DATA="net.conczin.immersive_furniture.data.FurnitureData";
    public static ItemStack stack(ShopCatalog.Goods goods){
        if(!goods.item().startsWith("model:")){var item=ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(goods.item()));return item==null?ItemStack.EMPTY:new ItemStack(item,goods.count());}
        var item=ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("immersive_furniture:furniture"));if(item==null||item==Items.AIR)return ItemStack.EMPTY;
        try{
            var cls=Class.forName(DATA);var model=cls.getConstructor().newInstance();cls.getField("name").set(model,goods.name());cls.getField("author").set(model,"RouchNao · 朝夕商行");
            @SuppressWarnings("unchecked") var elements=(List<Object>)cls.getField("elements").get(model);
            String kind=goods.item().substring(6);String wood="minecraft:spruce_planks",cloth=kind.equals("chair")?"minecraft:brown_wool":"minecraft:blue_wool";
            switch(kind){
                case "chair","armchair"->{box(elements,2,7,2,14,9,14,cloth);legs(elements,2,2,12,12,7,wood);box(elements,2,9,12,14,21,14,wood);box(elements,3,11,11.7f,13,18,12,cloth);seat(elements,8,9,7);if(kind.equals("armchair")){box(elements,1,9,2,3,13,14,wood);box(elements,13,9,2,15,13,14,wood);}}
                case "table"->{box(elements,0,12,0,16,15,16,"minecraft:oak_planks");legs(elements,1,1,13,13,12,wood);}
                case "sofa"->{cls.getField("size").set(model,new Vector3i(2,2,1));box(elements,1,3,2,31,5,14,wood);box(elements,2,5,2,30,9,13,cloth);box(elements,1,9,12,31,18,15,cloth);box(elements,0,5,1,3,14,14,wood);box(elements,29,5,1,32,14,14,wood);seat(elements,9,9,7);seat(elements,23,9,7);}
                case "cabinet"->{box(elements,1,0,1,15,16,15,wood);box(elements,2,1,0,14,7,1,"minecraft:oak_planks");box(elements,2,8,0,14,14,1,"minecraft:oak_planks");box(elements,7,3,-.5f,9,4,0,"minecraft:iron_block");box(elements,7,10,-.5f,9,11,0,"minecraft:iron_block");cls.getField("inventorySize").setInt(model,9);}
                case "bookcase"->{box(elements,1,0,11,15,24,14,wood);box(elements,1,0,1,3,24,14,wood);box(elements,13,0,1,15,24,14,wood);for(int y:new int[]{0,8,16,23})box(elements,1,y,1,15,y+1,14,"minecraft:oak_planks");for(int x:new int[]{4,7,10}){box(elements,x,1,7,x+2,7,12,"minecraft:red_wool");box(elements,x,9,7,x+2,15,12,cloth);}}
                case "lamp"->{box(elements,3,0,3,13,2,13,wood);box(elements,7,2,7,9,23,9,wood);box(elements,2,23,2,14,28,14,"minecraft:yellow_wool");cls.getField("lightLevel").setInt(model,12);}
                default->{return ItemStack.EMPTY;}
            }
            if(!kind.equals("sofa"))cls.getField("size").set(model,new Vector3i(1,kind.equals("lamp")||kind.equals("bookcase")||kind.equals("chair")||kind.equals("armchair")?2:1,1));
            var stack=new ItemStack(item,goods.count());item.getClass().getMethod("setData",ItemStack.class,cls).invoke(null,stack,model);return stack;
        }catch(ReflectiveOperationException|LinkageError e){com.mojang.logging.LogUtils.getLogger().error("朝夕家具生成失败：{}",goods.id(),e);return ItemStack.EMPTY;}
    }
    private static Object element(List<Object> list,float x,float y,float z,float xx,float yy,float zz)throws ReflectiveOperationException{
        var cls=Class.forName(DATA+"$Element");var e=cls.getConstructor().newInstance();cls.getField("from").set(e,new Vector3f(x,y,z));cls.getField("to").set(e,new Vector3f(xx,yy,zz));list.add(e);return e;
    }
    private static void box(List<Object> list,float x,float y,float z,float xx,float yy,float zz,String texture)throws ReflectiveOperationException{var e=element(list,x,y,z,xx,yy,zz);var m=e.getClass().getField("material").get(e);m.getClass().getField("source").set(m,ResourceLocation.parse(texture));}
    private static void legs(List<Object> list,int x,int z,int xx,int zz,int h,String wood)throws ReflectiveOperationException{for(int a:new int[]{x,xx})for(int b:new int[]{z,zz})box(list,a,0,b,a+2,h,b+2,wood);}
    @SuppressWarnings({"rawtypes","unchecked"}) private static void seat(List<Object> list,float x,float y,float z)throws ReflectiveOperationException{var e=element(list,x,y,z,x,y,z);var type=Class.forName(DATA+"$ElementType");e.getClass().getField("type").set(e,Enum.valueOf((Class)type,"PLAYER_POSE"));}
}
