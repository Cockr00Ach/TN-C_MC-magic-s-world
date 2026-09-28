package com.tnc.tnc.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import java.lang.reflect.Method;
import java.util.Optional;

/** Optional FTB party integration; no hard dependency and no personal-team-as-party mistake. */
public final class CombatTeams {
    private CombatTeams() {}
    private static boolean initialized,failed;
    private static Method api,manager,loaded,teamForPlayer,isParty,id;
    public static String group(ServerPlayer player) {
        if(ModList.get().isLoaded("ftbteams")&&!failed) {
            try {
                if(!initialized) {
                    var apiClass=Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
                    var apiType=Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API");
                    var managerType=Class.forName("dev.ftb.mods.ftbteams.api.TeamManager");
                    var teamType=Class.forName("dev.ftb.mods.ftbteams.api.Team");
                    api=apiClass.getMethod("api");manager=apiType.getMethod("getManager");
                    loaded=apiType.getMethod("isManagerLoaded");
                    teamForPlayer=managerType.getMethod("getTeamForPlayer",ServerPlayer.class);
                    isParty=teamType.getMethod("isPartyTeam");id=teamType.getMethod("getId");initialized=true;
                }
                Object instance=api.invoke(null);
                if(Boolean.TRUE.equals(loaded.invoke(instance))) {
                    Optional<?> team=(Optional<?>)teamForPlayer.invoke(manager.invoke(instance),player);
                    if(team.isPresent()&&Boolean.TRUE.equals(isParty.invoke(team.get())))return "ftb:"+id.invoke(team.get());
                }
            } catch(ReflectiveOperationException|RuntimeException error) {
                failed=true;
                com.mojang.logging.LogUtils.getLogger().warn("TN-C: FTB rescue party lookup unavailable; using scoreboard/co-op fallback",error);
            }
        }
        return player.getTeam()!=null?"scoreboard:"+player.getTeam().getName():"co-op";
    }
    public static void reset() { initialized=false;failed=false; }
}
