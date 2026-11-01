package dev.fthelper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Полностью кастомный Click GUI в стиле референса. */
public class ClickGui extends Screen {
    // Палитра (взята с референса)
    private static final int DIM = 0x99000000;
    private static final int PANEL_BG = 0xFF16161A;
    private static final int PANEL_BORDER = 0xFF2A2A30;
    private static final int HEADER_BG = 0xFF1E1E24;
    private static final int CARD_BG = 0xFF1F1F24;
    private static final int CARD_HOVER = 0xFF2A2A32;
    private static final int CARD_ON = 0xFF25252D;
    private static final int ACCENT = 0xFF7C86F0;
    private static final int TOGGLE_ON = 0xFF7C86F0;
    private static final int TOGGLE_OFF = 0xFF3C3C46;
    private static final int TEXT = 0xFFE8E8F0;
    private static final int DIM_TEXT = 0xFF7C7C88;
    private static final int WHITE = 0xFFFFFFFF;

    // Категории и модули
    private enum Category {
        MISC("Misc", "Предметы и прочее"),
        MOVEMENT("Movement", "Движение"),
        COMBAT("Combat", "Бой"),
        RENDER("Render", "Визуал"),
        PLAYER("Player", "Игрок");

        final String title;
        final String desc;

        Category(String title, String desc) {
            this.title = title;
            this.desc = desc;
        }
    }

    private enum Mod {
        // MISC
        AUCTION_ASSIST("AuctionAssist", "Помогает быстро делать покупки на аукционе.", Category.MISC),
        AUTO_ACCEPT("AutoAccept", "Автоматически принимает входящие приглашения.", Category.MISC),
        AUTO_CLEAR("AutoClear", "Убирает упавший мусор с земли.", Category.MISC),
        AUTO_FISH("AutoFish", "Автоматически кликает и тянет удочку.", Category.MISC),
        AUTO_SETUP("AutoSetup", "Меняет настройки мода под другие.", Category.MISC),
        CHEST_STEALER("ChestStealer", "Быстро забирает предметы из сундуков.", Category.MISC),
        CHAT_MACROS("ChatMacros", "Автоматические сообщения в чат.", Category.MISC),
        CLEANER_EXP("CleanerEXP", "Удаляет опыт с земли.", Category.MISC),
        DISABLER("Disabler", "Обходит проверки анти-чита.", Category.MISC),
        FANCY_CHAT("FancyChat", "Кастомизирует внешний вид чата.", Category.MISC),
        FARM_BOT("FarmBot", "Автоматически сажает и собирает урожай.", Category.MISC),
        FOLLOW_BOT("FollowBot", "Следует за игроком.", Category.MISC),
        GUILD_CHAT("GuildChat", "Быстрый чат для гильдии.", Category.MISC),

        // MOVEMENT (добавляем для визуала)
        SPRINT("Sprint", "Постоянный бег.", Category.MOVEMENT),
        FLY("Fly", "Позволяет летать.", Category.MOVEMENT),
        NO_FALL("NoFall", "Отключает урон от падения.", Category.MOVEMENT),

        // RENDER (наши модули)
        ESP("ESP", "Стрелки к игрокам.", Category.RENDER),
        FREECAM("Freecam", "Свободная камера.", Category.RENDER),
        INVIS("See Invisibles", "Видеть невидимых.", Category.RENDER),
        HUD("HUD", "Ячейки над хотбаром.", Category.RENDER),

        // PLAYER (наши модули)
        BINDS("Бинды предметов", "Предмет по клавише.", Category.PLAYER),
        FRIENDS("Друзья", "Список для ESP.", Category.PLAYER);

        final String title;
        final String desc;
        final Category category;

        Mod(String title, String desc, Category category) {
            this.title = title;
            this.desc = desc;
            this.category = category;
        }

