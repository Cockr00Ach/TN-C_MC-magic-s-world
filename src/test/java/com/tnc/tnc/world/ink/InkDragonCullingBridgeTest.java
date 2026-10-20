package com.tnc.tnc.world.ink;
import com.tnc.tnc.world.ink.client.InkDragonCullingBridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InkDragonCullingBridgeTest {
    public static final class OptionalApi {boolean culled=true,camera=true;int timeout;public void setCulled(boolean v){culled=v;}public void setOutOfCamera(boolean v){camera=v;}public void setTimeout(){timeout++;}}
    @Test void restoresVisibilityUsingOnlyTheOptionalApi(){var entity=new OptionalApi();assertTrue(InkDragonCullingBridge.reveal(entity));assertFalse(entity.culled);assertFalse(entity.camera);assertEquals(1,entity.timeout);}
    @Test void missingModDoesNotPreventRendering(){assertFalse(InkDragonCullingBridge.reveal(new Object()));}
}
