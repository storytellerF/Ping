package com.storyteller_f.ping.cubism

/**
 * MOC3の整合性を検証するかどうか。有効ならtrue。
 */
const val MOC_CONSISTENCY_VALIDATION_ENABLE = true

/**
 * Enable/Disable debug logging.
 */
const val DEBUG_LOG_ENABLE = true

/**
 * Enable/Disable premultiplied alpha.
 */
const val PREMULTIPLIED_ALPHA_ENABLE = true

/**
 * Scaling rate.
 */
enum class Scale(val value: Float) {
    /**
     * Default scaling rate
     */
    DEFAULT(1.0f),

    /**
     * Maximum scaling rate
     */
    MAX(2.0f),

    /**
     * Minimum scaling rate
     */
    MIN(0.8f)

}

/**
 * Logical view coordinate system.
 */
enum class LogicalView(val value: Float) {
    /**
     * Left end
     */
    LEFT(-1.0f),

    /**
     * Right end
     */
    RIGHT(1.0f),

    /**
     * Bottom end
     */
    BOTTOM(-1.0f),

    /**
     * Top end
     */
    TOP(1.0f)

}

/**
 * Maximum logical view coordinate system.
 */
enum class MaxLogicalView(val value: Float) {
    /**
     * Maximum left end
     */
    LEFT(-2.0f),

    /**
     * Maximum right end
     */
    RIGHT(2.0f),

    /**
     * Maximum bottom end
     */
    BOTTOM(-2.0f),

    /**
     * Maximum top end
     */
    TOP(2.0f)

}

/**
 * Motion group
 */
enum class MotionGroup(@JvmField val id: String) {
    /**
     * ID of the motion to be played at idling.
     */
    IDLE("Idle"),

    /**
     * ID of the motion to be played at tapping body.
     */
    TAP_BODY("TapBody")

}

/**
 * [Head] tag for hit detection.
 * (Match with external definition file(json))
 */
enum class HitAreaName(val id: String) {
    HEAD("Head"),
    BODY("Body")

}

/**
 * Motion priority
 */
enum class Priority(@JvmField val priority: Int) {
    NONE(0),
    IDLE(1),
    NORMAL(2),
    FORCE(3)
}