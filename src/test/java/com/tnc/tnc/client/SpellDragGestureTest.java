package com.tnc.tnc.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SpellDragGestureTest {
    @Test void aClickDoesNotAssign(){var d=new SpellDragGesture<String>();d.begin("spell",10,10);assertNull(d.finish(true));assertFalse(d.active());}
    @Test void draggingIntoSlotAssignsExactlyOnce(){var d=new SpellDragGesture<String>();d.begin("spell",0,0);d.move(8,8);assertEquals("spell",d.finish(true));assertNull(d.finish(true));}
    @Test void releasingOutsideOrCancelPreservesLoadout(){var d=new SpellDragGesture<String>();d.begin("spell",0,0);d.move(80,80);assertNull(d.finish(false));d.begin("other",0,0);d.move(30,30);d.cancel();assertNull(d.finish(true));}
    @Test void aNewGestureDoesNotInheritThePreviousDrag(){var d=new SpellDragGesture<String>();d.begin("a",0,0);d.move(20,20);d.begin("b",2,2);assertFalse(d.dragging());assertNull(d.finish(true));}
}
