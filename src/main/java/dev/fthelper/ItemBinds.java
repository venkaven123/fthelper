package dev.fthelper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

/**
 * Бинд -> открыть инвентарь -> через 4 тика (200 мс) предмет в слот в руке ->
 * закрыть инвентарь -> один ПКМ.
 */
public class ItemBinds {
    private static final int DELAY_TICKS = 4; // 200 мс

    private static int state = 0; // 0 ожидание, 1 ждём перенос, 2 ждём ПКМ
    private static int timer;
    private static int heldSlot;
    private static Item item = Items.AIR;

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

        if (state == 1) {
            if (!(mc.currentScreen instanceof InventoryScreen)
                    || mc.player.getInventory().getSelectedSlot() != heldSlot) {
                state = 0;
                setStatus("Отменено");
                return;
            }
            if (--timer <= 0) transfer(mc);
        } else if (state == 2) {
            if (--timer <= 0) {
                if (mc.currentScreen == null && mc.interactionManager != null) {
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    setStatus("Выполнено");
                }
                state = 0;
            }
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
        heldSlot = mc.player.getInventory().getSelectedSlot();
        mc.setScreen(new InventoryScreen(mc.player));
        timer = DELAY_TICKS;
        state = 1;
    }

    private static void transfer(MinecraftClient mc) {
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
                mc.player.closeHandledScreen();
                state = 0;
                setStatus("Нет предмета");
                return;
            }
            // SWAP: меняем найденный слот со слотом хотбара (button = номер слота хотбара)
            mc.interactionManager.clickSlot(h.syncId, found, heldSlot, SlotActionType.SWAP, mc.player);
        }

        mc.player.closeHandledScreen();
        state = 2;
        timer = 1;
    }
}
