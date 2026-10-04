package com.tnc.tnc.production.energy;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Ownership of a living mana source; a wild unclaimed plant is never drained automatically. */
public interface OwnedManaPlant {
    @Nullable UUID manaOwner();
}
