package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.warrior.WarriorWeaponOrnaments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Shares the real weapon mount; neither an arm attachment nor a world particle. */
public final class WarriorWeaponOrnamentRenderer {
    private WarriorWeaponOrnamentRenderer() {}
    public static void render(LivingEntity entity,ItemStack stack,ItemDisplayContext context,boolean left,
                              PoseStack pose,MultiBufferSource buffers,int light) {
        if(stack.isEmpty() || entity==null || !(context.firstPerson() || context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND))return;
        var mc=Minecraft.getInstance();if(mc.level==null || mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(entity.position())>1024)return;
        var id=ForgeRegistries.ITEMS.getKey(stack.getItem());if(id==null || !id.getNamespace().equals("tnc"))return;
        var active=WarriorAnimationClient.active(entity.getId());
        boolean energized=entity.isUsingItem() || active!=null && (active.hold() || active.animation().finisher());
        var boxes=WarriorWeaponOrnaments.build(id.getPath(),mc.level.getGameTime()+mc.getFrameTime(),energized);
        if(boxes.isEmpty())return;
        var model=mc.getItemRenderer().getModel(stack,mc.level,entity,entity.getId()+context.ordinal());
        pose.pushPose();
        try {
            model.getTransforms().getTransform(context).apply(left,pose);
            pose.translate(-.5,-.5,-.5);
            var out=buffers.getBuffer(WarriorGeometryRenderType.TYPE);
            for(var box:boxes)cube(out,pose,box);
        } finally {pose.popPose();}
    }
    static void cube(VertexConsumer out,PoseStack pose,WarriorWeaponOrnaments.Box b) {
        float x=(float)b.x()/16,y=(float)b.y()/16,z=(float)b.z()/16;
        float X=x+(float)b.w()/16,Y=y+(float)b.h()/16,Z=z+(float)b.d()/16;
        float[][] p={{x,y,z},{X,y,z},{X,Y,z},{x,Y,z},{x,y,Z},{X,y,Z},{X,Y,Z},{x,Y,Z}};
        int[][] faces={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{0,1,5,4},{3,7,6,2}};
        float[] shade={.73f,.86f,.7f,.86f,.6f,1};
        for(int f=0;f<6;f++)for(int i:faces[f])out.vertex(pose.last().pose(),p[i][0],p[i][1],p[i][2])
                .color((b.color()>>16&255)/255f*shade[f],(b.color()>>8&255)/255f*shade[f],(b.color()&255)/255f*shade[f],.96f).endVertex();
    }
}
