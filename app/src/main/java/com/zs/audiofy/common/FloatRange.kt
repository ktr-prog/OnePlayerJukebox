package com.zs.audiofy.common

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.zs.audiofy.common.FloatRange.Companion.packFloats

@Immutable
@JvmInline
value class FloatRange internal constructor(
    private val packedValue: Long
) {
    constructor(first: Float, second: Float):
            this(packFloats(first, second))

    @Stable
    val first: Float
        get() {
            // Explicitly compare against packed values to avoid auto-boxing of Size.Unspecified
            check(this.packedValue != Unspecified.packedValue) {
                "FloatRange is unspecified"
            }
            return unpackFloat1(packedValue)
        }

    @Stable
    val second: Float
        get() {
            // Explicitly compare against packed values to avoid auto-boxing of Size.Unspecified
            check(this.packedValue != Unspecified.packedValue) {
                "FloatRange is unspecified"
            }
            return unpackFloat2(packedValue)
        }

    @Stable
    operator fun component1(): Float = first

    @Stable
    operator fun component2(): Float = second

    companion object {

        /**
         * Packs two Float values into one Long value for use in inline classes.
         */
        internal inline fun packFloats(val1: Float, val2: Float): Long {
            val v1 = val1.toBits().toLong()
            val v2 = val2.toBits().toLong()
            return v1.shl(32) or (v2 and 0xFFFFFFFF)
        }

        /**
         * Unpacks the first Float value in [packFloats] from its returned Long.
         */
        private inline fun unpackFloat1(value: Long): Float {
            return Float.fromBits(value.shr(32).toInt())
        }

        /**
         * Unpacks the second Float value in [packFloats] from its returned Long.
         */
        private inline fun unpackFloat2(value: Long): Float {
            return Float.fromBits(value.and(0xFFFFFFFF).toInt())
        }

        /**
         * Represents an unspecified [FloatRange] value, usually a replacement for `null`
         * when a primitive value is desired.
         */
        @Stable
        val Unspecified = FloatRange(Float.NaN, Float.NaN)

        @Stable
        val FloatRange.isSpecified: Boolean get() =
            packedValue != Unspecified.packedValue
    }

    override fun toString() = if (isSpecified) {
        "$first..$second"
    } else {
        "FloatRange.Unspecified"
    }
}