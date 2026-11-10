package de.snowii.extractor.mixin;

import de.snowii.extractor.BiomeCacheReset;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.biome.Climate$RTree")
public abstract class ClimateRTreeMixin {

    @Shadow
    @Final
    private ThreadLocal<?> lastResult;

    @Inject(method = "search", at = @At("HEAD"))
    private void resetLastResultForChunk(CallbackInfoReturnable<Object> cir) {
        if (BiomeCacheReset.consumeNeeded()) {
            this.lastResult.remove();
        }
    }
}
