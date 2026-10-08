package ru.servermine.cities.gui;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MainMenuLayoutTest {
    @Test
    void everyMainPageSlotMatchesItsVisibleButtonOrGap() {
        int[] expected = {
                -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, 20, 20, 20, -1, 24, 24, 24, -1,
                -1, 29, 29, 29, -1, 33, 33, 33, -1,
                -1, 38, 38, 38, -1, 42, 42, 42, -1,
                -1, 47, 47, 47, -1, 51, 51, 51, -1
        };
        for (int slot = 0; slot < expected.length; slot++) {
            assertEquals(expected[slot], MainMenuLayout.actionSlot(slot), "Slot " + slot);
        }
    }

    @Test
    void buttonRegionsAreDisjointAndRemainInsideTheirRows() {
        Set<Integer> occupied = new HashSet<>();
        for (int center : MainMenuLayout.centers()) {
            for (int slot = center - 1; slot <= center + 1; slot++) {
                assertEquals(center / 9, slot / 9, "Button crosses a row at " + slot);
                assertTrue(occupied.add(slot), "Buttons overlap at " + slot);
                assertNotEquals(-1, MainMenuLayout.actionSlot(slot));
            }
        }
        assertEquals(24, occupied.size());
        for (int row = 0; row < 6; row++) {
            assertEquals(-1, MainMenuLayout.actionSlot(row * 9), "Left page margin");
            assertEquals(-1, MainMenuLayout.actionSlot(row * 9 + 8), "Right page margin");
        }
    }

    @Test
    void playerInventoryAndOutsideClicksCannotTriggerMainActions() {
        for (int slot = 54; slot < 90; slot++) {
            assertEquals(-1, MainMenuLayout.actionSlot(slot));
        }
        for (int slot : new int[]{-999, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertEquals(-1, MainMenuLayout.actionSlot(slot));
        }
    }

    @Test
    void adminAndMarketKeepSeparateActionsAndAnInactiveGap() {
        for (int slot : new int[]{46, 47, 48}) {
            assertEquals(47, MainMenuLayout.actionSlot(slot));
        }
        assertEquals(-1, MainMenuLayout.actionSlot(49));
        for (int slot : new int[]{50, 51, 52}) {
            assertEquals(51, MainMenuLayout.actionSlot(slot));
        }
    }

    @Test
    void callersCannotMutateSharedButtonPositions() {
        int[] centers = MainMenuLayout.centers();
        centers[0] = 0;
        assertArrayEquals(new int[]{20, 24, 29, 33, 38, 42, 47, 51}, MainMenuLayout.centers());
        assertEquals(20, MainMenuLayout.actionSlot(20));
        assertEquals(-1, MainMenuLayout.actionSlot(0));
    }
}
