package com.tnc.tnc.dungeon.mirror.client;

import com.mojang.authlib.GameProfile;
import com.tnc.tnc.dungeon.mirror.EchoDouble;
import com.tnc.tnc.dungeon.mirror.MirrorAccessories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;

/** Only a render subject. Never added to the level/player list or ticked as an actual player. */
public final class EchoPlayer extends RemotePlayer {
    private final PlayerInfo subject;
    public final EchoDouble actor;
    private int walked = -1;

    EchoPlayer(ClientLevel level,GameProfile profile,EchoDouble actor) {
        super(level,profile);
        var connection=Minecraft.getInstance().getConnection();
        var online=connection==null ? null : connection.getPlayerInfo(profile.getId());
        subject = online!=null ? online : new PlayerInfo(profile,false);
        this.actor=actor;
        setUUID(actor.getUUID());
        setId(actor.getId());
        MirrorAccessories.apply(this,actor.appearance().getList("Accessories",10));
    }
    @Override protected PlayerInfo getPlayerInfo() {
        return subject==null ? super.getPlayerInfo() : subject;
    }
    @Override public boolean isSpectator() { return false; }
    @Override public boolean isModelPartShown(PlayerModelPart part) {
        return actor==null || (actor.appearance().getInt("Layers") & part.getMask())!=0;
    }
    @Override public boolean isUsingItem() { return actor!=null && actor.isUsingItem(); }
    @Override public InteractionHand getUsedItemHand() { return actor==null ? InteractionHand.MAIN_HAND : actor.getUsedItemHand(); }
    @Override public ItemStack getUseItem() { return actor==null ? ItemStack.EMPTY : actor.getUseItem(); }
    @Override public int getUseItemRemainingTicks() { return actor==null ? 0 : actor.getUseItemRemainingTicks(); }

    void sync() {
        setPos(actor.getX(),actor.getY(),actor.getZ());
        xo=actor.xo; yo=actor.yo; zo=actor.zo;
        xOld=xo; yOld=yo; zOld=zo;
        setYRot(actor.getYRot()); setXRot(actor.getXRot());
        xRotO=actor.xRotO; yRotO=actor.yRotO;
        yHeadRotO=actor.yHeadRotO; yHeadRot=actor.yHeadRot;
        yBodyRotO=actor.yBodyRotO; yBodyRot=actor.yBodyRot;
        tickCount=actor.tickCount;
        swinging=actor.swinging; swingTime=actor.swingTime; swingingArm=actor.swingingArm;
        attackAnim=actor.attackAnim; oAttackAnim=actor.oAttackAnim;
        hurtTime=actor.hurtTime; hurtDuration=actor.hurtDuration; deathTime=0;
        setPose(actor.getPose()); setMainArm(actor.getMainArm()); setOnGround(actor.onGround());
        setDeltaMovement(actor.getDeltaMovement());
        if (walked!=tickCount) {
            walkAnimation.update(actor.walkAnimation.speed(),1);
            walked=tickCount;
        }
        var motion=actor.getDeltaMovement();
        xCloakO=xo; yCloakO=yo; zCloakO=zo;
        xCloak=getX()-motion.x*.28; yCloak=getY()-motion.y*.20; zCloak=getZ()-motion.z*.28;
        for(var slot:EquipmentSlot.values()) {
            var item=actor.getItemBySlot(slot);
            if(getItemBySlot(slot)!=item) setItemSlot(slot,item);
        }
    }
}
