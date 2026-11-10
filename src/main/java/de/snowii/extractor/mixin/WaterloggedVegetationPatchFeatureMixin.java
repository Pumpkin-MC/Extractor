package de.snowii.extractor.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.WaterloggedVegetationPatchFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashSet;
import java.util.LinkedHashSet;

@Mixin(WaterloggedVegetationPatchFeature.class)
public abstract class WaterloggedVegetationPatchFeatureMixin {
    @Redirect(
        method = "placeGroundPatch",
        at = @At(value = "NEW", target = "java/util/HashSet")
    )
    private HashSet<BlockPos> snowii$useInsertionOrderedWaterSurfaceSet() {
        return new LinkedHashSet<>();
    }
}
