package dev.fthelper;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Бинд -> предмет меняется со слотом в руке (без открытия инвентаря) ->
 * через 200 мс один ПКМ -> (если включено returnItem) ещё через 200 мс
 * предмет возвращается на прежнее место.
 * Пока идёт вся последовательность, движение игрока останавливается (если freezeWhileUsing),
 * а сразу после неё продолжается, если клавиши всё ещё зажаты.
 */
public class ItemBinds {
    private static final int DELAY_TICKS = 4; // 200 мс между действиями

    // 0 ожидание, 1 ждём ПКМ, 2 ждём возврат
    private static int state = 0;
    private static int timer;
    private static int heldSlot;
    private static int returnSlot = -1; // слот окна инвентаря, откуда взяли предмет
    private static Item item = Items.AIR;

    private static boolean frozen = false;

    public static String status = "";
    public static long statusTime = 0;

    public static Item item(String id) {
        Identifier i = Identifier.tryParse(id);
        return i == null ? Items.AIR : Registries.ITEM.get(i);
    }

    public static int count(MinecraftClient mc, Item it) {
        if (mc.player == null) return 0;
        PlayerInventory inv = mc.player.getInventory();
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(it)) n += s.getCount();
        }
        return n;
    }

    public static void reset() {
        state = 0;
        returnSlot = -1;
        frozen = false;
    }

    private static boolean down(KeyBinding kb) {
        InputUtil.Key k = KeyBindingHelper.getBoundKeyOf(kb);
        if (k.getCategory() != InputUtil.Type.KEYSYM) return false;
        long h = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(h, k.getCode()) == GLFW.GLFW_PRESS;
    }

    private static void setMove(GameOptions o, boolean physical) {
        o.forwardKey.setPressed(physical && down(o.forwardKey));
        o.backKey.setPressed(physical && down(o.backKey));
        o.leftKey.setPressed(physical && down(o.leftKey));
        o.rightKey.setPressed(physical && down(o.rightKey));
        o.jumpKey.setPressed(physical && down(o.jumpKey));
        o.sprintKey.setPressed(physical && down(o.sprintKey));
    }

    /**
     * Вызывается в начале тика (до чтения управления игроком): пока идёт использование,
     * обнуляем клавиши движения; когда закончили - возвращаем те, что всё ещё зажаты.
     */
    public static void startTick(MinecraftClient mc) {
        if (mc.player == null) {
            frozen = false;
            return;
        }
        boolean want = state != 0 && Config.get().freezeWhileUsing;
        if (want) {
            setMove(mc.options, false);
            frozen = true;
        } else if (frozen) {
            frozen = false;
            if (mc.currentScreen == null) setMove(mc.options, true);
        }
    }

    private static void setStatus(String s) {
        status = s;
        statusTime = System.currentTimeMillis();
    }

    public static void tick(MinecraftClient mc) {
        Config cfg = Config.get();

        for (Config.ItemBind b : cfg.binds) {
            boolean pressed = Keys.edge("bind:" + b.item, b.key);
            if (pressed && state == 0 && mc.currentScreen == null) start(mc, b);
        }

        if (state == 0) return;
        if (mc.currentScreen != null) return; // пока открыто любое окно, ждём

        if (state == 1) {
            if (--timer > 0) return;

            if (mc.player.getInventory().getSelectedSlot() == heldSlot && mc.interactionManager != null) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                setStatus("Выполнено");
            } else {
                setStatus("Отменено"); // сменили слот хотбара: предмет не используем
            }

            if (cfg.returnItem && returnSlot != -1) {
                state = 2;
                timer = DELAY_TICKS;
            } else {
                state = 0;
                returnSlot = -1;
            }
        } else if (state == 2) {
            if (--timer <= 0) giveBack(mc);
        }
    }

    private static void start(MinecraftClient mc, Config.ItemBind b) {
        Item it = item(b.item);
        if (it == Items.AIR) return;
        if (count(mc, it) <= 0) {
            setStatus("Нет предмета");
            return;
        }
        item = it;
        returnSlot = -1;
        heldSlot = mc.player.getInventory().getSelectedSlot();

        ScreenHandler h = mc.player.playerScreenHandler;
        int found = -1;
        boolean alreadyHeld = false;

        // слоты окна инвентаря: 9..35 основной, 36..44 хотбар
        for (int i = 9; i <= 44; i++) {
            if (!h.slots.get(i).getStack().isOf(item)) continue;
            if (i == 36 + heldSlot) { alreadyHeld = true; break; }
            if (found == -1) found = i;
        }

        if (!alreadyHeld) {
            if (found == -1) {
                setStatus("Нет предмета");
                return;
            }
            // SWAP: меняем найденный слот со слотом хотбара (button = номер слота хотбара)
            mc.interactionManager.clickSlot(h.syncId, found, heldSlot, SlotActionType.SWAP, mc.player);
            returnSlot = found;
        }

        state = 1;
        timer = DELAY_TICKS;
    }

    /** Повторный SWAP тех же слотов возвращает всё как было. */
    private static void giveBack(MinecraftClient mc) {
        ScreenHandler h = mc.player.playerScreenHandler;
        ItemStack inHand = h.slots.get(36 + heldSlot).getStack();

        // защита: в слоте должен лежать наш предмет (или пусто, если стак израсходован)
        if (returnSlot != -1 && (inHand.isOf(item) || inHand.isEmpty())) {
            mc.interactionManager.clickSlot(h.syncId, returnSlot, heldSlot, SlotActionType.SWAP, mc.player);
            setStatus("Возвращено");
        } else {
            setStatus("Возврат отменён");
        }

        state = 0;
        returnSlot = -1;
    }
}
