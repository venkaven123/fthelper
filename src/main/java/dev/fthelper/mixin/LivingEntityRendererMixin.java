package dev.fthelper.mixin;

import dev.fthelper.SeeInvisibles;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    /** Ванильная прозрачность призрака (0x26FFFFFF, около 15%) заменяется на нашу. */
    @ModifyConstant(method = "render", constant = @Constant(intValue = 654311423), require = 0)
    private int fthelper$ghostAlpha(int original) {
        return SeeInvisibles.ghostMode() ? SeeInvisibles.ghostColor() : original;
    }
}
