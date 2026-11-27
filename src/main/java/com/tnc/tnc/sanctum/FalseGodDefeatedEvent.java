package com.tnc.tnc.sanctum;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;
import java.util.Set;
import java.util.UUID;

/** Posted once for the whole expedition, after its persistent victory flag has been set. */
public final class FalseGodDefeatedEvent extends Event {
    public final MinecraftServer server;
    public final UUID expedition;
    public final Set<UUID> members;

    FalseGodDefeatedEvent(MinecraftServer server, UUID expedition, Set<UUID> members) {
        this.server = server;
        this.expedition = expedition;
        this.members = Set.copyOf(members);
    }
}
