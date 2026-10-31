package dev.fthelper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * Показывает невидимых игроков.
 * 100% - снимаем флаг невидимости на клиенте (полностью видны).
 * 30..95% - "призрачный" режим: миксины делают игрока полупрозрачным с заданной яркостью.
 */
public class SeeInvisibles {
    private static final Set<Integer> cleared = new HashSet<>();

    /** Включён полупрозрачный режим (используется миксинами). */
    public static boolean ghostMode() {
        Config c = Config.get();
        return c.seeInvisibles && c.invisAlpha < 100;
    }

    /** Цвет-множитель с нужной прозрачностью (ARGB). */
    public static int ghostColor() {
        int a = Math.round(255f * Config.get().invisAlpha / 100f);
        return (a << 24) | 0xFFFFFF;
    }

    public static void tick(MinecraftClient mc) {
        if (mc.world == null) {
            cleared.clear();
            return;
        }
        Config c = Config.get();
        boolean clearMode = c.seeInvisibles && c.invisAlpha >= 100;

        if (clearMode) {
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p != mc.player && p.isInvisible()) {
                    p.setInvisible(false);
                    cleared.add(p.getId());
                }
            }
        } else if (!cleared.isEmpty()) {
            // вернуть невидимость тем, кого мы раскрывали (если эффект ещё действует)
            for (int id : cleared) {
                Entity e = mc.world.getEntityById(id);
                if (e instanceof LivingEntity le && le.hasStatusEffect(StatusEffects.INVISIBILITY)) {
                    e.setInvisible(true);
                }
            }
            cleared.clear();
        }
    }
}
