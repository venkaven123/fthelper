package dev.fthelper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3x2fStack;

/** Горизонтальный ряд ячеек над хотбаром: клавиша, иконка, количество. */
public class Hud {
    public static void render(DrawContext ctx) {
        Config cfg = Config.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!cfg.hudEnabled || mc.player == null || mc.options.hudHidden) return;
        int n = cfg.binds.size();
        if (n == 0) return;

        TextRenderer tr = mc.textRenderer;
        int cw = 36, ch = 30, gap = 2;
        int totalW = n * (cw + gap) - gap;

        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(ctx.getScaledWindowWidth() / 2f, ctx.getScaledWindowHeight() - cfg.hudYOffset);
        m.scale((float) cfg.hudScale, (float) cfg.hudScale);

        int x0 = -totalW / 2;
        int y0 = -ch;

        for (int i = 0; i < n; i++) {
            Config.ItemBind b = cfg.binds.get(i);
            Item item = ItemBinds.item(b.item);
            int count = ItemBinds.count(mc, item);
            int x = x0 + i * (cw + gap);

            ctx.fill(x, y0, x + cw, y0 + ch, count > 0 ? 0x99000000 : 0x99303030);

            ctx.drawCenteredTextWithShadow(tr, Keys.name(b.key), x + cw / 2, y0 + 2,
                    count > 0 ? 0xFFDDDDDD : 0xFF777777);

            ctx.drawItem(new ItemStack(item), x + 10, y0 + 11);
            if (count <= 0) ctx.fill(x + 10, y0 + 11, x + 26, y0 + 27, 0xAA303030);

            String cs = String.valueOf(count);
            ctx.drawTextWithShadow(tr, cs, x + cw - 2 - tr.getWidth(cs), y0 + 20,
                    count > 0 ? 0xFFFFFFFF : 0xFFFF5555);
        }

        if (!ItemBinds.status.isEmpty() && System.currentTimeMillis() - ItemBinds.statusTime < 2000) {
            ctx.drawCenteredTextWithShadow(tr, ItemBinds.status, 0, y0 - 11, 0xFFFFFF55);
        }

        m.popMatrix();
    }
}
