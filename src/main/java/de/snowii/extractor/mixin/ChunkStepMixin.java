package de.snowii.extractor.mixin;

import de.snowii.extractor.extractors.ChunkStageHashStorage;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkStep.class)
public class ChunkStepMixin {

    @Shadow
    @Final
    private ChunkStatus targetStatus;

    @Inject(method = "completeChunkGeneration", at = @At("RETURN"))
    private void onCompleteChunkGeneration(ChunkAccess chunk, ProfiledDuration duration, CallbackInfoReturnable<ChunkAccess> cir) {
        String dimension = ChunkStageHashStorage.INSTANCE.getCurrentDimension();
        if (!ChunkStageHashStorage.INSTANCE.isTracking(chunk.getPos(), dimension)) {
            return;
        }

        if (this.targetStatus == ChunkStatus.FEATURES) {
            ChunkStageHashStorage.INSTANCE.markReady(chunk.getPos(), dimension);
        }
    }
}
