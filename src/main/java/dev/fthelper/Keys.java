package dev.fthelper;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/** Опрос клавиш и кнопок мыши напрямую через GLFW (без KeyBinding). */
public class Keys {
    private static final Map<String, Boolean> prev = new HashMap<>();

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
