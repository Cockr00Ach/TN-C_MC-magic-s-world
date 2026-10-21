package com.tnc.tnc.arcana;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.core.BlockPos;
public final class ArcanaSpirits {
    public static void tick(ServerPlayer p,boolean sight,double sanity){if(!sight||sanity<=80)return;var t=ArcanaPhysiology.data(p);long now=ArcanaPhysiology.now(p);if(t.hasUUID("Spirit")&&p.serverLevel().getEntity(t.getUUID("Spirit")) instanceof DimensionalSpirit)return;if(t.getLong("NextSpirit")>now)return;
        double a=p.getRandom().nextDouble()*Math.PI*2,r=16+p.getRandom().nextInt(17);var pos=BlockPos.containing(p.getX()+Math.cos(a)*r,p.getY(),p.getZ()+Math.sin(a)*r);if(!p.serverLevel().hasChunkAt(pos))return;
        var spirit=ArcanaContent.SPIRIT.create(p.serverLevel());if(spirit==null)return;spirit.owner=p.getUUID();spirit.setPos(pos.getX()+.5,p.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ())+1.3,pos.getZ()+.5);if(p.serverLevel().addFreshEntity(spirit)){t.putUUID("Spirit",spirit.getUUID());t.putLong("NextSpirit",now+6000);}
    }
    public static boolean allowed(ServerPlayer p,DimensionalSpirit spirit){return p.isAlive()&&!p.isSpectator()&&!com.tnc.tnc.combat.DownedCombat.isDowned(p)&&p.getUUID().equals(spirit.owner)&&p.serverLevel()==spirit.level()&&p.distanceToSqr(spirit)<64&&com.tnc.tnc.mana.sight.ManaSightServer.enabled(p)&&ArcanaPhysiology.data(p).getDouble("vision_sanity")>80;}
    public static void open(ServerPlayer p,DimensionalSpirit spirit){if(!allowed(p,spirit))return;var t=new CompoundTag();t.putString("Type","contract");t.putInt("Entity",spirit.getId());ArcanaNetwork.sync(p,t);}
    public static void trade(ServerPlayer p,int entity,int offer){if(offer<0||offer>3||!(p.serverLevel().getEntity(entity) instanceof DimensionalSpirit spirit)||!allowed(p,spirit))return;
        var record=ArcanaPhysiology.data(p);long now=ArcanaPhysiology.now(p);if(record.getLong("Pact"+offer)>now)return;
        ItemStack reward=new ItemStack(ArcanaContent.item(new String[]{"dimensional_mana_prism","forbidden_scroll","void_parasitic_seed","astral_wand_core"}[offer]));
        boolean room=false;for(int i=0;i<36;i++){var s=p.getInventory().getItem(i);if(s.isEmpty()||ItemStack.isSameItemSameTags(s,reward)&&s.getCount()<s.getMaxStackSize()){room=true;break;}}if(!room)return;
        if(offer==0){if(p.getHealth()<2)return;p.setHealth(Math.max(.5f,p.getHealth()*.1f));}
        else if(offer==1){if(p.experienceLevel<3)return;p.giveExperienceLevels(-3);}
        else if(offer==2){var w=p.getMainHandItem();if(!(w.getItem() instanceof SwordItem)&&!(w.getItem() instanceof AxeItem)||w.isEnchanted()||w.getMaxDamage()<=0||w.getDamageValue()>w.getMaxDamage()/2)return;w.shrink(1);}
        else{int slot=-1;for(int i=0;i<36;i++){var s=p.getInventory().getItem(i);if(s.is(ArcanaContent.item("elixir_focus"))&&s.hasTag()&&s.getTag().getInt("RitualQuality")==2&&s.getTag().getInt("Fusion")==100){slot=i;break;}}if(slot<0)return;p.getInventory().getItem(slot).shrink(1);}
        if(!p.getInventory().add(reward))p.drop(reward,false);if(offer==3&&!p.getInventory().add(new ItemStack(Items.GLASS_BOTTLE)))p.drop(new ItemStack(Items.GLASS_BOTTLE),false);record.putLong("Pact"+offer,now+24000);com.tnc.tnc.journey.JourneyData.get(p.server).setDirty();p.sendSystemMessage(net.minecraft.network.chat.Component.literal("契约已成。代价已经付出。"));
    }
}
