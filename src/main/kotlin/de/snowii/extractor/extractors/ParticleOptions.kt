package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.EyeblossomBlock
import net.minecraft.world.level.block.entity.CreakingHeartBlockEntity
import net.minecraft.world.level.gameevent.PositionSource
import net.minecraft.world.phys.Vec3
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue

class ParticleOptions : Extractor.Extractor {
    override fun fileName(): String {
        return "particle_options.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val particlesJson = JsonArray()
        for (particle in BuiltInRegistries.PARTICLE_TYPE) {
            val particleJson = JsonObject()
            val name = BuiltInRegistries.PARTICLE_TYPE.getKey(particle)!!.path
            particleJson.addProperty("name", name)

            val fieldNames = particle.codec().keys(JsonOps.INSTANCE).map { it.asString }.distinct().toList()
            val fieldTypes = wireTypes(particle.streamCodec())
            if (fieldNames.size != fieldTypes.size) {
                error("Particle $name has fields $fieldNames but network types $fieldTypes")
            }
            val fieldsJson = JsonArray()
            for ((fieldName, fieldType) in fieldNames.zip(fieldTypes)) {
                val fieldJson = JsonObject()
                fieldJson.addProperty("name", fieldName)
                fieldJson.addProperty("type", fieldType)
                fieldsJson.add(fieldJson)
            }
            particleJson.add("fields", fieldsJson)

            if (particle === ParticleTypes.TRAIL) {
                particleJson.add("block_colors", trailColors())
            }
            particlesJson.add(particleJson)
        }

        return particlesJson
    }

    private fun trailColors(): JsonObject {
        val colors = mutableMapOf<Block, MutableSet<Int>>()
        for (type in EyeblossomBlock.Type.entries) {
            colors.getOrPut(type.block()) { mutableSetOf() }.add(type.particleColor)
        }
        colors.getOrPut(Blocks.CREAKING_HEART) { mutableSetOf() }
            .addAll(literalTrailColors(CreakingHeartBlockEntity::class.java))

        val json = JsonObject()
        for (block in BuiltInRegistries.BLOCK) {
            val values = colors[block] ?: continue
            json.add(BuiltInRegistries.BLOCK.getKey(block).path, JsonArray().apply { values.sorted().forEach(::add) })
        }
        return json
    }

    private fun wireTypes(codec: StreamCodec<*, *>): List<String> {
        namedCodecs[codec]?.let { return listOf(it) }
        if (codec.javaClass == unitClass) {
            return emptyList()
        }
        if (codec.javaClass == idMapperClass) {
            return listOf("var_int")
        }

        val children = codec.javaClass.declaredFields
            .filter { StreamCodec::class.java.isAssignableFrom(it.type) }
            .sortedBy { CODEC_FIELD.find(it.name)?.groupValues?.get(1)?.toInt() ?: Int.MAX_VALUE }
            .map { it.isAccessible = true; it.get(codec) as StreamCodec<*, *> }
        if (children.isEmpty()) {
            error("Unrecognised particle codec ${codec.javaClass.name}")
        }
        return children.flatMap { wireTypes(it) }
    }

    private fun literalTrailColors(owner: Class<*>): Set<Int> {
        val bytes = owner.classLoader.getResourceAsStream(owner.name.replace('.', '/') + ".class")!!.use { it.readBytes() }
        val node = ClassNode()
        ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

        val colors = linkedSetOf<Int>()
        val analyzer = Analyzer(LiteralIntInterpreter(colors))
        for (method in node.methods) {
            if (method.instructions.any { it is MethodInsnNode && it.owner == TRAIL_PARTICLE && it.name == "<init>" }) {
                analyzer.analyze(node.name, method)
            }
        }
        if (colors.isEmpty()) error("No trail particle colors found in ${owner.name}")
        return colors
    }

    private class IntLiterals(val values: Set<Int>) : BasicValue(Type.INT_TYPE) {
        override fun equals(other: Any?): Boolean = other is IntLiterals && values == other.values
        override fun hashCode(): Int = values.hashCode()
    }

    private class LiteralIntInterpreter(private val colors: MutableSet<Int>) : BasicInterpreter(Opcodes.ASM9) {
        override fun newOperation(insn: AbstractInsnNode): BasicValue? {
            return when (insn.opcode) {
                in Opcodes.ICONST_M1..Opcodes.ICONST_5 -> IntLiterals(setOf(insn.opcode - Opcodes.ICONST_0))
                Opcodes.BIPUSH, Opcodes.SIPUSH -> IntLiterals(setOf((insn as IntInsnNode).operand))
                Opcodes.LDC -> ((insn as LdcInsnNode).cst as? Int)?.let { IntLiterals(setOf(it)) } ?: super.newOperation(insn)
                else -> super.newOperation(insn)
            }
        }

        override fun naryOperation(insn: AbstractInsnNode, values: List<BasicValue>): BasicValue? {
            if (insn is MethodInsnNode && insn.owner == TRAIL_PARTICLE && insn.name == "<init>") {
                if (insn.desc != "(Lnet/minecraft/world/phys/Vec3;II)V") error("Unrecognised TrailParticleOption constructor ${insn.desc}")
                val color = values[values.size - 2] as? IntLiterals ?: error("Trail particle color is not a literal")
                colors.addAll(color.values)
            }
            return super.naryOperation(insn, values)
        }

        override fun merge(value1: BasicValue, value2: BasicValue): BasicValue {
            if (value1 is IntLiterals && value2 is IntLiterals) return IntLiterals(value1.values + value2.values)
            if (value1.type == Type.INT_TYPE && value2.type == Type.INT_TYPE) return BasicValue.INT_VALUE
            return super.merge(value1, value2)
        }
    }

    private companion object {
        const val TRAIL_PARTICLE = "net/minecraft/core/particles/TrailParticleOption"
        val CODEC_FIELD = Regex("""codec(\d+)$""")

        val unitClass: Class<*> = StreamCodec.unit<ByteBuf, Int>(0).javaClass
        val idMapperClass: Class<*> = ByteBufCodecs.idMapper<Int>({ 0 }, { 0 }).javaClass

        val namedCodecs: Map<StreamCodec<*, *>, String> = linkedMapOf(
            ByteBufCodecs.BOOL to "bool",
            ByteBufCodecs.BYTE to "byte",
            ByteBufCodecs.SHORT to "short",
            ByteBufCodecs.INT to "int",
            ByteBufCodecs.VAR_INT to "var_int",
            ByteBufCodecs.LONG to "long",
            ByteBufCodecs.VAR_LONG to "var_long",
            ByteBufCodecs.FLOAT to "float",
            ByteBufCodecs.DOUBLE to "double",
            ByteBufCodecs.STRING_UTF8 to "string",
            Vec3.STREAM_CODEC to "vec3",
            ItemStackTemplate.STREAM_CODEC to "item_stack",
            PositionSource.STREAM_CODEC to "position_source",
        )
    }
}
