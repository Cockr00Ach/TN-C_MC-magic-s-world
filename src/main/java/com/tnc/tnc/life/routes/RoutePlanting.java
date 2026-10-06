package com.tnc.tnc.life.routes;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import com.tnc.tnc.life.botanical.*;
import java.util.Set;
public final class RoutePlanting {
    public static final Set<String> LEGACY=Set.of("road_bell_crop","night_gourd_crop","tide_reed_crop","hushcap_mushroom","rainletter_bush","homeward_flower","warning_moss","mana_root","verdant_vein","sky_vine");
    private static final Set<String> FRUIT=Set.of("hearth_pepper_fruit","echo_bean_pod","farlight_fruit","rainproof_pod","star_dew_fruit");
    public static InteractionResult plantFruit(String id,UseOnContext c){if(!FRUIT.contains(id)||c.getPlayer()==null)return InteractionResult.PASS;for(var s:BotanicalSpecies.values())if(s.product.equals(id)){var ctx=new net.minecraft.world.item.context.BlockPlaceContext(c);return new net.minecraft.world.item.BlockItem(BotanicalContent.BLOCKS.get(s.id),new net.minecraft.world.item.Item.Properties()).place(ctx);}return InteractionResult.PASS;}
    public static boolean magic(BlockState s){if(s.getBlock() instanceof BotanicalBlock||s.getBlock() instanceof RoutePlantBlock)return true;var id=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(s.getBlock());return id!=null&&id.getNamespace().equals("tnc")&&LEGACY.contains(id.getPath());}
    public static boolean wild(BlockState s,LevelReader l,BlockPos p){if(s.hasProperty(RoutePlantBlock.WILD))return s.getValue(RoutePlantBlock.WILD);var id=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(s.getBlock());if(id==null)return false;if(id.getPath().startsWith("wild_"))return true;if(l.getBlockEntity(p) instanceof com.tnc.tnc.life.ecology.VerdantVeinBlockEntity be)return be.manaOwner()==null;return false;}
    public static BlockPos soil(BlockState s,BlockPos p){if(s.getBlock() instanceof com.tnc.tnc.life.WarningMoss.MossBlock)return p.relative(s.getValue(com.tnc.tnc.life.WarningMoss.MossBlock.FACING).getOpposite());return p.below();}
    public static boolean grow(BlockState s,Level l,BlockPos p){return !magic(s)||wild(s,l,p)||MagicSoilBlock.isSoil(l.getBlockState(soil(s,p)))&&MagicSoilBlock.irrigated(l,soil(s,p));}
}
