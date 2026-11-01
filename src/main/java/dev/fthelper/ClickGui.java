package dev.fthelper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Click GUI (открывается на J): карточки модулей слева, настройки выбранного модуля справа. */
public class ClickGui extends Screen {
    // палитра
    private static final int DIM = 0xA0070810;
    private static final int PANEL = 0xF00E1017;
    private static final int BORDER = 0x30FFFFFF;
    private static final int CARD = 0xFF151822;
    private static final int CARD_HOVER = 0xFF1B1F2C;
    private static final int CARD_SEL = 0xFF1E2336;
    private static final int ROW = 0xFF141721;
    private static final int ROW_HOVER = 0xFF1A1E2B;
    private static final int ACCENT = 0xFF7C86F0;
    private static final int ACCENT_DIM = 0x667C86F0;
    private static final int TEXT = 0xFFEDEFFF;
    private static final int DIMTEXT = 0xFF7D8294;
    private static final int OFF = 0xFF2B2F3D;
    private static final int WHITE = 0xFFFFFFFF;

    private enum Mod {
        BINDS("Бинды предметов", "Предмет по клавише"),
        ESP("ESP", "Стрелки к игрокам"),
        FREECAM("Freecam", "Свободная камера"),
        INVIS("See Invisibles", "Видеть невидимых"),
        HUD("HUD", "Ячейки над хотбаром"),
        FRIENDS("Друзья", "Список для ESP");

        final String title;
        final String desc;

        Mod(String title, String desc) {
            this.title = title;
            this.desc = desc;
        }

        boolean hasToggle() {
            return this == ESP || this == FREECAM || this == INVIS || this == HUD;
        }
    }

    private interface Painter {
        void paint(DrawContext g, Flat f, boolean hovered);
    }

    private static Mod selected = Mod.BINDS;

    private final List<ClickableWidget> widgets = new ArrayList<>();
    private IntConsumer listenSetter;
    private ClickableWidget listenBtn;

    private int mainX, mainY, mainW, mainH;
    private int setX, setY, setH;
    private final int setW = 210;
    private boolean showMain, showSettings;

    public ClickGui() {
        super(Text.literal("FT Helper"));
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return listenSetter == null; }

