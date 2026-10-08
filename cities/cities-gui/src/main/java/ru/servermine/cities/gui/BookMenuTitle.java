package ru.servermine.cities.gui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/** Positioned, server-owned text over the cosmetic parchment background. */
final class BookMenuTitle {
    private static final Key SPACE = Key.key("gradostroy", "book/space");
    private static final TextColor INK = TextColor.color(0x28160D);
    private static final TextColor VALUE = TextColor.color(0x711A16);

    private BookMenuTitle() { }

    static Component create(MenuType type, CitySnapshot city) {
        // Inventory labels start at x=8. The visual cover is 208px wide and
        // intentionally extends sixteen pixels past each inventory edge.
        Component title = Component.empty().append(move(-8))
                .append(move(-8))
                .append(move(-8))
                .append(Component.text("\uE00D").font(Key.key("gradostroy", "gui")).color(NamedTextColor.WHITE))
                // The title origin starts at x=8. After shifting left by 24 and
                // drawing the 208px cover, -192 returns the cursor to x=0.
                .append(move(-192))
                .append(centered(type.fallbackTitle(), 18, 150));
        if (type == MenuType.MAIN && city != null) {
            title = title.append(at(95, city.name(), 27, 73, VALUE))
                    .append(at(95, city.territory() + " / " + city.territoryLimit() + " чанков", 36, 73, INK))
                    .append(at(95, String.format(java.util.Locale.forLanguageTag("ru-RU"), "%,d", city.treasury()), 45, 73, INK))
                    .append(at(95, String.valueOf(city.residents().size()), 54, 73, INK));
        }
        return title;
    }

    private static Component centered(String label, int y, int maximumWidth) {
        String text = BookFont.fit(label, maximumWidth);
        int width = BookFont.width(text);
        int x = (176 - width) / 2;
        return Component.empty().append(move(x))
                .append(Component.text(text).font(Key.key("gradostroy", "book/line_" + y)).color(INK))
                .append(move(-x - width));
    }

    private static Component at(int x, String label, int y, int maximumWidth, TextColor color) {
        String text = BookFont.fit(label, maximumWidth);
        return Component.empty().append(move(x))
                .append(Component.text(text).font(Key.key("gradostroy", "book/line_" + y)).color(color))
                .append(move(-x - BookFont.width(text)));
    }

    private static Component move(int pixels) {
        if (pixels < -256 || pixels > 256) throw new IllegalArgumentException("Book text offset outside atlas");
        return Component.text(String.valueOf((char) (0xE200 + pixels))).font(SPACE);
    }
}
