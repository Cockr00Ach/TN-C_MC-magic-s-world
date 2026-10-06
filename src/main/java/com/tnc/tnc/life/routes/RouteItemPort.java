package com.tnc.tnc.life.routes;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.*;
import net.minecraftforge.items.wrapper.InvWrapper;
/** Optional hopper interface: material slots insert only, finished slots extract only. */
final class RouteItemPort implements IItemHandler {
 private final RouteNodeEntity node;private final Direction side;private final InvWrapper inventory;
 RouteItemPort(RouteNodeEntity node,Direction side){this.node=node;this.side=side;inventory=new InvWrapper(node.inventory);}
 private boolean allowed(){if(node.isRemoved()||!(node.getLevel() instanceof net.minecraft.server.level.ServerLevel l)||side==null||!RouteAccess.allowed(l,node.getBlockPos(),node.owner)||!RouteAccess.allowed(l,node.getBlockPos().relative(side),node.owner))return false;var a=com.tnc.tnc.home.HousingService.ownedAt(l,node.getBlockPos());var b=com.tnc.tnc.home.HousingService.ownedAt(l,node.getBlockPos().relative(side));return a==null?b==null:b!=null&&a.id().equals(b.id());}
 public int getSlots(){return 6;}public ItemStack getStackInSlot(int slot){return allowed()?inventory.getStackInSlot(slot):ItemStack.EMPTY;}
 public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return allowed()&&slot<4?inventory.insertItem(slot,stack,simulate):stack;}
 public ItemStack extractItem(int slot,int amount,boolean simulate){return allowed()&&slot>=4?inventory.extractItem(slot,amount,simulate):ItemStack.EMPTY;}
 public int getSlotLimit(int slot){return 64;}public boolean isItemValid(int slot,ItemStack stack){return slot>=0&&slot<4;}
}
