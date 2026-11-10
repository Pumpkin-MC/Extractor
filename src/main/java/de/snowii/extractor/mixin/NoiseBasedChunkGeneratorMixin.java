package de.snowii.extractor.mixin;

import de.snowii.extractor.extractors.ChunkStageHashStorage;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseBasedChunkGeneratorMixin {

    @Inject(method = "doFill", at = @At("RETURN"))
    private void onDoFill(NoiseChunk noiseChunk, ChunkAccess chunk, CallbackInfo ci) {
        ChunkStageHashStorage.INSTANCE.recordChunk(chunk, "minecraft:noise");
    }

    @Inject(method = "buildSurface", at = @At("RETURN"))
    private void onBuildSurface(ChunkAccess chunk, NoiseChunk noiseChunk, RandomState randomState, BiomeManager biomeManager, Set<Holder<Biome>> set, MaterialRule materialRule, CallbackInfo ci) {
        ChunkStageHashStorage.INSTANCE.recordChunk(chunk, "minecraft:surface");
    }

    @Inject(method = "generateCarvers", at = @At("RETURN"))
    private void onGenerateCarvers(ChunkAccess chunk, Blender blender, NoiseChunk noiseChunk, RandomState randomState, BiomeManager biomeManager, WorldGenRegion worldGenRegion, MaterialRule materialRule, CallbackInfo ci) {
        ChunkStageHashStorage.INSTANCE.recordChunk(chunk, "minecraft:carvers");
    }
}
