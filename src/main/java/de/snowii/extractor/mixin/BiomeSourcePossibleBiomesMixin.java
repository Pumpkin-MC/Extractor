package de.snowii.extractor.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mixin(BiomeSource.class)
public abstract class BiomeSourcePossibleBiomesMixin {
    @Shadow
    protected abstract Stream<Holder<Biome>> collectPossibleBiomes();

    @Inject(method = "possibleBiomes", at = @At("HEAD"), cancellable = true)
    private void snowii$possibleBiomesInInsertionOrder(CallbackInfoReturnable<Set<Holder<Biome>>> cir) {
        cir.setReturnValue(this.collectPossibleBiomes()
            .distinct()
            .collect(Collectors.toCollection(LinkedHashSet::new)));
    }
}
