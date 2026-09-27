package com.tnc.tnc.magic.water;

import com.tnc.tnc.TNMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Persistent 30-second weather lease; if another actor changes weather, relinquish it. */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class WaterWeather extends SavedData {
    private long until;
    private int clear,rain,thunder;
    private int observedRain,observedThunder;
    private boolean observedCycle;
    private boolean raining,thundering;
    static WaterWeather get(ServerLevel level){return level.getDataStorage().computeIfAbsent(WaterWeather::load,WaterWeather::new,"tnc_water_weather");}
    public static void storm(ServerLevel level) {
        if(!level.dimension().equals(Level.OVERWORLD))return;
        var state=get(level); var data=level.getServer().getWorldData().overworldData();
        if(state.until==0) {state.clear=data.getClearWeatherTime();state.rain=data.getRainTime();state.thunder=data.getThunderTime();state.raining=data.isRaining();state.thundering=data.isThundering();}
        state.until=level.getGameTime()+600;
        // Extra tick margin avoids vanilla randomizing the state before our lease expires.
        level.setWeatherParameters(0,620,true,true);state.observedRain=620;state.observedThunder=620;
        state.observedCycle=level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE);state.setDirty();
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        ServerLevel level=event.getServer().overworld();var state=get(level);if(state.until==0)return;
        var data=level.getServer().getWorldData().overworldData();
        long remaining=state.until-level.getGameTime();
        // Do not overwrite /weather changes or a competing weather controller.
        boolean cycle=level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE);
        if(!data.isRaining() || !data.isThundering() || data.getClearWeatherTime()>0
                || !ownsTimer(state.observedRain,data.getRainTime(),cycle||state.observedCycle)
                || !ownsTimer(state.observedThunder,data.getThunderTime(),cycle||state.observedCycle)) {
            state.until=0;state.setDirty();return;
        }
        state.observedRain=data.getRainTime();state.observedThunder=data.getThunderTime();state.observedCycle=cycle;state.setDirty();
        if(remaining<=0) {
            data.setClearWeatherTime(state.clear);data.setRainTime(state.rain);data.setThunderTime(state.thunder);
            data.setRaining(state.raining);data.setThundering(state.thundering);state.until=0;state.setDirty();
        }
    }
    static boolean ownsTimer(int previous,int actual,boolean cycle){return actual==previous || cycle && actual==previous-1;}
    private static WaterWeather load(CompoundTag t){var s=new WaterWeather();s.until=t.getLong("until");s.clear=t.getInt("clear");s.rain=t.getInt("rain");s.thunder=t.getInt("thunder");s.raining=t.getBoolean("raining");s.thundering=t.getBoolean("thundering");s.observedRain=t.getInt("observedRain");s.observedThunder=t.getInt("observedThunder");s.observedCycle=t.getBoolean("observedCycle");return s;}
    @Override public CompoundTag save(CompoundTag t){t.putLong("until",until);t.putInt("clear",clear);t.putInt("rain",rain);t.putInt("thunder",thunder);t.putBoolean("raining",raining);t.putBoolean("thundering",thundering);t.putInt("observedRain",observedRain);t.putInt("observedThunder",observedThunder);t.putBoolean("observedCycle",observedCycle);return t;}
}
