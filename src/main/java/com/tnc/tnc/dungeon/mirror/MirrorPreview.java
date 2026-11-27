package com.tnc.tnc.dungeon.mirror;

import com.tnc.tnc.dungeon.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A short route to the actual guardian, using the same arena, start checks and AI as a normal run. */
@Mod.EventBusSubscriber(modid="tnc")
public final class MirrorPreview {
    private static final String PREVIEW_ROOM="MirrorPreviewRoom";
    private static final String ORIGINAL_MODE="MirrorPreviewOriginalMode";
    private MirrorPreview() {}
    @SubscribeEvent public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("dungeon").then(Commands.literal("mirror")
                .requires(s->s.hasPermission(2) || s.getEntity() instanceof ServerPlayer p
                        && s.getServer().isSingleplayerOwner(p.getGameProfile()))
                .executes(ctx->open(ctx.getSource().getPlayerOrException()))));
    }

    private static int open(ServerPlayer p){
        var l=p.server.getLevel(DungeonRealm.KEY);
        if(l==null)return 0;
        var d=DungeonData.get(p.server);
        var r=DungeonService.run(p);
        List<ServerPlayer> party;
        if(r==null){
            if(DungeonRealm.inside(p.level())){
                DungeonService.message(p,"请先离开当前地下城，再开启虚镜试玩。");return 0;
            }
            var t=d.profile(p.getUUID());
            t.putString("ReturnDimension",p.level().dimension().location().toString());
            t.putDouble("ReturnX",p.getX());t.putDouble("ReturnY",p.getY());t.putDouble("ReturnZ",p.getZ());t.putFloat("ReturnYaw",p.getYRot());
            r=new DungeonData.Run(UUID.randomUUID(),p.getUUID(),p.getRandom().nextLong(),d.nextSlot++,DungeonTheme.ASTRAL);
            t.putUUID("Run",r.id);t.putLong("Pending",0);d.runs.put(r.id,r);
            party=List.of(p);
        }else if(r.floor==30 && r.theme==DungeonTheme.ASTRAL && r.state().events.contains(PREVIEW_ROOM)){
            var existing=r;
            var room=r.graph().rooms.stream().filter(v->isPreview(existing,v)).findFirst().orElse(null);
            if(room==null)return 0;
            if(r.state().clear(room.id())){
                DungeonService.message(p,"这场虚镜试炼已经完成。可从镜殿尽头的出口离开。");return 1;
            }
            if(com.tnc.tnc.combat.DownedCombat.isDowned(p)){
                DungeonService.message(p,"请等待队友救援，再继续虚镜试炼。");return 0;
            }
            if(DungeonService.recoveryPending(p)){
                DungeonService.message(p,"正在将你送回原来的试炼，抵达后可输入 /dungeon mirror 继续。");return 0;
            }
            if(DungeonService.recovering(p))DungeonService.resume(p);
            if(r.building){DungeonService.message(p,"镜殿正在准备中。");return 1;}
            var here=DungeonService.room(p);
            if(here!=null && here.id()==room.id()
                    && r.state().events.getCompound("EchoingMirror").getInt("Phase")!=0){
                DungeonService.message(p,"镜像仍在眼前，你可以在这里继续战斗。");return 1;
            }
            DungeonService.prepare(l,existing,()->arrive(p,existing,room));
            return 1;
        }else{
            if(r.floor!=0 || r.building || r.theme!=DungeonTheme.ASTRAL || !r.leader.equals(p.getUUID())){
                DungeonService.message(p,"请先结束当前挑战，或由队长在星眠幻庭大厅开启虚镜试玩。");return 0;
            }
            party=DungeonService.online(p.server,r);
            if(party.size()!=r.members.size() || party.stream().anyMatch(com.tnc.tnc.combat.DownedCombat::isDowned)){
                DungeonService.message(p,"请先救起倒地队友，让全队回到大厅。");return 0;
            }
        }
        r.floor=30;r.segment=26;r.multiplayer=party.size()>1;
        var run=r;
        var room=run.graph().rooms.stream().filter(v->v.kind()==DungeonGraph.Kind.GUARDIAN).findFirst().orElseThrow();
        run.state().events.putInt(PREVIEW_ROOM,room.id());
        d.setDirty();
        for (var member : party) {
            member.sendSystemMessage(net.minecraft.network.chat.Component.literal("正在准备星眠幻庭第 30 层。踏入镜殿的银纹石阵后，镜像将现身。"));
        }
        DungeonService.prepare(l,run,()->party.forEach(q->arrive(q,run,room)));
        return 1;
    }
    private static void arrive(ServerPlayer player,DungeonData.Run run,DungeonGraph.Room room){
        var profile=DungeonData.get(player.server).profile(player.getUUID());
        profile.putUUID("CursorRun",run.id);profile.putInt("LastRoom",room.id());profile.putInt("LastFloor",30);
        DungeonService.teleport(player,run,entry(run,room),MirrorArena.entrance(run,room).getOpposite().toYRot(),-5);
    }

    static BlockPos entry(DungeonData.Run run,DungeonGraph.Room room){
        return DungeonRealm.center(run,room).relative(MirrorArena.entrance(run,room),6).above();
    }
    private static boolean isPreview(DungeonData.Run run,DungeonGraph.Room room){
        return MirrorArena.site(run,room) && run.state().events.contains(PREVIEW_ROOM)
                && run.state().events.getInt(PREVIEW_ROOM)==room.id();
    }

    @SubscribeEvent public static void previewMode(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount%5!=0)return;
        var root=p.getPersistentData();
        var saved=root.getCompound(Player.PERSISTED_NBT_TAG);
        if(!p.isCreative() && !p.isSpectator() && !saved.contains(ORIGINAL_MODE))return;
        var run=DungeonRealm.inside(p.level())?DungeonService.run(p):null;
        boolean playing=run!=null && run.floor==30 && run.state().events.contains(PREVIEW_ROOM)
                && !run.state().clear(run.state().events.getInt(PREVIEW_ROOM));
        if(playing && !saved.contains(ORIGINAL_MODE)){
            saved.putInt(ORIGINAL_MODE,p.gameMode.getGameModeForPlayer().getId());
            root.put(Player.PERSISTED_NBT_TAG,saved);
            p.setGameMode(GameType.SURVIVAL);
        }else if(!playing && saved.contains(ORIGINAL_MODE)){
            var original=GameType.byId(saved.getInt(ORIGINAL_MODE));
            saved.remove(ORIGINAL_MODE);
            if(p.gameMode.getGameModeForPlayer()==GameType.SURVIVAL)p.setGameMode(original);
        }
    }
}
