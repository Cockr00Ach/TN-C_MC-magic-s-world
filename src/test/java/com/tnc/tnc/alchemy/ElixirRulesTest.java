package com.tnc.tnc.alchemy;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class ElixirRulesTest {
    @Test void everyFormulaHasAUniqueThreeHerbIdentity(){var ids=new HashSet<String>();var formulas=new HashSet<Set<String>>();assertEquals(23,Elixir.values().length);for(var e:Elixir.values()){assertTrue(ids.add(e.id));assertEquals(3,e.ingredients.size());assertTrue(formulas.add(Set.copyOf(e.ingredients)));assertTrue(e.ticks>=160&&e.ticks<=320);assertTrue(e.color!=0);}}
    @TestFactory Collection<DynamicTest> exactOrderIndependentRecipesRejectSubstitutes(){var tests=new ArrayList<DynamicTest>();for(var e:Elixir.values())tests.add(DynamicTest.dynamicTest(e.id,()->{var reversed=new ArrayList<>(e.ingredients);Collections.reverse(reversed);assertTrue(e.matches(reversed));assertFalse(e.matches(e.ingredients.subList(0,2)));reversed.set(0,"minecraft:dirt");assertFalse(e.matches(reversed));var tooMany=new ArrayList<>(e.ingredients);tooMany.add(e.ingredients.get(0));assertFalse(e.matches(tooMany));}));return tests;}
}
