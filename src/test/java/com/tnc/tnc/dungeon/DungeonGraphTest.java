package com.tnc.tnc.dungeon;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DungeonGraphTest {
 @Test void thirtyFloorsRemainConnectedAcrossManySeeds(){for(long seed=0;seed<1000;seed++)for(int f=1;f<=30;f++){var graph=DungeonGraph.generate(seed,f);assertTrue(graph.valid(),"seed="+seed+" floor="+f);assertEquals(8,graph.rooms.size());assertEquals(2,graph.rooms.stream().filter(r->r.kind()==DungeonGraph.Kind.TREASURE).count());if(f%5==0){assertEquals(1,graph.rooms.stream().filter(r->r.kind()==DungeonGraph.Kind.GUARDIAN).count());assertEquals(1,graph.rooms.stream().filter(r->r.kind()==DungeonGraph.Kind.GUARDIAN).findFirst().orElseThrow().links().size());}else assertEquals(1,graph.rooms.stream().filter(r->r.kind()==DungeonGraph.Kind.EXIT).count());}}
 @Test void aRunKeepsItsLayoutAndFreshRunsVary(){assertEquals(DungeonGraph.generate(12,7).rooms,DungeonGraph.generate(12,7).rooms);assertNotEquals(DungeonGraph.generate(12,7).rooms,DungeonGraph.generate(13,7).rooms);}
 @Test void allTenFamiliesAndRequestedRolesExist(){assertEquals(10,MobCatalog.ALL.stream().map(MobCatalog.Species::family).distinct().count());assertEquals(28,MobCatalog.ALL.size());assertTrue(MobCatalog.ALL.stream().filter(s->s.family()==MobCatalog.Family.GOBLIN).map(MobCatalog.Species::role).toList().containsAll(java.util.List.of(MobCatalog.Role.WARRIOR,MobCatalog.Role.CASTER,MobCatalog.Role.TANK)));assertEquals(4,MobCatalog.ALL.stream().filter(s->s.family()==MobCatalog.Family.SLIME).map(MobCatalog.Species::skin).distinct().count());}
}
