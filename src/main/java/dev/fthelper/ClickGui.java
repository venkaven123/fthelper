package dev.fthelper;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Click GUI (открывается на J): Binds / ESP / Freecam / HUD / Friends / Invis. */
public class ClickGui extends Screen {
    private static final String[] TABS = {"Binds", "ESP", "Freecam", "HUD", "Friends", "Invis"};
    private static int tab = 0;

    private IntConsumer listenSetter;
    private ButtonWidget listenBtn;
    private Supplier<Text> listenRestore;
    private boolean rebuild = false;

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
    public void tick() {
        super.tick();
        if (rebuild) { rebuild = false; clearAndInit(); }
    }

    @Override
    protected void init() {
        Config c = Config.get();
        int left = width / 2 - 160;
        int y = 50;

        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal((tab == i ? "> " : "") + TABS[i]), b -> {
                tab = idx;
                listenSetter = null;
                clearAndInit();
            }).dimensions(left + i * 64, 20, 62, 20).build());
        }

        switch (tab) {
            case 0 -> {
                for (Config.ItemBind b : c.binds) {
                    Supplier<Text> label = () -> Text.empty()
                            .append(new ItemStack(ItemBinds.item(b.item)).getName())
                            .append(Text.literal(": [" + Keys.name(b.key) + "]"));
                    keyButton(left, y, 320, label, v -> b.key = v);
                    y += 24;
                }
            }
            case 1 -> {
                toggle(left, y, "ESP", () -> c.espEnabled, v -> c.espEnabled = v); y += 24;
                keyButton(left, y, 320, () -> Text.literal("Бинд ESP: [" + Keys.name(c.espKey) + "]"), v -> c.espKey = v); y += 24;
                stepper(left, y, "Расстояние от прицела", () -> c.espRadius, v -> c.espRadius = (int) v, 5, 10, 200, "%.0f"); y += 24;
                stepper(left, y, "Макс. дистанция", () -> c.espMaxDistance, v -> c.espMaxDistance = (int) v, 10, 10, 1000, "%.0f"); y += 24;
                TextFieldWidget color = new TextFieldWidget(textRenderer, left, y, 220, 20, Text.literal("Цвет"));
                color.setMaxLength(7);
                color.setText(c.espColor);
                color.setChangedListener(s -> c.espColor = s);
                addDrawableChild(color);
            }
            case 2 -> {
                toggle(left, y, "Freecam", Freecam::isActive, v -> { if (v != Freecam.isActive()) Freecam.toggle(client); }); y += 24;
                keyButton(left, y, 320, () -> Text.literal("Бинд Freecam: [" + Keys.name(c.freecamKey) + "]"), v -> c.freecamKey = v); y += 24;
                stepper(left, y, "Скорость", () -> c.freecamSpeed, v -> c.freecamSpeed = v, 0.1, 0.1, 10, "%.1f");
            }
            case 3 -> {
                toggle(left, y, "HUD", () -> c.hudEnabled, v -> c.hudEnabled = v); y += 24;
                stepper(left, y, "Размер", () -> c.hudScale, v -> c.hudScale = v, 0.1, 0.5, 2.0, "%.1f"); y += 24;
                stepper(left, y, "Высота над низом", () -> c.hudYOffset, v -> c.hudYOffset = (int) v, 5, 40, 250, "%.0f"); y += 24;
                keyButton(left, y, 320, () -> Text.literal("Клавиша Click GUI: [" + Keys.name(c.guiKey) + "]"), v -> c.guiKey = v);
            }
            case 5 -> {
                toggle(left, y, "See Invisibles", () -> c.seeInvisibles, v -> c.seeInvisibles = v); y += 24;
                keyButton(left, y, 320, () -> Text.literal("Бинд See Invisibles: [" + Keys.name(c.seeInvisKey) + "]"), v -> c.seeInvisKey = v); y += 24;
                stepper(left, y, "Видимость, %", () -> c.invisAlpha, v -> c.invisAlpha = (int) v, 5, 30, 100, "%.0f");
            }
            case 4 -> {
                TextFieldWidget field = new TextFieldWidget(textRenderer, left, y, 220, 20, Text.literal("Ник"));
                field.setMaxLength(16);
                field.setPlaceholder(Text.literal("Ник игрока"));
                addDrawableChild(field);
                addDrawableChild(ButtonWidget.builder(Text.literal("Добавить"), b -> {
                    String n = field.getText().trim();
                    if (!n.isEmpty() && !c.isFriend(n)) {
                        c.friends.add(n);
                        Config.save();
                        clearAndInit();
                    }
                }).dimensions(left + 224, y, 96, 20).build());
                y += 28;
                int shown = 0;
                for (String f : c.friends) {
                    if (shown++ >= 9) break;
                    ButtonWidget name = ButtonWidget.builder(Text.literal(f), b -> {}).dimensions(left, y, 292, 20).build();
                    name.active = false;
                    addDrawableChild(name);
                    addDrawableChild(ButtonWidget.builder(Text.literal("x"), b -> {
                        c.friends.removeIf(s -> s.equalsIgnoreCase(f));
                        Config.save();
                        clearAndInit();
                    }).dimensions(left + 296, y, 24, 20).build());
                    y += 22;
                }
            }
            default -> {}
        }
    }

    private void keyButton(int x, int y, int w, Supplier<Text> label, IntConsumer setter) {
        ButtonWidget btn = ButtonWidget.builder(label.get(), b -> {
            if (listenBtn != null && listenSetter != null && listenRestore != null) {
                listenBtn.setMessage(listenRestore.get());
            }
            listenBtn = b;
            listenSetter = setter;
            listenRestore = label;
            b.setMessage(Text.literal("Нажми клавишу или кнопку мыши... (Backspace — очистить)"));
        }).dimensions(x, y, w, 20).build();
        addDrawableChild(btn);
    }

    private void toggle(int x, int y, String name, BooleanSupplier get, Consumer<Boolean> set) {
        addDrawableChild(ButtonWidget.builder(toggleText(name, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(toggleText(name, get.getAsBoolean()));
        }).dimensions(x, y, 320, 20).build());
    }

    private static Text toggleText(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "ВКЛ" : "ВЫКЛ"));
    }

    private void stepper(int x, int y, String label, DoubleSupplier get, DoubleConsumer set,
                         double step, double min, double max, String fmt) {
        ButtonWidget mid = ButtonWidget.builder(Text.empty(), b -> {}).dimensions(x + 22, y, 276, 20).build();
        mid.active = false;
        Runnable refresh = () -> mid.setMessage(
                Text.literal(label + ": " + String.format(Locale.ROOT, fmt, get.getAsDouble())));
        refresh.run();

        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> {
            set.accept(Math.max(min, Math.round((get.getAsDouble() - step) * 100) / 100.0));
            refresh.run();
        }).dimensions(x, y, 20, 20).build());
        addDrawableChild(mid);
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
            set.accept(Math.min(max, Math.round((get.getAsDouble() + step) * 100) / 100.0));
            refresh.run();
        }).dimensions(x + 300, y, 20, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, "FT Helper — Click GUI", width / 2, 6, 0xFFFFFFFF);

        if (tab == 1) {
            int left = width / 2 - 160;
            ctx.fill(left + 224, 146, left + 320, 166, Config.get().espColorArgb());
            ctx.drawTextWithShadow(textRenderer, "Цвет ESP (HEX, например #FF3030). Друзья всегда зелёные.",
                    left, 172, 0xFFAAAAAA);
        }

        if (listenSetter != null) pollKey();
    }

    private void pollKey() {
        long h = client.getWindow().getHandle();
        for (int k = 32; k <= 348; k++) {
            if (GLFW.glfwGetKey(h, k) != GLFW.GLFW_PRESS) continue;
            if (k == GLFW.GLFW_KEY_ESCAPE) finishListen(null);
            else if (k == GLFW.GLFW_KEY_BACKSPACE || k == GLFW.GLFW_KEY_DELETE) finishListen(Config.NONE);
            else finishListen(k);
            return;
        }
        for (int b = 1; b <= 7; b++) { // ЛКМ (0) пропускаем — им нажимали на кнопку
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
        listenRestore = null;
        Config.save();
        rebuild = true;
    }
}
