package de.snowii.extractor.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashSet;
import java.util.LinkedHashSet;

@Mixin(TreeFeature.class)
public abstract class TreeFeatureMixin {
    @Redirect(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lcom/google/common/collect/Sets;newHashSet()Ljava/util/HashSet;",
            ordinal = 0
        )
    )
    private HashSet<BlockPos> snowii$useInsertionOrderedRootSet() {
        return new LinkedHashSet<>();
    }

    @Redirect(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lcom/google/common/collect/Sets;newHashSet()Ljava/util/HashSet;",
            ordinal = 1
        )
    )
    private HashSet<BlockPos> snowii$useInsertionOrderedTrunkSet() {
        return new LinkedHashSet<>();
    }

    @Redirect(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lcom/google/common/collect/Sets;newHashSet()Ljava/util/HashSet;",
            ordinal = 2
        )
    )
    private HashSet<BlockPos> snowii$useInsertionOrderedFoliageSet() {
        return new LinkedHashSet<>();
    }

    @Redirect(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lcom/google/common/collect/Sets;newHashSet()Ljava/util/HashSet;",
            ordinal = 3
        )
    )
    private HashSet<BlockPos> snowii$useInsertionOrderedDecorationSet() {
        return new LinkedHashSet<>();
    }

    @Redirect(
        method = "updateLeaves",
        at = @At(
            value = "INVOKE",
            target = "Lcom/google/common/collect/Sets;newHashSet()Ljava/util/HashSet;"
        )
    )
    private static HashSet<BlockPos> snowii$useInsertionOrderedLeafFrontierSet() {
        return new LinkedHashSet<>();
    }

    @org.spongepowered.asm.mixin.injection.Inject(
        method = "setBlockKnownShape",
        at = @At("HEAD")
    )
    private static void snowii$debugSetBlock(net.minecraft.world.level.LevelWriter level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (pos.getX() == -195 && pos.getZ() == -107) {
            System.out.println("VANILLA TreeFeature setBlockKnownShape at " + pos + " -> " + state);
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(
        method = "place",
        at = @At("HEAD")
    )
    private void snowii$debugPlace(net.minecraft.world.level.WorldGenLevel level, net.minecraft.world.level.chunk.ChunkGenerator chunkGenerator, net.minecraft.util.RandomSource random, BlockPos origin, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        System.out.println("VANILLA TreeFeature place at " + origin);
    }
}
