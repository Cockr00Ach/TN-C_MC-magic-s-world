package com.tnc.tnc.dungeon.mirror;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/** Copies the visible wearable slots to a client-only player for the pack's Accessories render layer. */
public final class MirrorAccessories {
    private record Api(Method capability, Method containers, Method slots, Method cosmetics,
                       Method visible, Method size, Method flags) {}
    private static final Api API = api();
    private MirrorAccessories() {}

    private static Api api() {
        if (!ModList.get().isLoaded("accessories")) return null;
        try {
            Class<?> cap = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
            Class<?> container = Class.forName("io.wispforest.accessories.api.AccessoriesContainer");
            return new Api(cap.getMethod("get",LivingEntity.class),cap.getMethod("getContainers"),
                    container.getMethod("getAccessories"),container.getMethod("getCosmeticAccessories"),
                    container.getMethod("shouldRender",int.class),container.getMethod("getSize"),container.getMethod("renderOptions"));
        } catch (ReflectiveOperationException e) {
            LogUtils.getLogger().warn("Unable to read Accessories visual slots for mirror opponents",e);
            return null;
        }
    }

    private static Map<?,?> slots(LivingEntity player) throws ReflectiveOperationException {
        Object cap=API.capability.invoke(null,player);
        return cap==null ? Map.of() : (Map<?,?>)API.containers.invoke(cap);
    }

    /** Keep the actual stacks (including capabilities) while functional accessory slots are empty. */
    static ListTag suspend(net.minecraft.server.level.ServerPlayer player) {
        var saved=new ListTag();
        if(API==null)return saved;
        try {
            var active=new java.util.LinkedHashMap<String,SimpleContainer>();
            for(var e:slots(player).entrySet())active.put(e.getKey().toString(),(SimpleContainer)API.slots.invoke(e.getValue()));
            for(var e:active.entrySet()){
                var container=e.getValue();
                for(int i=0;i<container.getContainerSize();i++){
                    var item=container.getItem(i);
                    if(item.isEmpty())continue;
                    var row=new CompoundTag();
                    row.putString("Type",e.getKey());row.putInt("Slot",i);row.put("Item",item.save(new CompoundTag()));
                    saved.add(row);
                }
            }
            for(int i=0;i<saved.size();i++){
                var row=saved.getCompound(i);
                active.get(row.getString("Type")).setItem(row.getInt("Slot"),ItemStack.EMPTY);
            }
        } catch(ReflectiveOperationException e){LogUtils.getLogger().warn("Reading equipped accessories for mirror duel",e);}
        return saved;
    }
    static void restore(net.minecraft.server.level.ServerPlayer player,ListTag saved,List<ItemStack> overflow){
        if(saved.isEmpty())return;
        Map<?,?> groups=Map.of();
        try{if(API!=null)groups=slots(player);}catch(ReflectiveOperationException e){LogUtils.getLogger().warn("Restoring duel accessories to inventory",e);}
        for(int i=0;i<saved.size();i++){
            var row=saved.getCompound(i);var item=ItemStack.of(row.getCompound("Item"));int slot=row.getInt("Slot");
            Object group=groups.get(row.getString("Type"));
            if(group!=null)try{
                var active=(SimpleContainer)API.slots.invoke(group);
                if(slot>=0 && slot<active.getContainerSize() && active.getItem(slot).isEmpty()){active.setItem(slot,item);continue;}
            } catch(ReflectiveOperationException e){LogUtils.getLogger().warn("Restoring individual duel accessory to inventory",e);}
            overflow.add(item);
        }
    }
    public static ListTag snapshot(LivingEntity player) {
        ListTag out=new ListTag();
        if (API==null) return out;
        try {
            for (var e:slots(player).entrySet()) {
                Object c=e.getValue();
                var regular=(SimpleContainer)API.slots.invoke(c);
                var cosmetic=(SimpleContainer)API.cosmetics.invoke(c);
                int count=(int)API.size.invoke(c);
                for (int i=0;i<count;i++) {
                    if (!(boolean)API.visible.invoke(c,i)) continue;
                    ItemStack a=regular.getItem(i), b=cosmetic.getItem(i);
                    if (a.isEmpty()&&b.isEmpty()) continue;
                    CompoundTag row=new CompoundTag();
                    row.putString("Type",e.getKey().toString()); row.putInt("Slot",i);
                    if(!a.isEmpty()) row.put("Item",visual(a));
                    if(!b.isEmpty()) row.put("Cosmetic",visual(b));
                    out.add(row);
                }
            }
        } catch (ReflectiveOperationException ex) { LogUtils.getLogger().warn("Reading mirror wearable slots",ex); }
        return out;
    }
    private static CompoundTag visual(ItemStack stack) {
        CompoundTag item=stack.save(new CompoundTag());
        item.remove("ForgeCaps");
        if (item.contains("tag",10)) {
            CompoundTag n=item.getCompound("tag"); n.remove("BlockEntityTag"); n.remove("Items");
        }
        return item;
    }

    public static void apply(LivingEntity visualPlayer,ListTag data) {
        if (API==null || data.isEmpty()) return;
        try {
            var slots=slots(visualPlayer);
            for (int n=0;n<data.size();n++) {
                var row=data.getCompound(n); var c=slots.get(row.getString("Type"));
                if(c==null) continue;
                int index=row.getInt("Slot");
                if(index<0 || index >= (int)API.size.invoke(c)) continue;
                ((SimpleContainer)API.slots.invoke(c)).setItem(index,ItemStack.of(row.getCompound("Item")));
                ((SimpleContainer)API.cosmetics.invoke(c)).setItem(index,ItemStack.of(row.getCompound("Cosmetic")));
                @SuppressWarnings("unchecked") List<Boolean> flags=(List<Boolean>)API.flags.invoke(c);
                if(index<flags.size()&&!flags.get(index)) flags.set(index,true);
            }
        } catch (ReflectiveOperationException ex) { LogUtils.getLogger().warn("Displaying mirror wearable slots",ex); }
    }
}
