package de.snowii.extractor.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.VegetationPatchFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashSet;
import java.util.LinkedHashSet;

@Mixin(VegetationPatchFeature.class)
public abstract class VegetationPatchFeatureMixin {
    @Redirect(
        method = "placeGroundPatch",
        at = @At(value = "NEW", target = "java/util/HashSet")
    )
    private HashSet<BlockPos> snowii$useInsertionOrderedSurfaceSet() {
        return new LinkedHashSet<>();
    }
}
