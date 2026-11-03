package com.sekwah.advancedportals.core.portal;

import static org.junit.jupiter.api.Assertions.*;

import com.sekwah.advancedportals.core.serializeddata.BlockLocation;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PortalIndexTest {
    private static AdvancedPortal portal(String name, String world, int x1,
                                         int y1, int z1, int x2, int y2,
                                         int z2) {
        AdvancedPortal portal = new AdvancedPortal(
            new BlockLocation(world, x1, y1, z1),
            new BlockLocation(world, x2, y2, z2), null, null);
        portal.setArgValues("name", new String[] {name});
        return portal;
    }

    @Test
    void emptyIndexFindsNothing() {
        assertTrue(PortalIndex.EMPTY.isEmpty());
        assertNull(PortalIndex.EMPTY.findContaining("world", 0, 0, 0, 5));
        assertEquals(0, PortalIndex.EMPTY.inChunkOf("world", 0, 0).length);
    }

    @Test
    void findsBlocksInsideAndNotOutside() {
        AdvancedPortal a = portal("a", "world", 10, 60, 10, 12, 62, 12);
        PortalIndex index = PortalIndex.build(Arrays.asList(a));

        assertSame(a, index.findContaining("world", 10, 60, 10, 0));
        assertSame(a, index.findContaining("world", 12, 62, 12, 0));
        assertNull(index.findContaining("world", 13, 62, 12, 0));
        assertNull(index.findContaining("world", 12, 63, 12, 0));
        assertNull(index.findContaining("other", 10, 60, 10, 0));
    }

    @Test
    void portalSpanningNegativeChunksIsFoundInEveryChunk() {
        // Crosses chunk borders at x=0 and z=0 (and negative coordinates)
        AdvancedPortal a = portal("a", "world", -20, 0, -20, 20, 5, 20);
        PortalIndex index = PortalIndex.build(Arrays.asList(a));

        for (int x : new int[] {-20, -17, -16, -1, 0, 15, 16, 20}) {
            for (int z : new int[] {-20, -1, 0, 16, 20}) {
                assertSame(a, index.findContaining("world", x, 2, z, 0),
                           "x=" + x + " z=" + z);
            }
        }
        assertNull(index.findContaining("world", -21, 2, 0, 0));
        assertNull(index.findContaining("world", 21, 2, 0, 0));
    }

    @Test
    void extraRadiusReachesPortalsInTheNextChunk() {
        // Portal sits at x=16..16 (chunk 1), the query is in chunk 0
        AdvancedPortal a = portal("a", "world", 16, 64, 0, 16, 64, 0);
        PortalIndex index = PortalIndex.build(Arrays.asList(a));

        assertNull(index.findContaining("world", 15, 64, 0, 0));
        assertSame(a, index.findContaining("world", 15, 64, 0, 1));
        assertSame(a, index.findContaining("world", 11, 64, 0, 5));
        assertNull(index.findContaining("world", 10, 64, 0, 5));
        // Vertical radius is applied as well
        assertSame(a, index.findContaining("world", 16, 69, 0, 5));
        assertNull(index.findContaining("world", 16, 70, 0, 5));
    }

    @Test
    void inChunkOfOnlyReturnsOverlappingPortals() {
        AdvancedPortal a = portal("a", "world", 0, 0, 0, 3, 3, 3);
        AdvancedPortal b = portal("b", "world", 32, 0, 32, 35, 3, 35);
        PortalIndex index = PortalIndex.build(Arrays.asList(a, b));

        assertArrayEquals(new AdvancedPortal[] {a}, index.inChunkOf("world", 5, 5));
        assertArrayEquals(new AdvancedPortal[] {b}, index.inChunkOf("world", 33, 40));
        assertEquals(0, index.inChunkOf("world", 100, 100).length);
        assertEquals(2, index.getAll().length);
    }
}
