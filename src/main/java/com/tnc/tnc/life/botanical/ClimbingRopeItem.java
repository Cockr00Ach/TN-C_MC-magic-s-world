package com.tnc.tnc.life.botanical;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
public final class ClimbingRopeItem extends Item {
    public ClimbingRopeItem(){super(new Properties().stacksTo(1));}
    @Override public InteractionResult useOn(UseOnContext c){return com.tnc.tnc.life.wonders.WonderRopes.use(c,12);}
}
