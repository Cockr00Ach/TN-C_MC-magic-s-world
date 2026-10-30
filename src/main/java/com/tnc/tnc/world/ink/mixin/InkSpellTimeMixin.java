package com.tnc.tnc.world.ink.mixin;

import com.tnc.tnc.world.ink.InkDragonDread;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.casting.SpellCast;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo @Mixin(targets="net.spell_engine.internals.SpellHelper",remap=false)
public abstract class InkSpellTimeMixin {
    @Unique private static final java.util.Map<net.minecraft.world.entity.player.Player,Integer> ink$pulses=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    @Inject(method="getCastDuration(Lnet/minecraft/world/entity/LivingEntity;Lnet/spell_engine/api/spell/Spell;Lnet/minecraft/world/item/ItemStack;)F",at=@At("RETURN"),cancellable=true,remap=false)
    private static void ink$duration(LivingEntity e,Spell spell,ItemStack stack,CallbackInfoReturnable<Float> c){if(InkDragonDread.active(e))c.setReturnValue(c.getReturnValue()*4);}
    @Inject(method="getCastTimeDetails",at=@At("RETURN"),cancellable=true,remap=false)
    private static void ink$details(LivingEntity e,Spell spell,CallbackInfoReturnable<SpellCast.Duration> c){
        if(InkDragonDread.active(e)){var d=c.getReturnValue();var slow=new SpellCast.Duration(d.speed()*.25F,d.length()*4);c.setReturnValue(slow);com.tnc.tnc.world.ink.InkSpellTimeBridge.markNewCast(e,slow.speed(),slow.length());}
    }
    @Inject(method="startCasting",at=@At("HEAD"),remap=false)
    private static void ink$markServer(net.minecraft.world.entity.player.Player p,net.minecraft.resources.ResourceLocation id,float speed,int length,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        if(InkDragonDread.active(p))com.tnc.tnc.world.ink.InkSpellTimeBridge.markNewCast(p,speed,length);
    }
    @Inject(method="performSpell",at=@At("HEAD"),cancellable=true,remap=false)
    private static void ink$channel(net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player p,net.minecraft.resources.ResourceLocation id,
            java.util.List<net.minecraft.world.entity.Entity> targets,SpellCast.Action action,float progress,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        if(action!=SpellCast.Action.CHANNEL||!InkDragonDread.active(p)){ink$pulses.remove(p);return;}
        int count=ink$pulses.merge(p,1,Integer::sum);if((count&3)!=0)ci.cancel();
    }
}
