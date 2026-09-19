package de.snowii.extractor.extractors.non_registry

import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.block.state.properties.Property

/**
 * The key that joins a block's property list in `blocks.json` to its definition in
 * `properties.json`.
 *
 * `Property.hashCode` cannot be used for this. It folds in `clazz.hashCode()`, which is the
 * JVM's identity hash and therefore differs between machines and between runs, so the two
 * files were only ever reproducible by whoever generated them. Deriving the key from the
 * property's own data instead makes an extraction byte-for-byte repeatable anywhere, which
 * is what lets anyone check the committed assets against the game.
 */
object PropertyKey {
    /**
     * Returns the key for [property], derived from its kind, its name and the values it
     * accepts.
     *
     * Name alone would not be enough: `age`, `level` and `distance` each name several
     * properties that differ only in their range, and blocks must keep referring to the
     * right one.
     */
    fun of(property: Property<*>): Int {
        val kind = when (property) {
            is BooleanProperty -> "boolean"
            is IntegerProperty -> "int"
            else -> "enum"
        }
        return "$kind|${property.name}|${values(property)}".hashCode()
    }

    /** The names [property] serialises its values as, in declaration order. */
    private fun <T : Comparable<T>> values(property: Property<T>): String =
        property.possibleValues.joinToString(",") { property.getName(it) }
}
