package tim.core.game

import tim.core.render.Colors.rgb

/** Shared palette for the modern flat art style. */
object Style {
    val OUTLINE = rgb(0x1E2A3A)
    val OUTLINE_SOFT = rgb(0x3A4A5E)
    val SHADOW = 0x33000000
    val WHITE = rgb(0xFFFFFF)
    val CREAM = rgb(0xFFF6E5)
    val RED = rgb(0xE84855)
    val RED_DARK = rgb(0xB8323D)
    val ORANGE = rgb(0xF4A259)
    val YELLOW = rgb(0xFFD166)
    val GREEN = rgb(0x5CB85C)
    val GREEN_DARK = rgb(0x3E8E41)
    val TEAL = rgb(0x2EC4B6)
    val BLUE = rgb(0x3D8BFD)
    val BLUE_DARK = rgb(0x1F5FBF)
    val NAVY = rgb(0x2B3A67)
    val PURPLE = rgb(0x9B5DE5)
    val PINK = rgb(0xFF7AA2)
    val BROWN = rgb(0xA0623C)
    val BROWN_DARK = rgb(0x6E4126)
    val WOOD = rgb(0xD9A066)
    val WOOD_DARK = rgb(0xA8743D)
    val BRICK = rgb(0xD1603D)
    val BRICK_DARK = rgb(0x9E4529)
    val MORTAR = rgb(0xF2D6B3)
    val GREY = rgb(0x9AA5B1)
    val GREY_DARK = rgb(0x5C6672)
    val GREY_LIGHT = rgb(0xD5DCE4)
    val STEEL = rgb(0x7F8C9B)
    val STEEL_LIGHT = rgb(0xC4CDD6)
    val SKY = rgb(0xEAF4FF)
    val SKY_DARK = rgb(0xCFE3F8)
    val FLAME = rgb(0xFF9F1C)
    val FLAME_CORE = rgb(0xFFE066)
    val CHEESE = rgb(0xFFC857)

    /** Outline stroke width in world units. */
    const val LINE = 2.0
}
