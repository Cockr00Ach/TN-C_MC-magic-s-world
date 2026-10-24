package com.tnc.tnc.dungeon.leyline;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.tnc.tnc.dungeon.leyline.LeylineKind.*;
class LeylineDeckTest {
 @Test void realmsHaveDistinctPoolsAndEveryAuthoredRoomHasAHome(){
  var union=EnumSet.noneOf(LeylineKind.class);var pools=new HashSet<List<LeylineKind>>();
  for(var theme:List.of("desert","crypt","tide","astral")){var pool=LeylineDeck.pool(theme,30);union.addAll(pool);pools.add(pool);assertEquals(pool.size(),new HashSet<>(pool).size());}
  var expected=EnumSet.allOf(LeylineKind.class);expected.remove(NONE);assertEquals(expected,union);assertEquals(4,pools.size());
  assertTrue(LeylineDeck.pool("desert",1).contains(SUNDIAL));assertFalse(LeylineDeck.pool("crypt",30).contains(SUNDIAL));
  assertTrue(LeylineDeck.pool("tide",1).contains(TIDE));assertFalse(LeylineDeck.pool("desert",30).contains(TIDE));
 }
 @Test void roomDeckIsStableNonRepeatingAndProgressive(){
  for(var theme:List.of("desert","crypt","tide","astral"))for(long seed=0;seed<100;seed++)for(int floor=1;floor<=30;floor++){
   var selected=new HashSet<LeylineKind>();int count=Math.min(6,LeylineDeck.pool(theme,floor).size());
   for(int room=1;room<=count;room++){var kind=LeylineDeck.choose(theme,seed,floor,room);assertEquals(kind,LeylineDeck.choose(theme,seed,floor,room));assertTrue(selected.add(kind));}
   assertTrue(LeylineDeck.pool(theme,30).containsAll(LeylineDeck.pool(theme,1)));
   if(floor<6)assertFalse(selected.contains(GRAVITY)||selected.contains(SCALE)||selected.contains(PAWN));
  }
 }
}
