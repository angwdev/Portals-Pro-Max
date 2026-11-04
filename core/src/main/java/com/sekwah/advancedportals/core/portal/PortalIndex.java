package com.sekwah.advancedportals.core.portal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable chunk bucket index of every portal, so the hot paths (player
 * movement, block protection, physics, explosions...) only have to look at the
 * handful of portals that overlap the chunk being queried instead of looping
 * over every portal on the server.
 *
 * <p>A new index is built (and swapped in atomically by the owner) whenever a
 * portal is added, removed, moved or the portals are reloaded. This keeps reads
 * lock free which matters on Folia where several region threads query it at
 * once.
 */
public final class PortalIndex {
    private static final AdvancedPortal[] NONE = new AdvancedPortal[0];

    public static final PortalIndex EMPTY =
        new PortalIndex(new HashMap<>(), NONE);

    /**
     * world name -> chunk key -> portals overlapping that chunk
     */
    private final Map<String, Map<Long, AdvancedPortal[]>> chunks;

    private final AdvancedPortal[] all;

    private PortalIndex(Map<String, Map<Long, AdvancedPortal[]>> chunks,
                        AdvancedPortal[] all) {
        this.chunks = chunks;
        this.all = all;
    }

    public static PortalIndex build(Collection<AdvancedPortal> portals) {
        if (portals.isEmpty()) {
            return EMPTY;
        }

        Map<String, Map<Long, List<AdvancedPortal>>> building =
            new HashMap<>();

        for (AdvancedPortal portal : portals) {
            String world = portal.getWorldName();
            int minChunkX = portal.getMinLoc().getPosX() >> 4;
            int maxChunkX = portal.getMaxLoc().getPosX() >> 4;
            int minChunkZ = portal.getMinLoc().getPosZ() >> 4;
            int maxChunkZ = portal.getMaxLoc().getPosZ() >> 4;

            Map<Long, List<AdvancedPortal>> worldMap =
                building.computeIfAbsent(world, w -> new HashMap<>());

            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    worldMap.computeIfAbsent(chunkKey(cx, cz),
                                             k -> new ArrayList<>(2))
                        .add(portal);
                }
            }
        }

        Map<String, Map<Long, AdvancedPortal[]>> frozen =
            new HashMap<>(building.size());
        for (Map.Entry<String, Map<Long, List<AdvancedPortal>>> world :
             building.entrySet()) {
            Map<Long, AdvancedPortal[]> frozenWorld =
                new HashMap<>(world.getValue().size());
            for (Map.Entry<Long, List<AdvancedPortal>> chunk :
                 world.getValue().entrySet()) {
                frozenWorld.put(chunk.getKey(),
                                chunk.getValue().toArray(NONE));
            }
            frozen.put(world.getKey(), frozenWorld);
        }

        return new PortalIndex(frozen, portals.toArray(NONE));
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public boolean isEmpty() {
        return this.all.length == 0;
    }

    public AdvancedPortal[] getAll() {
        return this.all;
    }

    /**
     * Portals that overlap the chunk containing the given block column.
     * Never returns null, the returned array must not be modified.
     */
    public AdvancedPortal[] inChunkOf(String world, int blockX, int blockZ) {
        Map<Long, AdvancedPortal[]> worldMap = this.chunks.get(world);
        if (worldMap == null) {
            return NONE;
        }
        AdvancedPortal[] result =
            worldMap.get(chunkKey(blockX >> 4, blockZ >> 4));
        return result == null ? NONE : result;
    }

    /**
     * @return the first portal containing the block (expanded by
     *     {@code extra} blocks in every direction) or null if there is none.
     */
    public AdvancedPortal findContaining(String world, int x, int y, int z,
                                         int extra) {
        if (this.all.length == 0) {
            return null;
        }
        Map<Long, AdvancedPortal[]> worldMap = this.chunks.get(world);
        if (worldMap == null) {
            return null;
        }

        int minChunkX = (x - extra) >> 4;
        int maxChunkX = (x + extra) >> 4;
        int minChunkZ = (z - extra) >> 4;
        int maxChunkZ = (z + extra) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                AdvancedPortal[] bucket = worldMap.get(chunkKey(cx, cz));
                if (bucket == null) {
                    continue;
                }
                for (AdvancedPortal portal : bucket) {
                    if (portal.isLocationInPortal(world, x, y, z, extra)) {
                        return portal;
                    }
                }
            }
        }
        return null;
    }
}
