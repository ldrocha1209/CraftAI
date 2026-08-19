package com.lucasrocha.craftai.client.data;

import java.util.List;

public record WorldQueryTarget(
        WorldQueryResult.Kind kind,
        String identifier,
        String displayName,
        List<String> aliases,
        List<String> registryIds,
        int structureSearchRadius
) {
    public WorldQueryTarget {
        if (kind == null || identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("A world-query target requires a kind and identifier.");
        }
        if (displayName == null || displayName.isBlank() || aliases == null || aliases.isEmpty()) {
            throw new IllegalArgumentException("A world-query target requires a display name and aliases.");
        }
        if (registryIds == null || registryIds.isEmpty()) {
            throw new IllegalArgumentException("A world-query target requires at least one registry ID.");
        }
        if (kind == WorldQueryResult.Kind.STRUCTURE && structureSearchRadius <= 0) {
            throw new IllegalArgumentException("A structure target requires a positive search radius.");
        }

        aliases = List.copyOf(aliases);
        registryIds = List.copyOf(registryIds);
    }
}