        boolean hasToggle() {
            return switch (this) {
                case ESP, FREECAM, INVIS, HUD, SPRINT, FLY, NO_FALL, AUTO_FISH, CHEST_STEALER, FARM_BOT, CLEANER_EXP, DISABLER -> true;
                default -> false;
            };
        }
    }

    private interface Painter {
        void paint(DrawContext g, Flat f, boolean hovered);
    }

    private static Category selectedCategory = Category.MISC;
    private static Mod selectedMod = Mod.CHEST_STEALER;
    
    // Состояние для визуальных тумблеров
    private static final Map<Mod, Boolean> toggles = new HashMap<>();

    private final List<ClickableWidget> widgets = new ArrayList<>();
    private IntConsumer listenSetter;
    private ClickableWidget listenBtn;

    // Геометрия основного окна
    private int mainX, mainY, mainW, mainH;
    
    // Геометрия панели настроек
    private int setX, setY, setW, setH;
    
    // Геометрия панели бота (FightBot/Keybind)
    private int botX, botY, botW, botH;
    private TextFieldWidget keybindField;
    
    // Позиции для скролла (заглушка)
    private int scrollOffset = 0;

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
        // Фон не нужен, рисуем в render
    }

    // ───────────────────────── Утилиты рисования ─────────────────────────

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

    private static void border(DrawContext g, int x, int y, int w, int h, int r, int color) {
        rr(g, x, y, w, h, r, color);
        rr(g, x + 1, y + 1, w - 2, h - 2, r - 1, PANEL_BG);
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

    private void drawCheckbox(DrawContext g, int x, int y, boolean checked) {
        rr(g, x, y, 10, 10, 2, checked ? ACCENT : TOGGLE_OFF);
        if (checked) {
            rr(g, x + 2, y + 2, 6, 6, 1, WHITE);
        }
    }

    // ───────────────────────── Виджеты ─────────────────────────

    private class Flat extends ClickableWidget {
        private final Painter painter;
        int lastMouseX;

        Flat(int x, int y, int w, int h, PressAction action, Painter painter) {
            super(x, y, w, h, Text.empty());
            this.painter = painter;
            this.active = true;
            this.visible = true;
            this.setOnClick(action); // В 1.21.11 используется setOnClick
        }

        @Override
        public void renderWidget(DrawContext g, int mouseX, int mouseY, float delta) {
            lastMouseX = mouseX;
            painter.paint(g, this, isHovered());
        }
    }

    // Заглушка PressAction, если в твоей версии её нет
    private interface PressAction {
        void onPress(Flat b);
    }

    private class SliderW extends ClickableWidget {
        private final String label;
        private final String fmt;
        private final DoubleSupplier get;
        private final DoubleConsumer set;
        private final double min, max, step;
        private boolean dragging;
        private int lastMouseX;

        SliderW(int x, int y, int w, int h, String label, DoubleSupplier get, DoubleConsumer set,
                double min, double max, double step, String fmt) {
            super(x, y, w, h, Text.empty());
            this.label = label;
            this.get = get;
            this.set = set;
            this.min = min;
            this.max = max;
            this.step = step;
            this.fmt = fmt;
            this.active = true;
            this.visible = true;
            this.setOnClick(b -> ((SliderW) b).begin());
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
        public void renderWidget(DrawContext g, int mouseX, int mouseY, float delta) {
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
            rr(g, x, y, w, getHeight(), 3, isHovered() || dragging ? CARD_HOVER : CARD_BG);

            double value = get.getAsDouble();
            String vs = String.format(Locale.ROOT, fmt, value);
            txt(g, label, x + 8, y + 5, TEXT);
            txt(g, vs, x + w - 8 - tw(vs), y + 5, DIM_TEXT);

            double t = Math.max(0, Math.min(1, (value - min) / (max - min)));
            int trackW = w - 16;
            int filled = (int) Math.round(trackW * t);
            rr(g, x + 8, y + 19, trackW, 3, 1, TOGGLE_OFF);
            rr(g, x + 8, y + 19, Math.max(3, filled), 3, 1, ACCENT);
            rr(g, x + 8 + filled - 3, y + 17, 7, 7, 3, WHITE);
        }
    }

    private <T extends ClickableWidget> T add(T w) {
        addDrawableChild(w);
        widgets.add(w);
        return w;
    }

    // ───────────────────────── Логика модулей ─────────────────────────

    private boolean modOn(Mod m, Config c) {
        return switch (m) {
            case ESP -> c.espEnabled;
            case FREECAM -> Freecam.isActive();
            case INVIS -> c.seeInvisibles;
            case HUD -> c.hudEnabled;
            default -> toggles.getOrDefault(m, false);
        };
    }

    private void flip(Mod m, Config c) {
        switch (m) {
            case ESP -> c.espEnabled = !c.espEnabled;
            case FREECAM -> Freecam.toggle(client);
            case INVIS -> c.seeInvisibles = !c.seeInvisibles;
            case HUD -> c.hudEnabled = !c.hudEnabled;
            default -> toggles.put(m, !toggles.getOrDefault(m, false));
        }
        Config.save();
    }

    // ───────────────────────── Инициализация и раскладка ─────────────────────────

    @Override
    protected void init() {
        widgets.clear();
        Config c = Config.get();

        final int pad = 8, headerH = 26;
        final int cardW = 130, cardH = 44, gap = 6;
        
        // Основная панель
        mainW = pad * 2 + cardW * 3 + gap * 2;
        mainH = headerH + 4 * cardH + 3 * gap + pad; // 4 ряда карточек
        mainX = width / 2 - mainW / 2 - 30;
        mainY = height / 2 - mainH / 2;

        // Панель настроек (справа)
        setW = 220;
        setX = mainX + mainW + 16;
        setY = mainY - 20;
        
        // Панель бота (слева снизу)
        botW = 200;
        botH = 80;
        botX = mainX - botW - 16;
        botY = mainY + mainH - botH;

        // Если экран узкий, прячем боковые панели
        boolean narrow = width < mainW + setW + botW + 64;
        if (narrow) {
            setX = mainX + mainW + 8;
            botX = mainX - botW - 8;
        }

        // --- Рисуем карточки модулей ---
        Mod[] mods = Mod.values();
        List<Mod> filtered = new ArrayList<>();
        for (Mod m : mods) {
            if (m.category == selectedCategory) filtered.add(m);
        }

        int i = 0;
        for (Mod m : filtered) {
            int cx = mainX + pad + (i % 3) * (cardW + gap);
            int cy = mainY + headerH + (i / 3) * (cardH + gap);
            
            if (cy + cardH > mainY + mainH) break; // не влезает

            add(new Flat(cx, cy, cardW, cardH, b -> {
                Flat f = (Flat) b;
                if (m.hasToggle() && f.lastMouseX >= f.getX() + f.getWidth() - 36) {
                    flip(m, c);
                } else {
                    selectedMod = m;
                    clearAndInit();
                }
            }, (g, f, hov) -> {
                int x = f.getX(), y = f.getY(), w = f.getWidth(), h = f.getHeight();
                boolean sel = selectedMod == m;
                rr(g, x, y, w, h, 4, sel ? CARD_ON : (hov ? CARD_HOVER : CARD_BG));
                
                // Иконка (заглушка)
                rr(g, x + 6, y + 6, 10, 10, 2, ACCENT);
                
                int textW = w - 16 - (m.hasToggle() ? 30 : 0);
                txt(g, fit(m.title, textW), x + 20, y + 6, TEXT);
                txt(g, fit(m.desc, w - 12), x + 6, y + 22, DIM_TEXT);
                
                if (m.hasToggle()) {
                    // Тумблер
                    boolean on = modOn(m, c);
                    rr(g, x + w - 26, y + 6, 20, 10, 5, on ? TOGGLE_ON : TOGGLE_OFF);
                    rr(g, on ? x + w - 15 : x + w - 24, y + 8, 6, 6, 3, WHITE);
                }
            }));
            i++;
        }

        // --- Панель настроек выбранного модуля ---
        if (selectedMod != null) {
            buildSettingsPanel(c, selectedMod);
        }

        // --- Панель FightBot (левая нижняя) ---
        buildBotPanel(c);
    }

    private void buildSettingsPanel(Config c, Mod m) {
        int pad = 8;
        int x = setX + pad;
        int w = setW - pad * 2;
        int y = setY + 30;

        // Закрыть панель
        add(new Flat(setX + setW - 20, setY + 6, 14, 14, b -> {
            selectedMod = null;
            clearAndInit();
        }, (g, f, hov) -> {
            rr(g, f.getX(), f.getY(), 14, 14, 3, hov ? TOGGLE_OFF : HEADER_BG);
            txt(g, "x", f.getX() + 7 - tw("x") / 2, f.getY() + 3, hov ? WHITE : DIM_TEXT);
        }));

        // Заголовок
        txt(setX + pad, setY + 10, m.title, TEXT); // Рисуем прямо в render

        switch (m) {
            case CHEST_STEALER -> {
                // Чекбоксы и ползунок как на референсе
                addCheckboxRow(x, y, w, "Auto Close", () -> toggles.getOrDefault(Mod.CHEST_STEALER, false), 
                    () -> toggles.put(Mod.CHEST_STEALER, !toggles.getOrDefault(Mod.CHEST_STEALER, false))); 
                y += 18;
                addCheckboxRow(x, y, w, "Title Check", () -> true, () -> {}); 
                y += 18;
                add(new SliderW(x, y, w, 28, "Steal Delay", () -> 0.5, v -> {}, 0.1, 2.0, 0.1, "%.1f")); 
                y += 32;
                
                // Item Filter
                txt(g, "Item Filter", x, y, DIM_TEXT);
                txt(g, "2/6", x + w - tw("2/6"), y, DIM_TEXT);
                y += 14;
                
                addCheckboxRow(x, y, w, "Blocks", () -> true, () -> {});
                y += 18;
                addCheckboxRow(x, y, w, "Food", () -> true, () -> {});
                y += 18;
                addCheckboxRow(x, y, w, "Weapon", () -> true, () -> {});
                y += 18;
                
                // Item Name
                txt(g, "Item Name", x, y, DIM_TEXT);
                y += 14;
                TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, w, 20, Text.literal("ItemName"));
                field.setMaxLength(32);
                field.setText("Totem of Undying");
                add(field);
                y += 24;
            }
            case ESP -> {
                keyRow(x, y, w, "Бинд ESP", () -> c.espKey, v -> c.espKey = v, null); y += 22;
                add(new SliderW(x, y, w, 28, "Расстояние от прицела", () -> c.espRadius, v -> c.espRadius = (int) Math.round(v), 10, 200, 5, "%.0f")); y += 32;
                add(new SliderW(x, y, w, 28, "Макс. дистанция", () -> c.espMaxDistance, v -> c.espMaxDistance = (int) Math.round(v), 10, 1000, 10, "%.0f")); y += 32;
            }
            case FREECAM -> {
                keyRow(x, y, w, "Бинд Freecam", () -> c.freecamKey, v -> c.freecamKey = v, null); y += 22;
                add(new SliderW(x, y, w, 28, "Скорость", () -> c.freecamSpeed, v -> c.freecamSpeed = v, 0.1, 10, 0.1, "%.1f")); y += 32;
            }
            case INVIS -> {
                keyRow(x, y, w, "Бинд See Invisibles", () -> c.seeInvisKey, v -> c.seeInvisKey = v, null); y += 22;
                add(new SliderW(x, y, w, 28, "Видимость", () -> c.invisAlpha, v -> c.invisAlpha = (int) Math.round(v), 30, 100, 5, "%.0f%%")); y += 32;
            }
            case HUD -> {
                add(new SliderW(x, y, w, 28, "Размер", () -> c.hudScale, v -> c.hudScale = v, 0.5, 2.0, 0.1, "%.1f")); y += 32;
                add(new SliderW(x, y, w, 28, "Высота над низом", () -> c.hudYOffset, v -> c.hudYOffset = (int) Math.round(v), 40, 250, 5, "%.0f")); y += 32;
                keyRow(x, y, w, "Клавиша Click GUI", () -> c.guiKey, v -> c.guiKey = v, null); y += 22;
            }
            case BINDS -> {
                for (Config.ItemBind b : c.binds) {
                    ItemStack icon = new ItemStack(ItemBinds.item(b.item));
                    keyRow(x, y, w, icon.getName().getString(), () -> b.key, v -> b.key = v, icon);
                    y += 22;
                }
                y += 4;
                toggleRow(x, y, w, "Стоп при использовании", () -> c.freezeWhileUsing, () -> { c.freezeWhileUsing = !c.freezeWhileUsing; Config.save(); }); y += 22;
                toggleRow(x, y, w, "Возврат предмета", () -> c.returnItem, () -> { c.returnItem = !c.returnItem; Config.save(); }); y += 22;
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
                        rr(g, f.getX(), f.getY(), f.getWidth(), 20, 3, CARD_BG);
                        txt(g, fit(name, f.getWidth() - 16), f.getX() + 8, f.getY() + 6, TEXT);
                    }));
                    add(new Flat(x + w - 20, y, 20, 20, b -> {
                        c.friends.removeIf(s -> s.equalsIgnoreCase(name));
                        Config.save();
                        clearAndInit();
                    }, (g, f, hov) -> {
                        rr(g, f.getX(), f.getY(), 20, 20, 3, hov ? 0xFF5A2B33 : CARD_BG);
                        txt(g, "x", f.getX() + 10 - tw("x") / 2, f.getY() + 6, hov ? WHITE : DIM_TEXT);
                    }));
                    y += 22;
                }
            }
            default -> {
                // Заглушка для остальных модулей
                txt(g, "Нет настроек", x, y, DIM_TEXT);
            }
        }

        setH = (y - setY) + 8;
    }

    private void buildBotPanel(Config c) {
        int pad = 8;
        int x = botX + pad;
        int w = botW - pad * 2;
        int y = botY + 28;

        add(new Flat(botX + botW - 20, botY + 6, 14, 14, b -> {}, (g, f, hov) -> {
            rr(g, f.getX(), f.getY(), 14, 14, 3, hov ? TOGGLE_OFF : HEADER_BG);
            txt(g, "x", f.getX() + 7 - tw("x") / 2, f.getY() + 3, hov ? WHITE : DIM_TEXT);
        }));

        txt(g, "FightBot", botX + pad, botY + 10, TEXT);

        // Кнопка включения
        add(new Flat(x, y, w, 20, b -> { }, (g, f, hov) -> {
            rr(g, f.getX(), f.getY(), w, 20, 3, hov ? CARD_HOVER : CARD_BG);
            txt(g, "Включить", f.getX() + 8, f.getY() + 6, TEXT);
        }));
        y += 22;

        // Keybind
        txt(g, "Keybind", x, y, DIM_TEXT);
        y += 12;
        keybindField = new TextFieldWidget(textRenderer, x, y, w, 20, Text.literal("Keybind"));
        keybindField.setMaxLength(16);
        add(keybindField);
    }

    private void addCheckboxRow(int x, int y, int w, String label, BooleanSupplier get, Runnable flip) {
        add(new Flat(x, y, w, 14, b -> flip.run(), (g, f, hov) -> {
            rr(g, f.getX(), f.getY(), w, 14, 2, hov ? CARD_HOVER : CARD_BG);
            drawCheckbox(g, f.getX() + 4, f.getY() + 2, get.getAsBoolean());
            txt(g, fit(label, w - 24), f.getX() + 18, f.getY() + 3, TEXT);
        }));
    }

    private void toggleRow(int x, int y, int w, String label, BooleanSupplier get, Runnable flip) {
        add(new Flat(x, y, w, 20, b -> flip.run(), (g, f, hov) -> {
            rr(g, f.getX(), f.getY(), w, 20, 3, hov ? CARD_HOVER : CARD_BG);
            txt(g, fit(label, w - 44), f.getX() + 8, f.getY() + 6, TEXT);
            boolean on = get.getAsBoolean();
            rr(g, f.getX() + w - 26, f.getY() + 5, 20, 10, 5, on ? TOGGLE_ON : TOGGLE_OFF);
            rr(g, on ? f.getX() + w - 15 : f.getX() + w - 24, f.getY() + 7, 6, 6, 3, WHITE);
        }));
    }

    private void keyRow(int x, int y, int w, String label, IntSupplier get, IntConsumer set, ItemStack icon) {
        add(new Flat(x, y, w, 20, b -> {
            listenBtn = b;
            listenSetter = set;
        }, (g, f, hov) -> {
            rr(g, f.getX(), f.getY(), w, 20, 3, hov ? CARD_HOVER : CARD_BG);
            int tx = f.getX() + 8;
            if (icon != null) {
                g.drawItem(icon, f.getX() + 3, f.getY() + 2);
                tx = f.getX() + 24;
            }
            boolean listening = listenBtn == f;
            String k = listening ? "..." : Keys.name(get.getAsInt());
            int bw = Math.max(30, tw(k) + 12);
            txt(g, fit(label, w - bw - 12 - (tx - f.getX())), tx, f.getY() + 6, TEXT);
            rr(g, f.getX() + w - bw - 4, f.getY() + 3, bw, 14, 3, listening ? ACCENT : TOGGLE_OFF);
            txt(g, k, f.getX() + w - bw - 4 + (bw - tw(k)) / 2, f.getY() + 6, WHITE);
        }));
    }

    // ───────────────────────── Отрисовка ─────────────────────────

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, DIM);

        // Основная панель
        border(g, mainX, mainY, mainW, mainH, 6, PANEL_BORDER);
        rr(g, mainX + 1, mainY + 1, mainW - 2, 26, 5, HEADER_BG);
        txt(g, "≡ " + selectedCategory.title, mainX + 8, mainY + 8, TEXT);
        txt(g, "Misc", mainX + mainW - 40, mainY + 8, DIM_TEXT);

        // Панель настроек
        if (selectedMod != null) {
            border(g, setX, setY, setW, setH, 6, PANEL_BORDER);
            rr(g, setX + 1, setY + 1, setW - 2, 26, 5, HEADER_BG);
            // Заголовок настройки рисуется в render() виджета, но проще тут
            txt(g, "≡ " + selectedMod.title, setX + 8, setY + 8, TEXT);
        }

        // Панель бота
        border(g, botX, botY, botW, botH, 6, PANEL_BORDER);
        rr(g, botX + 1, botY + 1, botW - 2, 26, 5, HEADER_BG);
        txt(g, "≡ FightBot", botX + 8, botY + 8, TEXT);

        for (ClickableWidget w : widgets) w.render(g, mouseX, mouseY, delta);

        if (listenSetter != null) pollKey();
    }

    // ───────────────────────── Назначение клавиш ─────────────────────────

    private void pollKey() {
        long h = client.getWindow().getHandle();
        for (int k = 32; k <= 348; k++) {
            if (GLFW.glfwGetKey(h, k) != GLFW.GLFW_PRESS) continue;
            if (k == GLFW.GLFW_KEY_ESCAPE) finishListen(null);
            else if (k == GLFW.GLFW_KEY_BACKSPACE || k == GLFW.GLFW_KEY_DELETE) finishListen(Config.NONE);
            else finishListen(k);
            return;
        }
        for (int b = 1; b <= 7; b++) {
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
