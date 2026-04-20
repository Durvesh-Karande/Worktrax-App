package com.worktrax.app.data

enum class WeightUnit { KG, LB;
    val code: String get() = if (this == KG) "kg" else "lb"
    companion object { fun from(code: String): WeightUnit = if (code.equals("lb", true)) LB else KG }
}

enum class WorkoutType(val code: String) {
    STRENGTH("strength"),
    CARDIO("cardio"),
    AEROBIC("aerobic"),
    YOGA("yoga");
    companion object { fun from(code: String): WorkoutType = values().firstOrNull { it.code == code } ?: STRENGTH }
}

enum class ThemeMode(val code: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");
    companion object { fun from(code: String): ThemeMode = values().firstOrNull { it.code == code } ?: SYSTEM }
}

enum class Equipment(val label: String) {
    BARBELL("Barbell"),
    DUMBBELL("Dumbbell"),
    MACHINE("Machine"),
    BODYWEIGHT("Bodyweight"),
    CABLE("Cable");
    companion object { fun from(label: String): Equipment = values().firstOrNull { it.label == label } ?: BARBELL }
}

data class ExerciseDef(
    val id: String,
    val name: String,
    val muscle: String,
    val equipment: Equipment,
    val type: WorkoutType = WorkoutType.STRENGTH,
)

data class SetEntry(
    val reps: Int,
    val weight: Double,
    val unit: WeightUnit,
    val at: String,
)

data class ExerciseEntry(
    val id: String,
    val name: String,
    val muscle: String,
    val sets: List<SetEntry>,
)

data class Workout(
    val id: String,
    val date: String,
    val type: WorkoutType,
    val durationSec: Int,
    val exercises: List<ExerciseEntry>,
)

val EQUIPMENT_ORDER: List<Equipment> = listOf(
    Equipment.BARBELL,
    Equipment.DUMBBELL,
    Equipment.MACHINE,
    Equipment.CABLE,
    Equipment.BODYWEIGHT,
)
