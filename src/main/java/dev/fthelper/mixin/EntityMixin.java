package dev.fthelper.mixin;

import dev.fthelper.SeeInvisibles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    /** В призрачном режиме игроки "не невидимы" для нас: игра рисует их полупрозрачными. */
    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true, require = 0)
    private void fthelper$isInvisibleTo(PlayerEntity viewer, CallbackInfoReturnable<Boolean> cir) {
        if (SeeInvisibles.ghostMode() && (Object) this instanceof PlayerEntity && (Object) this != viewer) {
            cir.setReturnValue(false);
        }
    }
}
