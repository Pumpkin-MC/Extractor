package de.snowii.extractor.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.FallenTreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashSet;
import java.util.LinkedHashSet;

@Mixin(FallenTreeFeature.class)
public abstract class FallenTreeFeatureMixin {
    @Redirect(
        method = "placeFallenLog",
        at = @At(value = "NEW", target = "java/util/HashSet")
    )
    private HashSet<BlockPos> snowii$useInsertionOrderedFallenLogSet() {
        return new LinkedHashSet<>();
    }
}
