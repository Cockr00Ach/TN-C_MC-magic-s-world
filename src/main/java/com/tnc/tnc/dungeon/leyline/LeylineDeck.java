package com.tnc.tnc.dungeon.leyline;

import java.util.*;
import static com.tnc.tnc.dungeon.leyline.LeylineKind.*;

/** Each realm has its own language of puzzles; seed changes the order, never the realm identity. */
public final class LeylineDeck {
    public static List<LeylineKind> pool(String theme, int floor) {
        var list = new ArrayList<>(switch (theme) {
            case "desert" -> List.of(SUNDIAL, VINES, BRIDGE, MUSIC, FALSE_VAULT, EXHIBITION);
            case "crypt" -> List.of(PUPPETS, MUSIC, FALSE_VAULT, VINES, FACTIONS, CAULDRON);
            case "tide" -> List.of(TIDE, BRIDGE, MUSIC, VINES, ALCHEMY, MEAL);
            case "astral" -> List.of(DUAL_BRIDGE, FALSE_VAULT, CHESS, MUSIC, EXHIBITION, BRIDGE);
            default -> throw new IllegalArgumentException("Unknown dungeon theme " + theme);
        });
        if(floor >= 6) list.addAll(switch(theme) {
            case "desert" -> List.of(LENS, REFRACTION, FACTIONS);
            case "crypt" -> List.of(ECHO, POLLUTION, STOMACH);
            case "tide" -> List.of(RAPIDS, STOMACH, POLLUTION);
            default -> List.of(REFRACTION, GRAVITY, ECHO);
        });
        if(floor >= 11) list.addAll(switch(theme) {
            case "desert" -> List.of(SCALE, PAWN);
            case "crypt" -> List.of(SCALE, PAWN);
            case "tide" -> List.of(CAULDRON, PAWN);
            default -> List.of(MEAL, PAWN);
        });
        return List.copyOf(list);
    }
    public static LeylineKind choose(String theme,long seed,int floor,int room) {
        var deck=new ArrayList<>(pool(theme,floor));
        Collections.shuffle(deck,new Random(seed ^ floor*0x9e3779b97f4a7c15L ^ theme.hashCode()));
        return deck.get(Math.floorMod(room-1,deck.size()));
    }
    private LeylineDeck(){}
}
