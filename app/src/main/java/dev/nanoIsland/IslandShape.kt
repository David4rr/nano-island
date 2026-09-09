package dev.nanoIsland

data class IslandShape(
    val widthDp: Float,
    val heightDp: Float
) {
    companion object {
        val PUNCH_HOLE = IslandShape(10f, 10f)
        val PILL = IslandShape(120f, 35f)
        val CARD = IslandShape(360f, 180f)
    }
}
