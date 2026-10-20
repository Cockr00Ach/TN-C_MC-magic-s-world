package com.tnc.tnc.alchemy;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
public final class AlchemyEntity extends BlockEntity implements MenuProvider {
    public UUID owner;public int progress,burn,fire,color=0x64d8c9;public String job="";public boolean working;
    public final SimpleContainer inventory=new SimpleContainer(7){@Override public void setChanged(){super.setChanged();AlchemyEntity.this.setChanged();}};
    public AlchemyEntity(BlockPos p,BlockState s){super(AlchemyContent.ENTITY,p,s);}
    public boolean mayUse(ServerPlayer p){return owner==null||owner.equals(p.getUUID())||p.isCreative();}
    public void ignite(boolean magic){fire=magic?2:1;setChanged();if(level instanceof ServerLevel l){l.playSound(null,worldPosition,net.minecraft.sounds.SoundEvents.FIRECHARGE_USE,net.minecraft.sounds.SoundSource.BLOCKS,.7f,1.3f);sync();}}
    public Elixir recipe(){var input=new ArrayList<String>();for(int i=0;i<4;i++)if(!inventory.getItem(i).isEmpty())input.add(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(inventory.getItem(i).getItem()).toString());return Arrays.stream(Elixir.values()).filter(e->e.matches(input)).findFirst().orElse(null);}
    public static void igniteNear(ServerLevel l,BlockPos p,net.minecraft.world.entity.Entity caster){for(BlockPos q:BlockPos.betweenClosed(p.offset(-1,-1,-1),p.offset(1,1,1)))if(l.hasChunkAt(q)&&l.getBlockEntity(q) instanceof AlchemyEntity be&&caster instanceof ServerPlayer player&&be.mayUse(player))be.ignite(true);}
    private boolean room(Item item){var out=inventory.getItem(6);return out.isEmpty()||out.is(item)&&out.getCount()<Math.min(8,out.getMaxStackSize());}
    public static void tick(Level level,BlockPos pos,BlockState state,AlchemyEntity be){
        var recipe=be.recipe();be.working=false;
        if(recipe==null){be.progress=0;be.job="";}else{
            if(!be.job.equals(recipe.id)){be.job=recipe.id;be.progress=0;}be.color=recipe.color;
            if(be.fire>0&&be.inventory.getItem(5).is(Items.GLASS_BOTTLE)&&be.room(AlchemyContent.POTIONS.get(recipe))){
                if(be.burn==0&&be.inventory.getItem(4).is(AlchemyContent.FUEL)){be.inventory.removeItem(4,1);be.burn=1200;}
                if(be.burn>0){be.burn--;be.working=true;be.progress++;
                    // Optional borrowed heat: only a usable adjacent TN-C node, 1 real mana per second.
                    if(level.getGameTime()%20==0&&be.owner!=null&&level instanceof ServerLevel l){var p=l.getServer().getPlayerList().getPlayer(be.owner);if(p!=null)for(var d:Direction.values())if(l.getBlockEntity(pos.relative(d)) instanceof com.tnc.tnc.life.routes.RouteNodeEntity n&&n.mayUse(p)&&n.extract(1)==1){be.progress+=10;com.tnc.tnc.mana.sight.ManaSightServer.record(l,net.minecraft.world.phys.Vec3.atCenterOf(n.getBlockPos()),net.minecraft.world.phys.Vec3.atCenterOf(pos),1,recipe.color,0);break;}}
                    if(be.progress>=(be.fire==2?recipe.ticks:recipe.ticks*3/2)){
                        for(int i=0;i<4;i++)if(!be.inventory.getItem(i).isEmpty()){var old=be.inventory.getItem(i);var remainder=old.getCraftingRemainingItem();old.shrink(1);if(!remainder.isEmpty()){if(old.isEmpty())be.inventory.setItem(i,remainder);else Containers.dropItemStack(level,pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,remainder);}}
                        be.inventory.removeItem(5,1);var out=be.inventory.getItem(6);if(out.isEmpty())be.inventory.setItem(6,new ItemStack(AlchemyContent.POTIONS.get(recipe)));else out.grow(1);be.progress=0;be.setChanged();
                        if(level instanceof ServerLevel l){l.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,pos.getX()+.5,pos.getY()+1.15,pos.getZ()+.5,8,.18,.2,.18,.025);l.playSound(null,pos,net.minecraft.sounds.SoundEvents.BREWING_STAND_BREW,net.minecraft.sounds.SoundSource.BLOCKS,.8f,1.1f);}
                    }
                }
            }
        }
        if(level.getGameTime()%20==0){be.setChanged();be.sync();}
    }
    public final ContainerData data=new ContainerData(){public int get(int i){return switch(i){case 0->progress;case 1->burn;case 2->fire;case 3->Elixir.find(job)==null?0:Elixir.find(job).ordinal()+1;default->0;};}public void set(int i,int v){}public int getCount(){return 4;}};
    @Override public AbstractContainerMenu createMenu(int id,Inventory p,Player player){return new AlchemyMenu(id,p,this);}
    @Override public Component getDisplayName(){return Component.literal("魔焰炼药釜");}
    @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);var list=NonNullList.withSize(7,ItemStack.EMPTY);for(int i=0;i<7;i++)list.set(i,inventory.getItem(i));ContainerHelper.saveAllItems(t,list);if(owner!=null)t.putUUID("Owner",owner);t.putInt("Progress",progress);t.putInt("Burn",burn);t.putInt("Fire",fire);t.putString("Job",job);t.putBoolean("Working",working);t.putInt("Color",color);}
    @Override public void load(CompoundTag t){super.load(t);var list=NonNullList.withSize(7,ItemStack.EMPTY);ContainerHelper.loadAllItems(t,list);for(int i=0;i<7;i++)inventory.setItem(i,list.get(i));owner=t.hasUUID("Owner")?t.getUUID("Owner"):null;progress=Math.max(0,Math.min(480,t.getInt("Progress")));burn=Math.max(0,Math.min(1200,t.getInt("Burn")));fire=Math.max(0,Math.min(2,t.getInt("Fire")));job=t.getString("Job");working=t.getBoolean("Working");color=t.getInt("Color");}
    @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    private void sync(){if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
}
