package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.minecraft.network.VarInt
import net.minecraft.network.codec.StreamCodec
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.util.StringRepresentable
import net.minecraft.world.entity.animal.axolotl.Axolotl
import net.minecraft.world.entity.animal.cow.MushroomCow
import net.minecraft.world.entity.animal.equine.Llama
import net.minecraft.world.entity.animal.equine.Markings
import net.minecraft.world.entity.animal.equine.Variant
import net.minecraft.world.entity.animal.fish.Salmon
import net.minecraft.world.entity.animal.fish.TropicalFish
import net.minecraft.world.entity.animal.fox.Fox
import net.minecraft.world.entity.animal.panda.Panda
import net.minecraft.world.entity.animal.parrot.Parrot
import net.minecraft.world.entity.animal.rabbit.Rabbit

class EntityVariants : Extractor.Extractor {
    override fun fileName(): String {
        return "entity_variants.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val json = JsonObject()
        json.add("axolotl_variant", extractCodec(Axolotl.Variant.entries, Axolotl.Variant.STREAM_CODEC, Axolotl.Variant.DEFAULT) { variant, entry ->
            entry.addProperty("common", variant.common)
        })
        json.add("fox_variant", extractCodec(Fox.Variant.entries, Fox.Variant.STREAM_CODEC, Fox.Variant.DEFAULT))
        json.add("horse_markings", extractEnum(Markings.entries, Markings::getId, Markings::byId, { it.name.lowercase() }))
        json.add("horse_variant", extractCodec(Variant.entries, Variant.STREAM_CODEC))
        json.add("llama_variant", extractCodec(Llama.Variant.entries, Llama.Variant.STREAM_CODEC, Llama.Variant.DEFAULT))
        json.add("mooshroom_variant", extractCodec(MushroomCow.Variant.entries, MushroomCow.Variant.STREAM_CODEC, MushroomCow.Variant.DEFAULT) { variant, entry ->
            entry.addProperty("mushroom", BuiltInRegistries.BLOCK.getKey(variant.blockState.block).toString())
        })
        json.add("panda_gene", extractEnum(Panda.Gene.entries, Panda.Gene::getId, Panda.Gene::byId, { it.serializedName }, null) { gene, entry ->
            entry.addProperty("recessive", gene.isRecessive)
        })
        json.add("parrot_variant", extractCodec(Parrot.Variant.entries, Parrot.Variant.STREAM_CODEC, Parrot.Variant.DEFAULT))
        json.add("rabbit_variant", extractCodec(Rabbit.Variant.entries, Rabbit.Variant.STREAM_CODEC, Rabbit.Variant.DEFAULT))
        json.add("salmon_size", extractCodec(Salmon.Variant.entries, Salmon.Variant.STREAM_CODEC, Salmon.Variant.DEFAULT) { variant, entry ->
            entry.addProperty("bounding_box_scale", variant.boundingBoxScale)
        })
        val tropicalFish = extractCodec(
            TropicalFish.Pattern.entries,
            TropicalFish.Pattern.STREAM_CODEC,
            TropicalFish.DEFAULT_VARIANT.pattern()
        )
        val commonVariants = JsonArray()
        for (variant in TropicalFish.COMMON_VARIANTS) {
            val entry = JsonObject()
            entry.addProperty("pattern", variant.pattern().serializedName)
            entry.addProperty("base_color", variant.baseColor().serializedName)
            entry.addProperty("pattern_color", variant.patternColor().serializedName)
            commonVariants.add(entry)
        }
        tropicalFish.add("common_variants", commonVariants)
        json.add("tropical_fish_pattern", tropicalFish)
        return json
    }

    private fun <T : StringRepresentable> extractCodec(
        values: List<T>,
        codec: StreamCodec<ByteBuf, T>,
        default: T? = null,
        extra: (T, JsonObject) -> Unit = { _, _ -> }
    ): JsonObject {
        return extractEnum(values, { encode(codec, it) }, { decode(codec, it) }, { it.serializedName }, default, extra)
    }

    private fun <T : Any> extractEnum(
        values: List<T>,
        idOf: (T) -> Int,
        byId: (Int) -> T,
        nameOf: (T) -> String,
        default: T? = null,
        extra: (T, JsonObject) -> Unit = { _, _ -> }
    ): JsonObject {
        val sorted = values.associateBy(idOf).toSortedMap()
        val entries = JsonArray()
        for ((id, value) in sorted) {
            val entry = JsonObject()
            entry.addProperty("id", id)
            entry.addProperty("name", nameOf(value))
            extra(value, entry)
            entries.add(entry)
        }

        // ByIdMap keeps its out of bounds strategy private, so probe just past both ends.
        val first = sorted.getValue(sorted.firstKey())
        val last = sorted.getValue(sorted.lastKey())
        val below = byId(sorted.firstKey() - 1)
        val above = byId(sorted.lastKey() + 1)
        val json = JsonObject()
        json.add("entries", entries)
        default?.let { json.addProperty("default", nameOf(it)) }
        when {
            below == above -> {
                json.addProperty("out_of_bounds", "fallback")
                json.addProperty("fallback", nameOf(below))
            }
            below == first && above == last -> json.addProperty("out_of_bounds", "clamp")
            below == last && above == first -> json.addProperty("out_of_bounds", "wrap")
            else -> throw IllegalStateException("Unknown out of bounds strategy for ${first.javaClass.name}")
        }
        return json
    }

    private fun <T : Any> encode(codec: StreamCodec<ByteBuf, T>, value: T): Int {
        val buf = Unpooled.buffer()
        codec.encode(buf, value)
        return VarInt.read(buf)
    }

    private fun <T : Any> decode(codec: StreamCodec<ByteBuf, T>, id: Int): T {
        val buf = Unpooled.buffer()
        VarInt.write(buf, id)
        return codec.decode(buf)
    }
}
