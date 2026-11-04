package dev.fthelper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

/**
 * Позволяет свободно ходить/бегать и перетаскивать предметы между ячейками,
 * пока открыт свой инвентарь, сундук, верстак, печь и т. п.
 * Обычно Minecraft перестаёт обновлять клавиши движения, пока открыт любой экран:
 * здесь мы сами опрашиваем физическую клавиатуру и подставляем состояние в игру.
 */
public class InventoryMove {
    private static boolean applied = false;

    public static void startTick(MinecraftClient mc) {
        if (mc.player == null) {
            applied = false;
            return;
        }

        boolean want = Config.get().invMove && mc.currentScreen instanceof HandledScreen<?>;

        if (want) {
            Keys.setMoveKeys(mc.options, true);
            applied = true;
        } else if (applied) {
            applied = false;
            // возвращаем клавиши в нормальный режим: Minecraft сам продолжит их обновлять
            Keys.setMoveKeys(mc.options, mc.currentScreen == null);
        }
    }
}
