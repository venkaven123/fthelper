package dev.fthelper;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/** Опрос клавиш и кнопок мыши напрямую через GLFW (без KeyBinding). */
public class Keys {
    private static final Map<String, Boolean> prev = new HashMap<>();

    /** Физически зажата ли клавиша, назначенная на этот KeyBinding (в настройках Minecraft). */
    public static boolean physicallyDown(KeyBinding kb) {
        InputUtil.Key k = KeyBindingHelper.getBoundKeyOf(kb);
        if (k.getCategory() != InputUtil.Type.KEYSYM) return false;
        long h = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(h, k.getCode()) == GLFW.GLFW_PRESS;
    }

    /**
     * Принудительно синхронизирует состояние клавиш движения с их физическим состоянием (physical=true)
     * или полностью их отпускает (physical=false). Нужно, когда открытый экран мешает Minecraft
     * самому обновлять KeyBinding по вводу с клавиатуры.
     */
    public static void setMoveKeys(GameOptions o, boolean physical) {
        o.forwardKey.setPressed(physical && physicallyDown(o.forwardKey));
        o.backKey.setPressed(physical && physicallyDown(o.backKey));
        o.leftKey.setPressed(physical && physicallyDown(o.leftKey));
        o.rightKey.setPressed(physical && physicallyDown(o.rightKey));
        o.jumpKey.setPressed(physical && physicallyDown(o.jumpKey));
        o.sprintKey.setPressed(physical && physicallyDown(o.sprintKey));
        o.sneakKey.setPressed(physical && physicallyDown(o.sneakKey));
    }

    public static boolean isDown(int code) {
        if (code == Config.NONE) return false;
        long h = MinecraftClient.getInstance().getWindow().getHandle();
        if (code >= Config.MOUSE) return GLFW.glfwGetMouseButton(h, code - Config.MOUSE) == GLFW.GLFW_PRESS;
        return GLFW.glfwGetKey(h, code) == GLFW.GLFW_PRESS;
    }

    /** true один раз в момент нажатия. Вызывать каждый тик для каждого id. */
    public static boolean edge(String id, int code) {
        boolean now = isDown(code);
        boolean was = prev.getOrDefault(id, false);
        prev.put(id, now);
        return now && !was;
    }

    public static String name(int c) {
        if (c == Config.NONE) return "—";
        if (c >= Config.MOUSE) return "Mouse " + (c - Config.MOUSE + 1);
        switch (c) {
            case 32: return "SPACE";
            case 257: return "ENTER";
            case 258: return "TAB";
            case 340: case 344: return "SHIFT";
            case 341: case 345: return "CTRL";
            case 342: case 346: return "ALT";
            case 280: return "CAPS";
            case 262: return "→";
            case 263: return "←";
            case 264: return "↓";
            case 265: return "↑";
            default:
        }
        if (c >= 290 && c <= 314) return "F" + (c - 289);
        String n = GLFW.glfwGetKeyName(c, 0);
        if (n != null) return n.toUpperCase();
        return "KEY" + c;
    }
}
