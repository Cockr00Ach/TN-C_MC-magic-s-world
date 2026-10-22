package com.tnc.tnc.alchemy;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import java.util.*;

/** One shared inventory for a four-block kettle; closing its lid freezes the actual fusion. */
public final class AlchemyEntity extends BlockEntity implements MenuProvider {
    public static final int COOK_TICKS=120;
    public UUID owner;
    public int temperature,vortex_sync,overheated,perfectRemaining,readyQuality=1;
    public boolean ruined,hasIgnited,sealed;public long heatedAt=-100;
    public int progress,burn,fire,water,burst,color=0x64d8c9,cookTicks;
    public long pouredAt=-100,sealedAt=-100;
    public String job="",ready="",frozenJob="";
    public boolean working;
    public final SimpleContainer inventory=new SimpleContainer(7){@Override public void setChanged(){super.setChanged();AlchemyEntity.this.setChanged();}};
    public AlchemyEntity(BlockPos p,BlockState s){super(AlchemyContent.ENTITY,p,s);}
    public Vec3 center(){return Vec3.atLowerCornerOf(worldPosition).add(1,.7,1);}
    public boolean mayUse(ServerPlayer p){return p.isAlive()&&!p.isSpectator()&&!com.tnc.tnc.combat.DownedCombat.isDowned(p)&&(owner==null||owner.equals(p.getUUID())||p.isCreative());}
    public boolean ignite(boolean magic){
        if(!magic||water==0||!ready.isEmpty())return false;
        if(level!=null&&level.getGameTime()-heatedAt>=20){temperature=Math.min(1000,temperature+180);heatedAt=level.getGameTime();}
        hasIgnited=true;fire=2;burn=1600;changed();sound(SoundEvents.FIRECHARGE_USE,.7f,1.25f);return true;
    }
    public static void igniteNear(ServerLevel l,BlockPos p,net.minecraft.world.entity.Entity caster){if(!(caster instanceof ServerPlayer player))return;var seen=new HashSet<BlockPos>();for(BlockPos q:BlockPos.betweenClosed(p.offset(-1,-1,-1),p.offset(1,1,1))){var be=AlchemyBlock.master(l,q);if(be!=null&&seen.add(be.worldPosition)&&be.mayUse(player))be.ignite(true);}}
    public static void coolNear(ServerLevel l,BlockPos pos,net.minecraft.world.entity.Entity caster){if(!(caster instanceof ServerPlayer p))return;var seen=new HashSet<BlockPos>();for(BlockPos q:BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,1,1))){var be=AlchemyBlock.master(l,q);if(be!=null&&seen.add(be.worldPosition)&&be.mayUse(p)&&l.getGameTime()-be.heatedAt>=10){be.temperature=Math.max(0,be.temperature-220);be.heatedAt=l.getGameTime();be.changed();}}}
    public Elixir recipe(){var input=new ArrayList<String>();for(int i=0;i<3;i++)if(!inventory.getItem(i).isEmpty())input.add(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(inventory.getItem(i).getItem()).toString());return Arrays.stream(Elixir.values()).filter(e->e.matches(input)).findFirst().orElse(null);}
    public static boolean herb(ItemStack s){if(s.isEmpty())return false;String id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).toString();return Arrays.stream(Elixir.values()).anyMatch(e->e.ingredients.contains(id));}
    public boolean insert(ItemStack held){if(sealed||!ready.isEmpty()||water==0||progress>0||!herb(held))return false;for(int i=0;i<3;i++)if(inventory.getItem(i).is(held.getItem()))return false;for(int i=0;i<3;i++)if(inventory.getItem(i).isEmpty()){inventory.setItem(i,held.split(1));changed();sound(SoundEvents.GENERIC_SPLASH,.3f,1.5f);return true;}return false;}
    public boolean addWater(){if(sealed||water!=0||progress>0||!ready.isEmpty())return false;hasIgnited=false;water=3;pouredAt=level==null?-100:level.getGameTime();color=0x428fb7;changed();sound(SoundEvents.BUCKET_EMPTY,.8f,1);return true;}
    public String status(){
        if(!ready.isEmpty())return (readyQuality==3?"古怪废液":new String[]{"浑浊 · ","标准 · ","臻品 · 大师 · "}[readyQuality]+Elixir.find(ready).name)+" · 用玻璃瓶盛取";
        if(water==0)return "水桶倒水 · 一桶可炼三剂";
        if(sealed)return "已封盖 · 融合已锁定 · 保持绿色温区，后煮 "+cookTicks+" / "+COOK_TICKS;
        if(recipe()==null)return "往四格锅口投三味药材 · 无需燃料和搅拌";
        if(fire!=2)return "用火法命中锅体点燃";
        if(ruined)return "漩涡已散成黑雾 · 盖锅收取废液";
        if(perfectRemaining>0)return "三个漩涡已合一！盖锅锁定此刻药性";
        return temperature<360?"锅周蓝光 · 用火法升温":temperature>620?"锅周红光 · 用水/冰法降温":"锅周绿光 · 药材自动靠拢融合";
    }
    public void interact(ServerPlayer p,InteractionHand hand){
        if(!mayUse(p))return;var held=p.getItemInHand(hand);
        if(held.is(Items.BOOK)||held.is(Items.WRITABLE_BOOK)||held.is(Items.WRITTEN_BOOK)){net.minecraftforge.network.NetworkHooks.openScreen(p,this,b->b.writeBlockPos(worldPosition));return;}
        if(held.is(com.tnc.tnc.arcana.ArcanaContent.item("sealing_lid"))){if(seal(p)){if(!p.isCreative())inventory.setItem(6,held.split(1));changed();}}
        else if(held.is(Items.WATER_BUCKET)){if(addWater()&&!p.isCreative())p.setItemInHand(hand,new ItemStack(Items.BUCKET));}
        else if(held.is(Items.GLASS_BOTTLE)&&!ready.isEmpty()){
            var result=new ItemStack(readyQuality==3?com.tnc.tnc.arcana.ArcanaContent.item("strange_waste"):AlchemyContent.POTIONS.get(Elixir.find(ready)));
            result.getOrCreateTag().putInt("RitualQuality",readyQuality);result.getOrCreateTag().putInt("Fusion",vortex_sync);if(!p.isCreative())held.shrink(1);give(p,result);
            if(!inventory.getItem(6).isEmpty())give(p,inventory.removeItemNoUpdate(6));
            ready="";job="";frozenJob="";progress=0;vortex_sync=0;perfectRemaining=0;ruined=false;hasIgnited=false;sealed=false;cookTicks=0;fire=0;burn=0;AlchemyBlock.close(level,worldPosition,false);changed();sound(SoundEvents.BOTTLE_FILL,.7f,1);
        }else if(p.isShiftKeyDown()&&held.isEmpty()&&!sealed){for(int i=0;i<7;i++)if(!inventory.getItem(i).isEmpty())give(p,inventory.removeItemNoUpdate(i));progress=0;job="";fire=0;burn=0;working=false;vortex_sync=0;perfectRemaining=0;ruined=false;hasIgnited=false;changed();}
        else if(held.is(Items.FLINT_AND_STEEL)||held.is(Items.FIRE_CHARGE))p.displayClientMessage(Component.literal("普通火无效，药锅只响应火系法术。"),true);
        else insert(held);
        p.displayClientMessage(Component.literal(status()),true);
    }
    private static void give(ServerPlayer p,ItemStack s){if(!p.getInventory().add(s))p.drop(s,false);}
    public void absorbDrops(ServerLevel l){if(water==0||sealed||!ready.isEmpty())return;for(var item:l.getEntitiesOfClass(ItemEntity.class,new AABB(worldPosition.getX()+.05,worldPosition.getY()+.1,worldPosition.getZ()+.05,worldPosition.getX()+1.95,worldPosition.getY()+1.7,worldPosition.getZ()+1.95)))if(item.getOwner() instanceof ServerPlayer p&&mayUse(p)&&insert(item.getItem())){if(item.getItem().isEmpty())item.discard();else item.setItem(item.getItem());break;}}
    public static void tick(Level level,BlockPos pos,BlockState state,AlchemyEntity be){
        if(!(level instanceof ServerLevel l))return;if(be.burst>0)be.burst--;if(level.getGameTime()%4==0)be.absorbDrops(l);be.working=false;
        if(be.fire==2&&be.water>0&&be.ready.isEmpty()&&be.burn>0){be.burn--;be.temperature=Math.min(1000,be.temperature+1);}else{be.fire=0;be.temperature=Math.max(0,be.temperature-1);}
        var recipe=be.recipe();
        if(be.ready.isEmpty()&&recipe!=null){be.color=recipe.color;
            if(!be.sealed&&be.temperature>800&&be.fire==2){if(++be.overheated>=100){be.ruined=true;be.readyQuality=3;be.frozenJob=recipe.id;be.finish(recipe);l.sendParticles(net.minecraft.core.particles.ParticleTypes.SQUID_INK,pos.getX()+1,pos.getY()+1,pos.getZ()+1,60,1,.6,1,.03);for(var victim:l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new AABB(pos).inflate(3)))victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,100,0));}}
            else be.overheated=Math.max(0,be.overheated-2);
            if(be.ready.isEmpty()&&be.sealed){if(recipe.id.equals(be.frozenJob)&&be.fire==2&&com.tnc.tnc.arcana.RitualRules.sweet(be.temperature)){be.working=true;if(++be.cookTicks>=COOK_TICKS)be.finish(recipe);}}
            else if(be.ready.isEmpty()){
                if(!be.job.equals(recipe.id)){be.job=recipe.id;be.progress=0;be.vortex_sync=0;}
                if(be.fire==2&&be.water>0&&!be.ruined&&com.tnc.tnc.arcana.RitualRules.sweet(be.temperature)&&be.progress<recipe.ticks){be.working=true;be.progress++;be.vortex_sync=Math.min(100,be.progress*100/recipe.ticks);if(be.vortex_sync==100&&be.perfectRemaining==0){be.perfectRemaining=30;be.burst=20;be.sound(SoundEvents.AMETHYST_CLUSTER_HIT,1.1f,1.6f);}}
                if(be.vortex_sync>=100&&be.perfectRemaining>0){if(--be.perfectRemaining==0&&be.fire==2)be.ruined=true;}else if(be.vortex_sync>=100&&be.fire==2)be.ruined=true;
            }
        }
        if(level.getGameTime()%5==0){be.setChanged();be.sync();}
    }
    public boolean seal(ServerPlayer p){var e=recipe();if(sealed||e==null||!ready.isEmpty()||!hasIgnited)return false;sealed=true;sealedAt=level.getGameTime();frozenJob=e.id;cookTicks=0;readyQuality=com.tnc.tnc.arcana.RitualRules.quality(vortex_sync,perfectRemaining,ruined).ordinal();AlchemyBlock.close(level,worldPosition,true);changed();sound(SoundEvents.COPPER_PLACE,.8f,.65f);return true;}
    private void finish(Elixir e){
        if(!ready.isEmpty())return;
        for(int i=0;i<3;i++)if(!inventory.getItem(i).isEmpty()){var stack=inventory.getItem(i);var remainder=stack.getCraftingRemainingItem();stack.shrink(1);if(!remainder.isEmpty())Containers.dropItemStack(level,worldPosition.getX()+1,worldPosition.getY()+1.2,worldPosition.getZ()+1,remainder);}
        water=Math.max(0,water-1);ready=e.id;working=false;fire=0;burn=0;burst=70;AlchemyBlock.close(level,worldPosition,sealed);changed();sound(SoundEvents.BREWING_STAND_BREW,.8f,1.1f);
    }
    private void sound(SoundEvent s,float volume,float pitch){if(level!=null)level.playSound(null,worldPosition,s,SoundSource.BLOCKS,volume,pitch);}
    public void changed(){setChanged();sync();}
    @Override public Component getDisplayName(){return Component.literal("魔焰药典");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player p){return new AlchemyMenu(id,inv,this);}
    @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);var items=NonNullList.withSize(7,ItemStack.EMPTY);for(int i=0;i<7;i++)items.set(i,inventory.getItem(i));ContainerHelper.saveAllItems(t,items);if(owner!=null)t.putUUID("Owner",owner);t.putInt("RitualVersion",3);t.putBoolean("HasIgnited",hasIgnited);t.putBoolean("Sealed",sealed);t.putInt("CookTicks",cookTicks);t.putString("FrozenJob",frozenJob);t.putInt("temperature",temperature);t.putInt("vortex_sync",vortex_sync);t.putInt("Overheated",overheated);t.putInt("PerfectRemaining",perfectRemaining);t.putBoolean("Ruined",ruined);t.putInt("ReadyQuality",readyQuality);t.putLong("HeatedAt",heatedAt);t.putInt("Progress",progress);t.putInt("Burn",burn);t.putInt("Fire",fire);t.putInt("Water",water);t.putLong("PouredAt",pouredAt);t.putLong("SealedAt",sealedAt);t.putInt("Burst",burst);t.putString("Ready",ready);t.putString("Job",job);t.putBoolean("Working",working);t.putInt("Color",color);}
    @Override public void load(CompoundTag t){super.load(t);var list=NonNullList.withSize(7,ItemStack.EMPTY);ContainerHelper.loadAllItems(t,list);for(int i=0;i<7;i++)inventory.setItem(i,list.get(i));owner=t.hasUUID("Owner")?t.getUUID("Owner"):null;progress=Math.max(0,Math.min(480,t.getInt("Progress")));burn=Math.max(0,Math.min(1600,t.getInt("Burn")));fire=t.getInt("Fire")==2?2:0;hasIgnited=t.getBoolean("HasIgnited")||fire==2;water=Math.max(0,Math.min(3,t.getInt("Water")));pouredAt=t.contains("PouredAt")?t.getLong("PouredAt"):-100;burst=Math.max(0,Math.min(70,t.getInt("Burst")));temperature=com.tnc.tnc.arcana.RitualRules.clamp(t.getInt("temperature"),1000);vortex_sync=com.tnc.tnc.arcana.RitualRules.clamp(t.getInt("vortex_sync"),100);overheated=com.tnc.tnc.arcana.RitualRules.clamp(t.getInt("Overheated"),100);perfectRemaining=com.tnc.tnc.arcana.RitualRules.clamp(t.getInt("PerfectRemaining"),30);ruined=t.getBoolean("Ruined");readyQuality=t.contains("ReadyQuality")?com.tnc.tnc.arcana.RitualRules.clamp(t.getInt("ReadyQuality"),3):1;heatedAt=t.contains("HeatedAt")?t.getLong("HeatedAt"):-100;ready=Elixir.find(t.getString("Ready"))==null?"":t.getString("Ready");job=t.getString("Job");working=t.getBoolean("Working");color=t.getInt("Color");sealed=t.getBoolean("Sealed");sealedAt=t.contains("SealedAt")?t.getLong("SealedAt"):-100;cookTicks=com.tnc.tnc.arcana.RitualRules.clamp(t.getInt("CookTicks"),COOK_TICKS);frozenJob=t.getString("FrozenJob");}
    @Override public AABB getRenderBoundingBox(){return new AABB(worldPosition).inflate(5);}
    @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    private void sync(){if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
}