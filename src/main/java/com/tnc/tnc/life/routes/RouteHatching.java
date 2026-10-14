package com.tnc.tnc.life.routes;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import com.tnc.tnc.life.pasture.*;
public final class RouteHatching {
    public static boolean hatch(ServerLevel l,BlockPos nest,UUID owner,ItemStack egg){var data=egg.getOrCreateTag();if(owner==null||!data.hasUUID("EggOwner")||!owner.equals(data.getUUID("EggOwner")))return false;var kind=PastureRegistry.TYPES.get(data.getString("Species"));if(kind==null)return false;
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){var p=nest.offset(dx,1,dz);if(!RouteAccess.allowed(l,p,owner)||!l.getBlockState(p).isAir()||!l.getBlockState(p.above()).isAir())continue;var chunk=new AABB((p.getX()>>4)<<4,l.getMinBuildHeight(),(p.getZ()>>4)<<4,((p.getX()>>4)+1)<<4,l.getMaxBuildHeight(),((p.getZ()>>4)+1)<<4);if(l.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class,chunk).size()>=24||l.getEntitiesOfClass(PastureAnimal.class,chunk,a->a.getType()==kind).size()>=8)continue;var child=kind.create(l);if(child==null)return false;var saved=new CompoundTag();saved.putUUID("PastureOwner",owner);saved.putInt("Age",-PastureSpecies.byId(data.getString("Species")).adultDays()*24000);saved.putLong("Home",p.asLong());child.readAdditionalSaveData(saved);child.setPos(p.getX()+.5,p.getY(),p.getZ()+.5);if(l.noCollision(child,child.getBoundingBox())&&l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,child.getBoundingBox()).isEmpty()&&l.addFreshEntity(child))return true;child.discard();}return false;
    }
}
