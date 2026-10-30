package com.tnc.tnc.light;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Five learnable spells; deployed mirror items remain a convenient creative/test entry. */
public final class LightMirrorSpells {
    private LightMirrorSpells() {}
    public static int tier(String path) {
        for(int tier=1;tier<=5;tier++)if(path.equals("light_mirror_t"+tier))return tier;
        return 0;
    }
    public static void cast(ServerPlayer player,String path) {
        int tier=tier(path);if(tier==0)return;
        var level=player.serverLevel();
        var owned=level.getEntitiesOfClass(TNLightMirrorEntity.class,player.getBoundingBox().inflate(160),m->m.belongsTo(player));
        if(owned.size()>=8){player.displayClientMessage(net.minecraft.network.chat.Component.literal("附近最多维持 8 面光镜"),true);return;}
        Vec3 eye=player.getEyePosition(),end=eye.add(player.getLookAngle().scale(16));
        var hit=level.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        Vec3 at=hit.getType()==HitResult.Type.BLOCK ? hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.85))
                : eye.add(player.getLookAngle().scale(4)).add(0,-1.3,0);
        var mirror=LightMirrorRegistration.MIRROR.create(level);if(mirror==null)return;
        mirror.configure(player,tier);mirror.moveTo(at.x,at.y,at.z,player.getYRot(),0);
        if(!level.getWorldBorder().isWithinBounds(mirror.getBoundingBox())||!level.noCollision(mirror,mirror.getBoundingBox()))return;
        if(level.addFreshEntity(mirror))level.playSound(null,at.x,at.y,at.z,net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                net.minecraft.sounds.SoundSource.PLAYERS,1,1.2F);
    }
}
