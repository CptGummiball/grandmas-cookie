package dev.cptgummiball.omacore.core.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HouseCoreTest {
    private static final Path COTTAGE = Path.of("..", "neoforge", "src", "main", "resources", "oma_core", "house", "cottage.txt");

    @Test
    void shippedCottageParsesWithMarkersInsideTheHouse() throws IOException, BlueprintException {
        Blueprint b = BlueprintParser.parse(Files.readString(COTTAGE, StandardCharsets.UTF_8));
        assertEquals(11, b.sizeX());
        assertEquals(9, b.sizeY());
        assertEquals(9, b.sizeZ());
        Blueprint.Marker arrival = b.marker("arrival").orElseThrow();
        Blueprint.Marker grandma = b.marker("grandma").orElseThrow();
        // both stand on the floor (layer 0 is solid) with two free blocks above
        for (Blueprint.Marker m : List.of(arrival, grandma)) {
            assertTrue(b.isFoundation(m.x(), m.z()));
            assertTrue(b.stateAt(m.x(), m.y() + 1, m.z()).orElseThrow().equals(Blueprint.AIR_STATE));
        }
        assertEquals(Optional.of("minecraft:red_carpet"), b.stateAt(grandma.x(), grandma.y(), grandma.z()));
        assertEquals(Optional.of(Blueprint.AIR_STATE), b.stateAt(arrival.x(), arrival.y(), arrival.z()));
        assertEquals(Optional.of("minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false]"), b.stateAt(5, 1, 8));
        // every palette entry is vanilla or ends in a vanilla fallback, so the cottage builds without optional mods
        assertTrue(b.palette().values().stream()
                .allMatch(s -> Blueprint.alternatives(s).get(Blueprint.alternatives(s).size() - 1).startsWith("minecraft:")));
        // Grandma's stuck kitchen drawer (C00_Q05): a Macaw's drawer counter facing into the kitchen, barrel as fallback
        Blueprint.Marker drawer = b.marker("drawer").orElseThrow();
        assertEquals(List.of("mcwfurnitures:spruce_drawer_counter[facing=north]", "minecraft:barrel[facing=south]"),
                Blueprint.alternatives(b.stateAt(drawer.x(), drawer.y(), drawer.z()).orElseThrow()));
        assertTrue(b.isFoundation(drawer.x(), drawer.z()));
    }

    @Test
    void structureCellsAreTheHousesOwnBlocksOnly() throws IOException, BlueprintException {
        Blueprint b = BlueprintParser.parse(Files.readString(COTTAGE, StandardCharsets.UTF_8));
        assertTrue(b.isStructure(0, 1, 3));    // west wall
        assertTrue(b.isStructure(5, 1, 8));    // front door
        assertTrue(b.isStructure(4, 1, 1));    // Grandma's drawer
        assertTrue(b.isStructure(5, 0, 5));    // floor
        assertFalse(b.isStructure(5, 1, 5));   // free space inside: players may build there
        assertFalse(b.isStructure(0, 5, 0));   // air above the eaves
        assertFalse(b.isStructure(-1, 1, 3));  // outside the plan
        assertFalse(b.isStructure(11, 1, 3));
        assertFalse(b.isStructure(5, 9, 5));
        Blueprint keep = BlueprintParser.parse("size 2 1 1\nkey A minecraft:stone\nlayer 0\nA_");
        assertTrue(keep.isStructure(0, 0, 0));
        assertFalse(keep.isStructure(1, 0, 0)); // '_' leaves the world block alone and is not protected
    }

    @Test
    void paletteAlternativesKeepOrderAndIgnoreBlanks() {
        assertEquals(List.of("a:b[x=1]", "minecraft:barrel"), Blueprint.alternatives("a:b[x=1]| minecraft:barrel |"));
        assertEquals(List.of("minecraft:stone"), Blueprint.alternatives("minecraft:stone"));
    }

    @Test
    void parserRejectsBrokenPlans() {
        assertEquals(4, assertThrows(BlueprintException.class, // the short row is line 4
                () -> BlueprintParser.parse("size 2 1 1\nkey A minecraft:stone\nlayer 0\nA")).line());
        assertThrows(BlueprintException.class, () -> BlueprintParser.parse("size 1 1 1\nlayer 0\nX"));  // undefined key
        assertThrows(BlueprintException.class, () -> BlueprintParser.parse("size 1 2 1\nlayer 0\n."));  // layer 1 missing
        assertThrows(BlueprintException.class,
                () -> BlueprintParser.parse("size 1 1 1\nlayer 0\n.\nmarker m 1 0 0 0"));                // outside
        assertThrows(BlueprintException.class, () -> BlueprintParser.parse("size 1 1 1\nkey . minecraft:stone"));
        assertThrows(BlueprintException.class, () -> BlueprintParser.parse("size 1 1 1\nlayer 0\n.\nlayer 0\n."));
    }

    @Test
    void keepCellsAreNotPlaced() throws BlueprintException {
        Blueprint b = BlueprintParser.parse("size 2 1 1\nkey A minecraft:stone\nlayer 0\nA_");
        assertEquals(Optional.of("minecraft:stone"), b.stateAt(0, 0, 0));
        assertEquals(Optional.empty(), b.stateAt(1, 0, 0));
        assertFalse(b.isFoundation(1, 0));
    }

    @Test
    void sitePickerPrefersDryFlatLandNearSpawn() {
        SitePicker.Candidate wet = new SitePicker.Candidate(0, 0, new int[]{64, 64, 64}, true);
        SitePicker.Candidate hilly = new SitePicker.Candidate(8, 0, new int[]{60, 70, 66}, false);
        SitePicker.Candidate flatFar = new SitePicker.Candidate(24, 24, new int[]{70, 71, 70}, false);
        SitePicker.Candidate flatNear = new SitePicker.Candidate(8, 8, new int[]{71, 70, 70}, false);
        assertEquals(flatNear, SitePicker.pick(List.of(wet, hilly, flatFar, flatNear)).orElseThrow());
        assertEquals(hilly, SitePicker.pick(List.of(wet, hilly)).orElseThrow()); // hilly beats water
        assertTrue(SitePicker.pick(List.of(wet)).isEmpty());
        assertEquals(66, hilly.floorY());
    }

    @Test
    void houseAreaIncludesMargin() {
        HouseArea a = HouseArea.of(100, 64, 200, 11, 9, 9);
        assertTrue(a.contains(97, 61, 197));
        assertTrue(a.contains(113, 75, 211));
        assertFalse(a.contains(114, 64, 200));
        assertFalse(a.contains(100, 76, 200));
    }
}
