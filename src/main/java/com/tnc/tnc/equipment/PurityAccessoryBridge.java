package com.tnc.tnc.equipment;

import java.lang.reflect.Method;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;

/** Cached optional bridge for the pack's Accessories implementation; no hard dev dependency. */
final class PurityAccessoryBridge {
    private static Method get,isEquipped;
    private static boolean initialized;
    static boolean equipped(LivingEntity entity,Item item){
        if(!initialized){
            initialized=true;
            if(ModList.get().isLoaded("accessories")){
                try{
                    var api=Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
                    get=api.getMethod("get",LivingEntity.class);isEquipped=api.getMethod("isEquipped",Item.class);
                }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot connect purity amulet to Accessories",failure);}
            }
        }
        if(get==null)return false;
        try{var cap=get.invoke(null,entity);return cap!=null&&(boolean)isEquipped.invoke(cap,item);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot read equipped purity amulet",failure);}
    }
}
