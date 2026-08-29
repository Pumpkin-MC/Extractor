package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import de.snowii.extractor.Extractor
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.util.LinearCongruentialGenerator
import net.minecraft.util.Mth
import net.minecraft.world.level.biome.BiomeManager

class BiomeMixerTest : Extractor.Extractor {
    override fun fileName(): String {
        return "biome_mixer.json"
    }

    override fun isTest(): Boolean = true

    companion object {
        private fun getFiddledDistance(
            seed: Long,
            xRandom: Int,
            yRandom: Int,
            zRandom: Int,
            distanceX: Double,
            distanceY: Double,
            distanceZ: Double
        ): Double {
            var rval = LinearCongruentialGenerator.next(seed, xRandom.toLong())
            rval = LinearCongruentialGenerator.next(rval, yRandom.toLong())
            rval = LinearCongruentialGenerator.next(rval, zRandom.toLong())
            rval = LinearCongruentialGenerator.next(rval, xRandom.toLong())
            rval = LinearCongruentialGenerator.next(rval, yRandom.toLong())
            rval = LinearCongruentialGenerator.next(rval, zRandom.toLong())
            val fiddleX =  getFiddle(rval)
            rval = LinearCongruentialGenerator.next(rval, seed)
            val fiddleY =  getFiddle(rval)
            rval = LinearCongruentialGenerator.next(rval, seed)
            val fiddleZ = getFiddle(rval)
            return Mth.square(distanceZ + fiddleZ) + Mth.square(distanceY + fiddleY) + Mth.square(distanceX + fiddleX)
        }

        private fun getFiddle(rval: Long): Double {
            val uniform = Math.floorMod(rval shr 24, 1024) / 1024.0
            return (uniform - 0.5) * 0.9
        }

        // From net.minecraft.world.level.biome/BiomeManager
        fun getBiome(seed: Long, pos: BlockPos): BlockPos {
            val absX = pos.getX() - 2
            val absY = pos.getY() - 2
            val absZ = pos.getZ() - 2
            val parentX = absX shr 2
            val parentY = absY shr 2
            val parentZ = absZ shr 2
            val fractX = (absX and 3) / 4.0
            val fractY = (absY and 3) / 4.0
            val fractZ = (absZ and 3) / 4.0
            var minI = 0
            var minFiddledDistance = Double.POSITIVE_INFINITY

            for (i in 0..7) {
                val xEven = (i and 4) == 0
                val yEven = (i and 2) == 0
                val zEven = (i and 1) == 0
                val cornerX = if (xEven) parentX else parentX + 1
                val cornerY = if (yEven) parentY else parentY + 1
                val cornerZ = if (zEven) parentZ else parentZ + 1
                val distanceX = if (xEven) fractX else fractX - 1.0
                val distanceY = if (yEven) fractY else fractY - 1.0
                val distanceZ = if (zEven) fractZ else fractZ - 1.0
                val next = getFiddledDistance(
                    seed,
                    cornerX,
                    cornerY,
                    cornerZ,
                    distanceX,
                    distanceY,
                    distanceZ
                )
                if (minFiddledDistance > next) {
                    minI = i
                    minFiddledDistance = next
                }
            }

            val biomeX = if ((minI and 4) == 0) parentX else parentX + 1
            val biomeY = if ((minI and 2) == 0) parentY else parentY + 1
            val biomeZ = if ((minI and 1) == 0) parentZ else parentZ + 1

            return BlockPos(biomeX, biomeY, biomeZ)
        }
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val valuesJson = JsonArray()
        val startX = (-2).shl(2)
        val startZ = (-2).shl(2)

        println(BiomeManager.obfuscateSeed(-777))

        for (x in 0..15) {
            for (y in -64..256) {
                for (z in 0..15) {
                    val xAbs = startX + x
                    val zAbs = startZ + z
                    val result = getBiome(BiomeManager.obfuscateSeed(-777), BlockPos(xAbs, y, zAbs))
                    val point = JsonArray()
                    point.add(xAbs)
                    point.add(y)
                    point.add(zAbs)
                    point.add(result.x)
                    point.add(result.y)
                    point.add(result.z)

                    valuesJson.add(point)
                }
            }
        }

        return valuesJson
    }
}