package com.chen.classtable.rule

/** 课程卡低饱和配色（Light / Dark 成对，保证文字对比度）。 */
object CoursePalette {

    data class Pair(val container: Int, val content: Int)

    private val LIGHT = arrayOf(
        Pair(0xFFDCE6FF.toInt(), 0xFF1F3A78.toInt()),
        Pair(0xFFD9F3EA.toInt(), 0xFF134D40.toInt()),
        Pair(0xFFE9E0FF.toInt(), 0xFF41306B.toInt()),
        Pair(0xFFFFE4D6.toInt(), 0xFF6A321D.toInt()),
        Pair(0xFFF3E8C8.toInt(), 0xFF4F3D14.toInt()),
        Pair(0xFFFFE0E8.toInt(), 0xFF6B2438.toInt()),
        Pair(0xFFD9F1FB.toInt(), 0xFF124A5C.toInt()),
        Pair(0xFFE5F2DA.toInt(), 0xFF2F4A1F.toInt()),
        Pair(0xFFF1E2F6.toInt(), 0xFF4A2653.toInt())
    )

    private val DARK = arrayOf(
        Pair(0xFF273759.toInt(), 0xFFD7E3FF.toInt()),
        Pair(0xFF1C3D36.toInt(), 0xFFC8F0E5.toInt()),
        Pair(0xFF332A4D.toInt(), 0xFFE4D9FF.toInt()),
        Pair(0xFF4A2C20.toInt(), 0xFFFFDBCA.toInt()),
        Pair(0xFF3E3620.toInt(), 0xFFEFE1BB.toInt()),
        Pair(0xFF472731.toInt(), 0xFFFFD9E3.toInt()),
        Pair(0xFF1D3942.toInt(), 0xFFCDECF7.toInt()),
        Pair(0xFF2B3823.toInt(), 0xFFDCEDCE.toInt()),
        Pair(0xFF38283D.toInt(), 0xFFF0DDF6.toInt())
    )

    fun pairFor(name: String, offset: Int, dark: Boolean): Pair {
        val palette = if (dark) DARK else LIGHT
        val index = Math.floorMod(name.hashCode() + offset, palette.size)
        return palette[index]
    }
}
