package ru.servermine.cities.gui;

/** Click regions for the three-slot-wide buttons on the book's main page. */
final class MainMenuLayout {
    private static final int COLUMNS = 9;
    private static final int MENU_SIZE = 54;
    private static final int[] CENTERS = {20, 24, 29, 33, 38, 42, 47, 51};

    private MainMenuLayout() {
    }

    static int[] centers() {
        return CENTERS.clone();
    }

    /** Returns the existing menu action slot, or -1 outside the button regions. */
    static int actionSlot(int rawSlot) {
        if (rawSlot < 0 || rawSlot >= MENU_SIZE) return -1;
        for (int center : CENTERS) {
            if (rawSlot / COLUMNS == center / COLUMNS && Math.abs(rawSlot - center) <= 1) {
                return center;
            }
        }
        return -1;
    }
}
