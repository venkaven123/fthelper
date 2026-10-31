package dev.fthelper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;

/** Стрелки вокруг прицела в сторону игроков. Показывают только расстояние. */
public class Esp {
    private static final int FRIEND_COLOR = 0xFF55FF55;

    public static void render(DrawContext ctx) {
        Config cfg = Config.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!cfg.espEnabled || mc.player == null || mc.world == null || mc.options.hudHidden) return;

        int cx = ctx.getScaledWindowWidth() / 2;
        int cy = ctx.getScaledWindowHeight() / 2;
        float yaw = mc.player.getYaw();
        Matrix3x2fStack m = ctx.getMatrices();

        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            double dist = mc.player.distanceTo(p);
            if (dist > cfg.espMaxDistance) continue;

            double dx = p.getX() - mc.player.getX();
            double dz = p.getZ() - mc.player.getZ();
            double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
            double rel = Math.toRadians(MathHelper.wrapDegrees(targetYaw - yaw));

            float ax = (float) Math.sin(rel);
            float ay = (float) -Math.cos(rel);
            float px = cx + ax * cfg.espRadius;
            float py = cy + ay * cfg.espRadius;

            int color = cfg.isFriend(p.getName().getString()) ? FRIEND_COLOR : cfg.espColorArgb();

            m.pushMatrix();
            m.translate(px, py);
            m.rotate((float) rel);
            for (int i = 0; i < 7; i++) {
                int half = (i + 1) / 2;
                ctx.fill(-half, i - 6, half + 1, i - 5, color);
            }
            m.popMatrix();

            String d = (int) dist + "m";
            int tw = mc.textRenderer.getWidth(d);
            ctx.drawTextWithShadow(mc.textRenderer, d,
                    (int) (px + ax * 14 - tw / 2f), (int) (py + ay * 14 - 4), color);
        }
    }
}
