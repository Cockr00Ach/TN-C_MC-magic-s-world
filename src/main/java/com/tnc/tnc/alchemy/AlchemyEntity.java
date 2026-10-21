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

/** All recipe progress is world-side; the menu is only a read-only recipe book. */
public final class AlchemyEntity extends BlockEntity implements MenuProvider {
    public UUID owner;
    public int temperature,vortex_sync,overheated,perfectRemaining,readyQuality=1;public boolean ruined,hasIgnited;public long heatedAt=-100;
    public int progress,burn,fire,water,stirs,burst,color=0x64d8c9;
    public long stirredAt=-100,pouredAt=-100;
    public String job="",ready="";
    public boolean working;
    // Keep old slot indices for lossless recovery of packed cauldrons.
    public final SimpleContainer inventory=new SimpleContainer(7){
        @Override public void setChanged(){super.setChanged();AlchemyEntity.this.setChanged();}
    };
    public AlchemyEntity(BlockPos p,BlockState s){super(AlchemyContent.ENTITY,p,s);}
    public boolean mayUse(ServerPlayer p){return p.isAlive()&&!p.isSpectator()
            &&!com.tnc.tnc.combat.DownedCombat.isDowned(p)&&(owner==null||owner.equals(p.getUUID())||p.isCreative());}
    public boolean ignite(boolean magic){
        if(!magic||water==0||(!inventory.getItem(4).is(AlchemyContent.FUEL)&&burn==0)||!ready.isEmpty())return false;
        if(level!=null&&level.getGameTime()-heatedAt>=20){temperature=Math.min(1000,temperature+180);heatedAt=level.getGameTime();}hasIgnited=true;fire=2;changed();sound(SoundEvents.FIRECHARGE_USE,.7f,1.25f);return true;
    }
    public static void igniteNear(ServerLevel l,BlockPos p,net.minecraft.world.entity.Entity caster){
        if(!(caster instanceof ServerPlayer player))return;
        for(BlockPos q:BlockPos.betweenClosed(p.offset(-1,-1,-1),p.offset(1,1,1)))
            if(l.hasChunkAt(q)&&l.getBlockEntity(q) instanceof AlchemyEntity be&&be.mayUse(player))be.ignite(true);
    }
    public Elixir recipe(){
        var input=new ArrayList<String>();
        for(int i=0;i<4;i++)if(!inventory.getItem(i).isEmpty())
            input.add(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(inventory.getItem(i).getItem()).toString());
        return Arrays.stream(Elixir.values()).filter(e->e.matches(input)).findFirst().orElse(null);
    }
    public static boolean herb(ItemStack s){
        if(s.isEmpty())return false;
        String id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).toString();
        return Arrays.stream(Elixir.values()).anyMatch(e->e.ingredients.contains(id));
    }
    public boolean insert(ItemStack held){
        if(!ready.isEmpty()||water==0)return false;
        if(held.is(AlchemyContent.FUEL)){
            if(!inventory.getItem(4).isEmpty()||burn>0)return false;
            inventory.setItem(4,held.split(1));changed();sound(SoundEvents.AMETHYST_BLOCK_CHIME,.4f,1.2f);return true;
        }
        if(progress>0||!herb(held))return false;
        for(int i=0;i<4;i++)if(inventory.getItem(i).is(held.getItem()))return false;
        for(int i=0;i<3;i++)if(inventory.getItem(i).isEmpty()){
            inventory.setItem(i,held.split(1));changed();sound(SoundEvents.GENERIC_SPLASH,.3f,1.5f);return true;
        }return false;
    }
    public boolean addWater(){
        if(water!=0||progress>0||!ready.isEmpty())return false;
        hasIgnited=false;water=3;pouredAt=level==null?-100:level.getGameTime();color=0x428fb7;changed();sound(SoundEvents.BUCKET_EMPTY,.8f,1);return true;
    }
    public boolean stir(){
        var e=recipe();if(level==null||e==null||fire!=2||water==0||!ready.isEmpty()||stirs>=3
                ||(stirs>0&&progress<stirs*e.ticks/3)||level.getGameTime()-stirredAt<15)return false;
        stirs++;stirredAt=level.getGameTime();changed();sound(SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE,.8f,.8f+stirs*.15f);return true;
    }
    public String status(){
        if(!ready.isEmpty())return (readyQuality==3?"古怪废液":new String[]{"浑浊 · ","标准 · ","臻品 · 大师 · "}[readyQuality]+Elixir.find(ready).name)+" · 用空玻璃瓶盛取";
        if(water==0)return "先用水桶倒水 · 一桶可炼三剂";
        if(recipe()==null)return "向锅口投三种配方材料与焰蕊盐 · 潜行空手取回";
        if(fire!=2)return "向药釜施放火系法术点燃 · 普通火无效";
        if(burn==0&&!inventory.getItem(4).is(AlchemyContent.FUEL))return "需要一份焰蕊盐";
        if(ruined)return "药性已解离 · 用封魔盖收取古怪废液";
        if(perfectRemaining>0)return "漩涡同心！立即盖釜收汁！";
        if(!com.tnc.tnc.arcana.RitualRules.sweet(temperature))return temperature<360?"药焰温度不足 · 打开魔力视观察热环":"温度过高 · 用水或冰法术降温";
        if(progress>=stirs*recipe().ticks/3)return "需要搅拌 · 空手或搅拌棒右键";
        return new String[]{"浸润","融合","凝结"}[Math.min(2,progress*3/recipe().ticks)]+"中 · 魔力视观察漩涡，封魔盖收汁";
    }
    public void interact(ServerPlayer p,InteractionHand hand){
        if(!mayUse(p))return;var held=p.getItemInHand(hand);
        if(held.is(Items.BOOK)||held.is(Items.WRITABLE_BOOK)||held.is(Items.WRITTEN_BOOK)){
            net.minecraftforge.network.NetworkHooks.openScreen(p,this,b->b.writeBlockPos(worldPosition));return;
        }
        if(held.is(com.tnc.tnc.arcana.ArcanaContent.item("sealing_lid"))||(held.getItem() instanceof com.tnc.tnc.adventure.ElementWands.Wand&&p.isShiftKeyDown())){
            if(!com.tnc.tnc.mana.sight.ManaSightServer.enabled(p)){p.displayClientMessage(Component.literal("打开魔力视，观察漩涡后再封釜。"),true);return;}seal(p);p.displayClientMessage(Component.literal(status()),true);return;
        }
        if(held.is(Items.WATER_BUCKET)){if(addWater()&&!p.isCreative())p.setItemInHand(hand,new ItemStack(Items.BUCKET));}
        else if(held.is(Items.GLASS_BOTTLE)){
            if(!ready.isEmpty()){
                var result=new ItemStack(readyQuality==3?com.tnc.tnc.arcana.ArcanaContent.item("strange_waste"):AlchemyContent.POTIONS.get(Elixir.find(ready)));
                result.getOrCreateTag().putInt("RitualQuality",readyQuality);result.getOrCreateTag().putInt("Fusion",vortex_sync);
                if(!p.isCreative())held.shrink(1);give(p,result);
                ready="";job="";progress=0;stirs=0;vortex_sync=0;perfectRemaining=0;ruined=false;hasIgnited=false;changed();sound(SoundEvents.BOTTLE_FILL,.7f,1);
            }
        }else if(p.isShiftKeyDown()&&held.isEmpty()){
            for(int i=0;i<7;i++)if(!inventory.getItem(i).isEmpty())give(p,inventory.removeItemNoUpdate(i));
            if(ready.isEmpty()){progress=0;stirs=0;job="";fire=0;working=false;vortex_sync=0;perfectRemaining=0;ruined=false;hasIgnited=false;}changed();
        }else if(held.isEmpty()||held.is(AlchemyContent.STIRRER)){
            if(com.tnc.tnc.mana.sight.ManaSightServer.enabled(p))stir();else p.displayClientMessage(Component.literal("按F8打开魔力视，观察热环和药材漩涡。"),true);
        }
        else if(held.is(Items.FLINT_AND_STEEL)||held.is(Items.FIRE_CHARGE)){
            p.displayClientMessage(Component.literal("药焰只响应火系法术。请用火球等法术命中釜体。"),true);return;
        }else insert(held);
        p.displayClientMessage(Component.literal(status()),true);
    }
    private static void give(ServerPlayer p,ItemStack s){if(!p.getInventory().add(s))p.drop(s,false);}
    public void absorbDrops(ServerLevel l){
        if(water==0||!ready.isEmpty())return;
        for(var item:l.getEntitiesOfClass(ItemEntity.class,new AABB(worldPosition).inflate(.05,0,.05).move(0,.45,0))){
            if(item.getOwner() instanceof ServerPlayer p&&mayUse(p)&&insert(item.getItem())){
                if(item.getItem().isEmpty())item.discard();else item.setItem(item.getItem());break;
            }
        }
    }
    public static void tick(Level level,BlockPos pos,BlockState state,AlchemyEntity be){
        if(!(level instanceof ServerLevel l))return;
        if(be.burst>0)be.burst--;
        if(level.getGameTime()%4==0)be.absorbDrops(l);
        var recipe=be.recipe();be.working=false;
        if(be.fire==2&&be.water>0&&be.ready.isEmpty()){
            if(be.burn==0&&be.inventory.getItem(4).is(AlchemyContent.FUEL)){be.inventory.removeItem(4,1);be.burn=1200;}
            if(be.burn>0){be.burn--;be.temperature=Math.min(1000,be.temperature+1);}else be.fire=0;
        }else be.temperature=Math.max(0,be.temperature-2);
        if(recipe==null&&be.ready.isEmpty()){be.progress=0;be.stirs=0;be.job="";be.vortex_sync=0;}
        else if(recipe!=null&&be.ready.isEmpty()){
            if(!be.job.equals(recipe.id)){be.job=recipe.id;be.progress=0;be.vortex_sync=0;}
            be.color=recipe.color;
            if(be.temperature>800&&be.fire==2){if(++be.overheated>=100){be.ruined=true;be.seal(null);l.sendParticles(net.minecraft.core.particles.ParticleTypes.SQUID_INK,pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,60,1,.6,1,.03);for(var victim:l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new AABB(pos).inflate(3)))victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,100,0));}}
            else be.overheated=Math.max(0,be.overheated-2);
            int gate=be.stirs*recipe.ticks/3;
            if(be.fire==2&&be.water>0&&!be.ruined&&be.ready.isEmpty()&&com.tnc.tnc.arcana.RitualRules.sweet(be.temperature)&&be.progress<gate){
                be.working=true;be.progress++;be.vortex_sync=Math.min(100,be.progress*100/recipe.ticks);
                if(be.vortex_sync==100&&be.perfectRemaining==0){be.perfectRemaining=30;be.burst=20;be.sound(SoundEvents.AMETHYST_CLUSTER_HIT,1.1f,1.6f);}
            }
            if(be.vortex_sync>=100&&be.perfectRemaining>0){be.perfectRemaining--;if(be.perfectRemaining==0&&be.fire==2)be.ruined=true;}
            else if(be.vortex_sync>=100&&be.fire==2)be.ruined=true;
            // Only idle, unrestrained vanilla animals: no teleporting, no taming/home changes.
            if(be.working&&recipe.effectFamily()==0&&level.getGameTime()%80==0)
                for(var animal:l.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class,new AABB(pos).inflate(8),
                        a->!a.isLeashed()&&!a.isPassenger()&&!a.isVehicle()&&a.getTarget()==null&&a.getNavigation().isDone()
                        &&!(a instanceof net.minecraft.world.entity.TamableAnimal t&&t.isTame())
                        &&net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(a.getType()).getNamespace().equals("minecraft")))
                    if(animal.distanceToSqr(Vec3.atCenterOf(pos))>9)animal.getNavigation().moveTo(pos.getX()+2.5,pos.getY(),pos.getZ()+.5,.65);
        }
        if(level.getGameTime()%5==0){be.setChanged();be.sync();}
    }
    public static void coolNear(ServerLevel l,BlockPos pos,net.minecraft.world.entity.Entity caster){
        if(!(caster instanceof ServerPlayer p))return;for(BlockPos q:BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,1,1)))if(l.hasChunkAt(q)&&l.getBlockEntity(q) instanceof AlchemyEntity be&&be.mayUse(p)&&l.getGameTime()-be.heatedAt>=10){be.temperature=Math.max(0,be.temperature-220);be.heatedAt=l.getGameTime();be.changed();}
    }
    public boolean seal(ServerPlayer p){var e=recipe();if(e==null||!ready.isEmpty()||!hasIgnited)return false;
        readyQuality=com.tnc.tnc.arcana.RitualRules.quality(vortex_sync,perfectRemaining,ruined).ordinal();
        for(int i=0;i<4;i++)if(!inventory.getItem(i).isEmpty()){var stack=inventory.getItem(i);var remainder=stack.getCraftingRemainingItem();stack.shrink(1);if(!remainder.isEmpty())Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+1.2,worldPosition.getZ()+.5,remainder);}
        water=Math.max(0,water-1);ready=e.id;working=false;fire=0;burst=70;changed();sound(SoundEvents.BREWING_STAND_BREW,.8f,1.1f);return true;
    }
    private void sound(SoundEvent s,float volume,float pitch){if(level!=null)level.playSound(null,worldPosition,s,SoundSource.BLOCKS,volume,pitch);}
    public void changed(){setChanged();sync();}
    public final ContainerData data=new ContainerData(){
        public int get(int i){return switch(i){case 0->progress;case 1->burn;case 2->fire;case 3->Elixir.find(job)==null?0:Elixir.find(job).ordinal()+1;default->0;};}
        public void set(int i,int v){}public int getCount(){return 4;}
    };
    @Override public AbstractContainerMenu createMenu(int id,Inventory p,Player player){return new AlchemyMenu(id,p,this);}
    @Override public Component getDisplayName(){return Component.literal("魔焰药典");}
    @Override protected void saveAdditional(CompoundTag t){
        super.saveAdditional(t);var list=NonNullList.withSize(7,ItemStack.EMPTY);
        for(int i=0;i<7;i++)list.set(i,inventory.getItem(i));ContainerHelper.saveAllItems(t,list);
        if(owner!=null)t.putUUID("Owner",owner);
        t.putBoolean("HasIgnited",hasIgnited);t.putInt("RitualVersion",2);t.putInt("temperature",temperature);t.putInt("vortex_sync",vortex_sync);t.putInt("Overheated",overheated);t.putInt("PerfectRemaining",perfectRemaining);t.putBoolean("Ruined",ruined);t.putInt("ReadyQuality",readyQuality);t.putLong("HeatedAt",heatedAt);
        t.putInt("Progress",progress);t.putInt("Burn",burn);t.putInt("Fire",fire);t.putInt("Water",water);
        t.putLong("PouredAt",pouredAt);t.putInt("Stirs",stirs);t.putLong("StirredAt",stirredAt);t.putInt("Burst",burst);t.putString("Ready",ready);
        t.putString("Job",job);t.putBoolean("Working",working);t.putInt("Color",color);
    }
    @Override public void load(CompoundTag t){
        super.load(t);var list=NonNullList.withSize(7,ItemStack.EMPTY);ContainerHelper.loadAllItems(t,list);
        for(int i=0;i<7;i++)inventory.setItem(i,list.get(i));owner=t.hasUUID("Owner")?t.getUUID("Owner"):null;
        progress=Math.max(0,Math.min(480,t.getInt("Progress")));burn=Math.max(0,Math.min(1200,t.getInt("Burn")));
        fire=t.getInt("Fire")==2?2:0;hasIgnited=t.getBoolean("HasIgnited")||fire==2;water=Math.max(0,Math.min(3,t.getInt("Water")));stirs=Math.max(0,Math.min(3,t.getInt("Stirs")));
        pouredAt=t.contains("PouredAt")?t.getLong("PouredAt"):-100;stirredAt=t.getLong("StirredAt");burst=Math.max(0,Math.min(70,t.getInt("Burst")));
        temperature=com.tnc.tnc.arcana.RitualRules.clamp(t.getInt("temperature"),1000);vortex_sync=com.tnc.tnc.arcana.RitualRules.clamp(t.getInt("vortex_sync"),100);overheated=com.tnc.tnc.arcana.RitualRules.clamp(t.getInt("Overheated"),100);perfectRemaining=com.tnc.tnc.arcana.RitualRules.clamp(t.getInt("PerfectRemaining"),30);ruined=t.getBoolean("Ruined");readyQuality=t.contains("ReadyQuality")?com.tnc.tnc.arcana.RitualRules.clamp(t.getInt("ReadyQuality"),3):1;heatedAt=t.contains("HeatedAt")?t.getLong("HeatedAt"):-100;
        if(!t.contains("RitualVersion")&&water==0){progress=0;stirs=0;fire=0;}
        ready=Elixir.find(t.getString("Ready"))==null?"":t.getString("Ready");job=t.getString("Job");working=t.getBoolean("Working");color=t.getInt("Color");
    }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition).inflate(5);}
    @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    private void sync(){if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
}
