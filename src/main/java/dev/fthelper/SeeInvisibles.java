package dev.fthelper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

/** Показывает невидимых игроков (с зельем невидимости): снимаем флаг невидимости на клиенте. */
public class SeeInvisibles {
    public static void tick(MinecraftClient mc) {
        if (!Config.get().seeInvisibles || mc.world == null) return;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && p.isInvisible()) p.setInvisible(false);
        }
    }
}
