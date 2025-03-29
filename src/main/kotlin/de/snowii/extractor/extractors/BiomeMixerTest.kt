package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import de.snowii.extractor.Extractor
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.world.biome.source.BiomeAccess
import net.minecraft.world.biome.source.SeedMixer

class BiomeMixerTest : Extractor.Extractor {
    override fun fileName(): String {
        return "biome_mixer.json"
    }

    companion object {
        private fun method_38106(l: Long, i: Int, j: Int, k: Int, d: Double, e: Double, f: Double): Double {
            var m = SeedMixer.mixSeed(l, i.toLong())
            m = SeedMixer.mixSeed(m, j.toLong())
            m = SeedMixer.mixSeed(m, k.toLong())
            m = SeedMixer.mixSeed(m, i.toLong())
            m = SeedMixer.mixSeed(m, j.toLong())
            m = SeedMixer.mixSeed(m, k.toLong())
            val g = method_38108(m)
            m = SeedMixer.mixSeed(m, l)
            val h = method_38108(m)
            m = SeedMixer.mixSeed(m, l)
            val n = method_38108(m)
            return MathHelper.square(f + n) + MathHelper.square(e + h) + MathHelper.square(d + g)
        }

        private fun method_38108(l: Long): Double {
            val d = Math.floorMod(l shr 24, 1024) / 1024.0
            return (d - 0.5) * 0.9
        }

        fun getBiome(seed: Long, pos: BlockPos): BlockPos {
            val i = pos.x - 2
            val j = pos.y - 2
            val k = pos.z - 2
            val l = i shr 2
            val m = j shr 2
            val n = k shr 2
            val d = (i and 3) / 4.0
            val e = (j and 3) / 4.0
            val f = (k and 3) / 4.0
            var o = 0
            var g = Double.POSITIVE_INFINITY

            for (p in 0..7) {
                val bl = (p and 4) == 0
                val bl2 = (p and 2) == 0
                val bl3 = (p and 1) == 0
                val q = if (bl) l else l + 1
                val r = if (bl2) m else m + 1
                val s = if (bl3) n else n + 1
                val h = if (bl) d else d - 1.0
                val t = if (bl2) e else e - 1.0
                val u = if (bl3) f else f - 1.0
                val v = method_38106(seed, q, r, s, h, t, u)
                if (g > v) {
                    o = p
                    g = v
                }
            }

            val px = if ((o and 4) == 0) l else l + 1
            val w = if ((o and 2) == 0) m else m + 1
            val x = if ((o and 1) == 0) n else n + 1

            return BlockPos(px, w, x)
        }
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val valuesJson = JsonArray()
        val startX = (-2).shl(2)
        val startZ = (-2).shl(2)

        println(BiomeAccess.hashSeed(-777))

        for (x in 0..15) {
            for (y in -64..256) {
                for (z in 0..15) {
                    val xAbs = startX + x
                    val zAbs = startZ + z
                    val result = getBiome(BiomeAccess.hashSeed(-777), BlockPos(xAbs, y, zAbs))
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