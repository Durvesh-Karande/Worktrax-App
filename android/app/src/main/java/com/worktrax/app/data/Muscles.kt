package com.worktrax.app.data

object Muscles {
    const val CHEST = "Chest"
    const val BACK = "Back"
    const val SHOULDERS = "Shoulders"
    const val BICEPS = "Biceps"
    const val TRICEPS = "Triceps"
    const val QUADS = "Quads"
    const val HAMSTRINGS = "Hamstrings"
    const val GLUTES = "Glutes"
    const val CALVES = "Calves"
    const val CORE = "Core"

    val ALL: List<String> = listOf(
        CHEST, BACK, SHOULDERS, BICEPS, TRICEPS,
        QUADS, HAMSTRINGS, GLUTES, CALVES, CORE,
    )

    enum class Side { FRONT, BACK }

    val SIDE: Map<String, Side> = mapOf(
        CHEST to Side.FRONT,
        BACK to Side.BACK,
        SHOULDERS to Side.FRONT,
        BICEPS to Side.FRONT,
        TRICEPS to Side.BACK,
        QUADS to Side.FRONT,
        HAMSTRINGS to Side.BACK,
        GLUTES to Side.BACK,
        CALVES to Side.BACK,
        CORE to Side.FRONT,
    )
}
