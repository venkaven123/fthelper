package dev.fthelper;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Свободная камера: отдельная сущность-камера, управление WASD/Space/Shift.
 * Настоящий игрок стоит на месте (клавиши движения обнуляются).
 */
public class Freecam {
    private static boolean active = false;
    private static OtherClientPlayerEntity cam;
    private static float savedYaw, savedPitch;
    private static long lastNanos;

    public static boolean isActive() { return active; }

    public static void toggle(MinecraftClient mc) {
        if (active) disable(mc); else enable(mc);
    }

    private static void enable(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        cam = new OtherClientPlayerEntity(mc.world, mc.player.getGameProfile());
        cam.refreshPositionAndAngles(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                mc.player.getYaw(), mc.player.getPitch());
        savedYaw = mc.player.getYaw();
        savedPitch = mc.player.getPitch();
        mc.setCameraEntity(cam);
        lastNanos = System.nanoTime();
        active = true;
    }

    private static void disable(MinecraftClient mc) {
        active = false;
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
            mc.player.setYaw(savedYaw);
            mc.player.setPitch(savedPitch);
        }
        cam = null;
    }

    public static void forceOff() {
        active = false;
        cam = null;
    }

    private static boolean down(KeyBinding kb) {
        InputUtil.Key k = KeyBindingHelper.getBoundKeyOf(kb);
        if (k.getCategory() != InputUtil.Type.KEYSYM) return false;
        long h = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(h, k.getCode()) == GLFW.GLFW_PRESS;
    }

    /** Обнуляем управление настоящим игроком, пока камера свободна. */
    public static void startTick(MinecraftClient mc) {
        if (!active) return;
        if (mc.player == null) { forceOff(); return; }
        GameOptions o = mc.options;
        o.forwardKey.setPressed(false);
        o.backKey.setPressed(false);
        o.leftKey.setPressed(false);
        o.rightKey.setPressed(false);
        o.jumpKey.setPressed(false);
        o.sneakKey.setPressed(false);
        o.sprintKey.setPressed(false);
    }

    /** Двигаем камеру каждый кадр (плавно). */
    public static void frame() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!active || cam == null || mc.player == null) return;

        long now = System.nanoTime();
        double dt = Math.min((now - lastNanos) / 1e9, 0.1);
        lastNanos = now;

        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);

        Vec3d look = new Vec3d(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
        Vec3d right = new Vec3d(-Math.cos(yr), 0, -Math.sin(yr));

        Vec3d move = Vec3d.ZERO;
        if (mc.currentScreen == null) {
            GameOptions o = mc.options;
            if (down(o.forwardKey)) move = move.add(look);
            if (down(o.backKey)) move = move.subtract(look);
            if (down(o.rightKey)) move = move.add(right);
            if (down(o.leftKey)) move = move.subtract(right);
            if (down(o.jumpKey)) move = move.add(0, 1, 0);
            if (down(o.sneakKey)) move = move.add(0, -1, 0);
        }

        if (move.lengthSquared() > 0) move = move.normalize().multiply(10.0 * Config.get().freecamSpeed * dt);

        cam.refreshPositionAndAngles(cam.getX() + move.x, cam.getY() + move.y, cam.getZ() + move.z, yaw, pitch);
    }
}
