package dev.fthelper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Шрифт GeosansLight для всех надписей мода.
 * Файл шрифта: src/main/resources/assets/fthelper/font/geosanslight.ttf
 * Если файла нет, используется обычный шрифт Minecraft.
 */
public class Fonts {
    private static final Identifier FONT_ID = Identifier.of("fthelper", "geosanslight");
    private static final Identifier FONT_FILE = Identifier.of("fthelper", "font/geosanslight.ttf");

    private static Style style = Style.EMPTY;
    private static boolean checked = false;

    public static Style style() {
        if (!checked) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getResourceManager() == null) return Style.EMPTY;
            checked = true;
            boolean present = false;
            try {
                present = mc.getResourceManager().getResource(FONT_FILE).isPresent();
            } catch (Exception ignored) {
            }
            style = present ? Style.EMPTY.withFont(new StyleSpriteSource.Font(FONT_ID)) : Style.EMPTY;
        }
        return style;
    }

    public static MutableText t(String s) {
        return Text.literal(s).setStyle(style());
    }
}
