package dev.nanoIsland

data class IslandShape(
    val widthDp: Float,
    val heightDp: Float,
    val cornerRadiusDp: Float
) {
    companion object {
        // Realistic punch-hole camera circle (covers camera lens)
        val PUNCH_HOLE = IslandShape(widthDp = 34f, heightDp = 34f, cornerRadiusDp = 17f)

        // iOS-style compact idle pill (capsule: radius = height / 2)
        val PILL = IslandShape(widthDp = 126f, heightDp = 36f, cornerRadiusDp = 18f)

        // Fluid power-off lock squircle
        val ROUNDED_SQUARE = IslandShape(widthDp = 52f, heightDp = 52f, cornerRadiusDp = 16f)

        // iOS-style expanded card (sleek 32dp corners, not a sausage oval)
        val CARD = IslandShape(widthDp = 360f, heightDp = 170f, cornerRadiusDp = 32f)
    }
}