    @Override
    public void removed() { Config.save(); }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // свой фон рисуется в render()
    }

    // ───────────────────────── рисование ─────────────────────────

    private static void rr(DrawContext g, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double d = r - i - 0.5;
            int dx = r - (int) Math.round(Math.sqrt(r * r - d * d));
            g.fill(x + dx, y + i, x + w - dx, y + i + 1, color);
            g.fill(x + dx, y + h - 1 - i, x + w - dx, y + h - i, color);
        }
    }

    private static void panel(DrawContext g, int x, int y, int w, int h) {
        rr(g, x - 1, y - 1, w + 2, h + 2, 6, BORDER);
        rr(g, x, y, w, h, 6, PANEL);
    }

    private static void pill(DrawContext g, int x, int y, boolean on) {
        rr(g, x, y, 22, 11, 5, on ? ACCENT : OFF);
        rr(g, on ? x + 12 : x + 2, y + 2, 7, 7, 3, WHITE);
    }

    private int tw(String s) {
        return textRenderer.getWidth(Fonts.t(s));
    }

    private void txt(DrawContext g, String s, int x, int y, int color) {
        g.drawText(textRenderer, Fonts.t(s), x, y, color, false);
    }

    private String fit(String s, int maxW) {
        if (tw(s) <= maxW) return s;
        while (s.length() > 1 && tw(s + "..") > maxW) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    // ───────────────────────── виджеты ─────────────────────────

    /**
     * Кнопка с собственной отрисовкой.
     * В 1.21.11 ButtonWidget абстрактный, renderWidget у него final, а внутри есть свой класс Text,
     * поэтому Text здесь пишем полным именем, фон гасим через setAlpha(0), а рисуем в drawIcon.
     */
    private class Flat extends ButtonWidget {
        private final Painter painter;
        int lastMouseX;

        Flat(int x, int y, int w, int h, PressAction action, Painter painter) {
            super(x, y, w, h, net.minecraft.text.Text.empty(), action, s -> s.get());
            this.painter = painter;
            setAlpha(0f); // стандартный фон кнопки становится невидимым, рисуем свой
        }

        @Override
        public void drawIcon(DrawContext g, int mouseX, int mouseY, float delta) {
            lastMouseX = mouseX;
            painter.paint(g, this, isHovered());
        }
    }

    /** Ползунок: тянется мышью, значение считается по позиции курсора. */
    private class SliderW extends ButtonWidget {
        private final String label;
        private final String fmt;
        private final DoubleSupplier get;
        private final DoubleConsumer set;
        private final double min, max, step;
        private boolean dragging;
        private int lastMouseX;

        SliderW(int x, int y, int w, int h, String label, DoubleSupplier get, DoubleConsumer set,
                double min, double max, double step, String fmt) {
            super(x, y, w, h, net.minecraft.text.Text.empty(), b -> ((SliderW) b).begin(), s -> s.get());
            this.label = label;
            this.get = get;
            this.set = set;
            this.min = min;
            this.max = max;
            this.step = step;
            this.fmt = fmt;
            setAlpha(0f); // стандартный фон кнопки становится невидимым, рисуем свой
        }

        void begin() {
            dragging = true;
            apply(lastMouseX);
        }

        private void apply(int mx) {
            int trackX = getX() + 8;
            int trackW = getWidth() - 16;
            double t = Math.max(0, Math.min(1, (mx - trackX) / (double) trackW));
            double v = min + t * (max - min);
            v = Math.round(v / step) * step;
            v = Math.round(v * 100) / 100.0;
            v = Math.max(min, Math.min(max, v));
            set.accept(v);
        }

        @Override
        public void drawIcon(DrawContext g, int mouseX, int mouseY, float delta) {
            lastMouseX = mouseX;
            if (dragging) {
                long h = MinecraftClient.getInstance().getWindow().getHandle();
                if (GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
                    dragging = false;
                    Config.save();
                } else {
                    apply(mouseX);
                }
            }
            int x = getX(), y = getY(), w = getWidth();
            rr(g, x, y, w, getHeight(), 3, (isHovered() || dragging) ? ROW_HOVER : ROW);

            double value = get.getAsDouble();
            String vs = String.format(Locale.ROOT, fmt, value);
            txt(g, label, x + 8, y + 5, TEXT);
            txt(g, vs, x + w - 8 - tw(vs), y + 5, DIMTEXT);

            double t = Math.max(0, Math.min(1, (value - min) / (max - min)));
            int trackW = w - 16;
            int filled = (int) Math.round(trackW * t);
            rr(g, x + 8, y + 19, trackW, 3, 1, OFF);
            rr(g, x + 8, y + 19, Math.max(3, filled), 3, 1, ACCENT);
            rr(g, x + 8 + filled - 3, y + 17, 7, 7, 3, WHITE);
        }
    }

    private <T extends ClickableWidget> T add(T w) {
        addDrawableChild(w);
        widgets.add(w);
        return w;
    }

    private boolean modOn(Mod m, Config c) {
        return switch (m) {
            case ESP -> c.espEnabled;
            case FREECAM -> Freecam.isActive();
            case INVIS -> c.seeInvisibles;
            case HUD -> c.hudEnabled;
            default -> false;
        };
    }

    private void flip(Mod m, Config c) {
        switch (m) {
            case ESP -> c.espEnabled = !c.espEnabled;
            case FREECAM -> Freecam.toggle(client);
            case INVIS -> c.seeInvisibles = !c.seeInvisibles;
            case HUD -> c.hudEnabled = !c.hudEnabled;
            default -> { }
        }
        Config.save();
    }

    // ───────────────────────── раскладка ─────────────────────────

    @Override
    protected void init() {
        widgets.clear();
        Config c = Config.get();

        final int pad = 8, cardW = 140, cardH = 44, gap = 6, headerH = 28, rows = 3, panelGap = 10;
        mainW = pad * 2 + cardW * 2 + gap;
        mainH = headerH + rows * cardH + (rows - 1) * gap + pad;

        boolean narrow = width < mainW + panelGap + setW + 12;
        showSettings = selected != null;
        showMain = !(narrow && showSettings);

        int total = (showMain ? mainW : 0) + (showSettings ? (showMain ? panelGap : 0) + setW : 0);
        mainX = (width - total) / 2;
        mainY = Math.max(6, (height - mainH) / 2 - 10);
        setX = showMain ? mainX + mainW + panelGap : mainX;
        setY = mainY;

        if (showMain) {
            Mod[] mods = Mod.values();
            for (int i = 0; i < mods.length; i++) {
                final Mod m = mods[i];
                int cx = mainX + pad + (i % 2) * (cardW + gap);
                int cy = mainY + headerH + (i / 2) * (cardH + gap);
                add(new Flat(cx, cy, cardW, cardH, b -> {
                    Flat f = (Flat) b;
                    if (m.hasToggle() && f.lastMouseX >= f.getX() + f.getWidth() - 36) {
                        flip(m, c);
                    } else {
                        selected = m;
                        listenSetter = null;
                        listenBtn = null;
                        clearAndInit();
                    }
                }, (g, f, hov) -> {
                    int x = f.getX(), y = f.getY(), w = f.getWidth(), h = f.getHeight();
                    boolean sel = selected == m;
                    if (sel) rr(g, x - 1, y - 1, w + 2, h + 2, 5, ACCENT_DIM);
                    rr(g, x, y, w, h, 4, sel ? CARD_SEL : (hov ? CARD_HOVER : CARD));
                    int textW = w - 16 - (m.hasToggle() ? 30 : 0);
                    txt(g, fit(m.title, textW), x + 8, y + 9, TEXT);
                    txt(g, fit(m.desc, w - 16), x + 8, y + 26, DIMTEXT);
                    if (m.hasToggle()) pill(g, x + w - 30, y + 8, modOn(m, c));
                }));
            }
        }

        if (showSettings) buildSettings(c, pad);
    }

    private void buildSettings(Config c, int pad) {
        int x = setX + pad;
        int w = setW - pad * 2;
        int y = setY + 30;

        // закрыть панель настроек
        add(new Flat(setX + setW - pad - 14, setY + 7, 14, 14, b -> {
            selected = null;
            listenSetter = null;
            listenBtn = null;
            clearAndInit();
        }, (g, f, hov) -> {
            rr(g, f.getX(), f.getY(), 14, 14, 3, hov ? OFF : ROW);
            txt(g, "x", f.getX() + 7 - tw("x") / 2, f.getY() + 3, hov ? WHITE : DIMTEXT);
        }));

        switch (selected) {
            case BINDS -> {
                for (Config.ItemBind b : c.binds) {
                    ItemStack icon = new ItemStack(ItemBinds.item(b.item));
                    keyRow(x, y, w, icon.getName().getString(), () -> b.key, v -> b.key = v, icon);
                    y += 22;
                }
                y += 4;
                toggleRow(x, y, w, "Стоп при использовании", () -> c.freezeWhileUsing,
                        () -> { c.freezeWhileUsing = !c.freezeWhileUsing; Config.save(); });
                y += 22;
                toggleRow(x, y, w, "Возврат предмета", () -> c.returnItem,
                        () -> { c.returnItem = !c.returnItem; Config.save(); });
                y += 22;
            }
            case ESP -> {
                keyRow(x, y, w, "Бинд ESP", () -> c.espKey, v -> c.espKey = v, null); y += 22;
                add(new SliderW(x, y, w, 28, "Расстояние от прицела", () -> c.espRadius,
                        v -> c.espRadius = (int) Math.round(v), 10, 200, 5, "%.0f")); y += 32;
                add(new SliderW(x, y, w, 28, "Макс. дистанция", () -> c.espMaxDistance,
                        v -> c.espMaxDistance = (int) Math.round(v), 10, 1000, 10, "%.0f")); y += 32;

                // цвет: подпись, образец и поле HEX
                add(new Flat(x, y, w - 94, 20, b -> { }, (g, f, hov) -> {
                    rr(g, f.getX(), f.getY(), f.getWidth(), 20, 3, ROW);
                    txt(g, "Цвет ESP", f.getX() + 8, f.getY() + 6, TEXT);
                    rr(g, f.getX() + f.getWidth() - 20, f.getY() + 4, 12, 12, 3, Config.get().espColorArgb());
                }));
                TextFieldWidget color = new TextFieldWidget(textRenderer, x + w - 90, y, 90, 20, Text.literal("Цвет"));
                color.setMaxLength(7);
                color.setText(c.espColor);
                color.setChangedListener(s -> c.espColor = s);
                add(color);
                y += 24;
                note(x, y, w, "Друзья всегда зелёные"); y += 16;
            }
            case FREECAM -> {
                keyRow(x, y, w, "Бинд Freecam", () -> c.freecamKey, v -> c.freecamKey = v, null); y += 22;
                add(new SliderW(x, y, w, 28, "Скорость", () -> c.freecamSpeed,
                        v -> c.freecamSpeed = v, 0.1, 10, 0.1, "%.1f")); y += 32;
            }
            case INVIS -> {
                keyRow(x, y, w, "Бинд See Invisibles", () -> c.seeInvisKey, v -> c.seeInvisKey = v, null); y += 22;
                add(new SliderW(x, y, w, 28, "Видимость", () -> c.invisAlpha,
                        v -> c.invisAlpha = (int) Math.round(v), 30, 100, 5, "%.0f%%")); y += 32;
            }
            case HUD -> {
                add(new SliderW(x, y, w, 28, "Размер", () -> c.hudScale,
                        v -> c.hudScale = v, 0.5, 2.0, 0.1, "%.1f")); y += 32;
                add(new SliderW(x, y, w, 28, "Высота над низом", () -> c.hudYOffset,
                        v -> c.hudYOffset = (int) Math.round(v), 40, 250, 5, "%.0f")); y += 32;
                keyRow(x, y, w, "Клавиша Click GUI", () -> c.guiKey, v -> c.guiKey = v, null); y += 22;
            }
            case FRIENDS -> {
                TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, w - 70, 20, Text.literal("Ник"));
                field.setMaxLength(16);
                field.setPlaceholder(Fonts.t("Ник игрока"));
                add(field);
                add(new Flat(x + w - 66, y, 66, 20, b -> {
                    String n = field.getText().trim();
                    if (!n.isEmpty() && !c.isFriend(n)) {
                        c.friends.add(n);
                        Config.save();
                        clearAndInit();
                    }
                }, (g, f, hov) -> {
                    rr(g, f.getX(), f.getY(), 66, 20, 3, hov ? 0xFF8F98F5 : ACCENT);
                    String s = "Добавить";
                    txt(g, s, f.getX() + 33 - tw(s) / 2, f.getY() + 6, WHITE);
                }));
                y += 26;
                int shown = 0;
                for (String name : c.friends) {
                    if (shown++ >= 8) break;
                    add(new Flat(x, y, w - 24, 20, b -> { }, (g, f, hov) -> {
                        rr(g, f.getX(), f.getY(), f.getWidth(), 20, 3, ROW);
                        txt(g, fit(name, f.getWidth() - 16), f.getX() + 8, f.getY() + 6, TEXT);
                    }));
                    add(new Flat(x + w - 20, y, 20, 20, b -> {
                        c.friends.removeIf(s -> s.equalsIgnoreCase(name));
                        Config.save();
                        clearAndInit();
                    }, (g, f, hov) -> {
                        rr(g, f.getX(), f.getY(), 20, 20, 3, hov ? 0xFF5A2B33 : ROW);
                        txt(g, "x", f.getX() + 10 - tw("x") / 2, f.getY() + 6, hov ? WHITE : DIMTEXT);
                    }));
                    y += 22;
                }
            }
        }

        setH = (y - setY) + pad - 2;
    }

    private void toggleRow(int x, int y, int w, String label, BooleanSupplier get, Runnable flip) {
        add(new Flat(x, y, w, 20, b -> flip.run(), (g, f, hov) -> {
            rr(g, f.getX(), f.getY(), w, 20, 3, hov ? ROW_HOVER : ROW);
            txt(g, fit(label, w - 44), f.getX() + 8, f.getY() + 6, TEXT);
            pill(g, f.getX() + w - 30, f.getY() + 4, get.getAsBoolean());
        }));
    }

    private void keyRow(int x, int y, int w, String label, IntSupplier get, IntConsumer set, ItemStack icon) {
        add(new Flat(x, y, w, 20, b -> {
            listenBtn = b;
            listenSetter = set;
        }, (g, f, hov) -> {
            rr(g, f.getX(), f.getY(), w, 20, 3, hov ? ROW_HOVER : ROW);
            int tx = f.getX() + 8;
            if (icon != null) {
                g.drawItem(icon, f.getX() + 3, f.getY() + 2);
                tx = f.getX() + 24;
            }
            boolean listening = listenBtn == f;
            String k = listening ? "..." : Keys.name(get.getAsInt());
            int bw = Math.max(30, tw(k) + 12);
            txt(g, fit(label, w - bw - 12 - (tx - f.getX())), tx, f.getY() + 6, TEXT);
            rr(g, f.getX() + w - bw - 4, f.getY() + 3, bw, 14, 3, listening ? ACCENT : OFF);
            txt(g, k, f.getX() + w - bw - 4 + (bw - tw(k)) / 2, f.getY() + 6, WHITE);
        }));
    }

    private void note(int x, int y, int w, String text) {
        add(new Flat(x, y, w, 12, b -> { }, (g, f, hov) -> txt(g, fit(text, w), f.getX() + 2, f.getY() + 2, DIMTEXT)));
    }

    // ───────────────────────── отрисовка экрана ─────────────────────────

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, DIM);

        if (showMain) {
            panel(g, mainX, mainY, mainW, mainH);
            txt(g, "FT Helper", mainX + 10, mainY + 10, TEXT);
            String hint = "Click GUI [" + Keys.name(Config.get().guiKey) + "]";
            txt(g, hint, mainX + mainW - 10 - tw(hint), mainY + 10, DIMTEXT);
        }
        if (showSettings && selected != null) {
            panel(g, setX, setY, setW, setH);
            txt(g, selected.title, setX + 10, setY + 10, TEXT);
        }

        for (ClickableWidget w : widgets) w.render(g, mouseX, mouseY, delta);

        if (listenSetter != null) pollKey();
    }

    // ───────────────────────── назначение клавиш ─────────────────────────

    private void pollKey() {
        long h = client.getWindow().getHandle();
        for (int k = 32; k <= 348; k++) {
            if (GLFW.glfwGetKey(h, k) != GLFW.GLFW_PRESS) continue;
            if (k == GLFW.GLFW_KEY_ESCAPE) finishListen(null);
            else if (k == GLFW.GLFW_KEY_BACKSPACE || k == GLFW.GLFW_KEY_DELETE) finishListen(Config.NONE);
            else finishListen(k);
            return;
        }
        for (int b = 1; b <= 7; b++) { // ЛКМ (0) пропускаем: им нажимали на кнопку
            if (GLFW.glfwGetMouseButton(h, b) == GLFW.GLFW_PRESS) {
                finishListen(Config.MOUSE + b);
                return;
            }
        }
    }

    private void finishListen(Integer value) {
        if (value != null) listenSetter.accept(value);
        listenSetter = null;
        listenBtn = null;
        Config.save();
    }
}
