package dev.fthelper;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

public class FTHelper implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Config.load();

        ClientTickEvents.START_CLIENT_TICK.register(Freecam::startTick);
        ClientTickEvents.END_CLIENT_TICK.register(FTHelper::tick);

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            Freecam.frame();
            Hud.render(ctx);
            Esp.render(ctx);
        });
    }

    private static void tick(MinecraftClient mc) {
        Config cfg = Config.get();

        if (mc.player == null || mc.world == null) {
            ItemBinds.reset();
            Freecam.forceOff();
            return;
        }

        // Edge считаем всегда, чтобы не было ложных срабатываний после закрытия меню
        boolean gui = Keys.edge("gui", cfg.guiKey);
        boolean esp = Keys.edge("esp", cfg.espKey);
        boolean fc = Keys.edge("freecam", cfg.freecamKey);

        ItemBinds.tick(mc);

        if (mc.currentScreen == null) {
            if (gui) mc.setScreen(new ClickGui());
            if (esp) { cfg.espEnabled = !cfg.espEnabled; Config.save(); }
            if (fc) Freecam.toggle(mc);
        }
    }
}
