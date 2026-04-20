package com.worktrax.app.data

val EXERCISES: List<ExerciseDef> = listOf(
    // ─────────── STRENGTH ───────────
    // Chest
    ExerciseDef("bench-press", "Bench Press", Muscles.CHEST, Equipment.BARBELL),
    ExerciseDef("incline-db-press", "Incline Dumbbell Press", Muscles.CHEST, Equipment.DUMBBELL),
    ExerciseDef("db-fly", "Dumbbell Fly", Muscles.CHEST, Equipment.DUMBBELL),
    ExerciseDef("chest-press-machine", "Chest Press", Muscles.CHEST, Equipment.MACHINE),
    ExerciseDef("push-up", "Push-Up", Muscles.CHEST, Equipment.BODYWEIGHT),
    ExerciseDef("cable-crossover", "Cable Crossover", Muscles.CHEST, Equipment.CABLE),

    // Back
    ExerciseDef("deadlift", "Deadlift", Muscles.BACK, Equipment.BARBELL),
    ExerciseDef("bent-over-row", "Bent-Over Row", Muscles.BACK, Equipment.BARBELL),
    ExerciseDef("db-row", "Dumbbell Row", Muscles.BACK, Equipment.DUMBBELL),
    ExerciseDef("lat-pulldown", "Lat Pulldown", Muscles.BACK, Equipment.CABLE),
    ExerciseDef("seated-row", "Seated Row", Muscles.BACK, Equipment.MACHINE),
    ExerciseDef("pull-up", "Pull-Up", Muscles.BACK, Equipment.BODYWEIGHT),

    // Shoulders
    ExerciseDef("ohp", "Overhead Press", Muscles.SHOULDERS, Equipment.BARBELL),
    ExerciseDef("db-shoulder-press", "Dumbbell Shoulder Press", Muscles.SHOULDERS, Equipment.DUMBBELL),
    ExerciseDef("lateral-raise", "Lateral Raise", Muscles.SHOULDERS, Equipment.DUMBBELL),
    ExerciseDef("rear-delt-fly", "Rear Delt Fly", Muscles.SHOULDERS, Equipment.DUMBBELL),
    ExerciseDef("face-pull", "Face Pull", Muscles.SHOULDERS, Equipment.CABLE),
    ExerciseDef("shoulder-press-machine", "Shoulder Press", Muscles.SHOULDERS, Equipment.MACHINE),

    // Biceps
    ExerciseDef("barbell-curl", "Barbell Curl", Muscles.BICEPS, Equipment.BARBELL),
    ExerciseDef("db-curl", "Dumbbell Curl", Muscles.BICEPS, Equipment.DUMBBELL),
    ExerciseDef("hammer-curl", "Hammer Curl", Muscles.BICEPS, Equipment.DUMBBELL),
    ExerciseDef("cable-curl", "Cable Curl", Muscles.BICEPS, Equipment.CABLE),
    ExerciseDef("preacher-curl", "Preacher Curl", Muscles.BICEPS, Equipment.MACHINE),

    // Triceps
    ExerciseDef("close-grip-bench", "Close-Grip Bench", Muscles.TRICEPS, Equipment.BARBELL),
    ExerciseDef("skullcrusher", "Skullcrusher", Muscles.TRICEPS, Equipment.BARBELL),
    ExerciseDef("tricep-pushdown", "Tricep Pushdown", Muscles.TRICEPS, Equipment.CABLE),
    ExerciseDef("overhead-tricep-ext", "Overhead Extension", Muscles.TRICEPS, Equipment.DUMBBELL),
    ExerciseDef("dip", "Dip", Muscles.TRICEPS, Equipment.BODYWEIGHT),

    // Quads
    ExerciseDef("back-squat", "Back Squat", Muscles.QUADS, Equipment.BARBELL),
    ExerciseDef("front-squat", "Front Squat", Muscles.QUADS, Equipment.BARBELL),
    ExerciseDef("leg-press", "Leg Press", Muscles.QUADS, Equipment.MACHINE),
    ExerciseDef("lunge", "Lunge", Muscles.QUADS, Equipment.DUMBBELL),
    ExerciseDef("leg-extension", "Leg Extension", Muscles.QUADS, Equipment.MACHINE),
    ExerciseDef("bulgarian-split-squat", "Bulgarian Split Squat", Muscles.QUADS, Equipment.DUMBBELL),

    // Hamstrings
    ExerciseDef("romanian-deadlift", "Romanian Deadlift", Muscles.HAMSTRINGS, Equipment.BARBELL),
    ExerciseDef("leg-curl", "Leg Curl", Muscles.HAMSTRINGS, Equipment.MACHINE),
    ExerciseDef("good-morning", "Good Morning", Muscles.HAMSTRINGS, Equipment.BARBELL),
    ExerciseDef("nordic-curl", "Nordic Curl", Muscles.HAMSTRINGS, Equipment.BODYWEIGHT),

    // Glutes
    ExerciseDef("hip-thrust", "Hip Thrust", Muscles.GLUTES, Equipment.BARBELL),
    ExerciseDef("glute-bridge", "Glute Bridge", Muscles.GLUTES, Equipment.BODYWEIGHT),
    ExerciseDef("cable-kickback", "Cable Kickback", Muscles.GLUTES, Equipment.CABLE),
    ExerciseDef("glute-machine", "Glute Machine", Muscles.GLUTES, Equipment.MACHINE),

    // Calves
    ExerciseDef("standing-calf-raise", "Standing Calf Raise", Muscles.CALVES, Equipment.MACHINE),
    ExerciseDef("seated-calf-raise", "Seated Calf Raise", Muscles.CALVES, Equipment.MACHINE),
    ExerciseDef("db-calf-raise", "Dumbbell Calf Raise", Muscles.CALVES, Equipment.DUMBBELL),
    ExerciseDef("bw-calf-raise", "Bodyweight Calf Raise", Muscles.CALVES, Equipment.BODYWEIGHT),

    // Core
    ExerciseDef("plank", "Plank", Muscles.CORE, Equipment.BODYWEIGHT),
    ExerciseDef("hanging-leg-raise", "Hanging Leg Raise", Muscles.CORE, Equipment.BODYWEIGHT),
    ExerciseDef("ab-rollout", "Ab Rollout", Muscles.CORE, Equipment.BODYWEIGHT),
    ExerciseDef("cable-crunch", "Cable Crunch", Muscles.CORE, Equipment.CABLE),
    ExerciseDef("russian-twist", "Russian Twist", Muscles.CORE, Equipment.DUMBBELL),
    ExerciseDef("sit-up", "Sit-Up", Muscles.CORE, Equipment.BODYWEIGHT),

    // ─────────── CARDIO ───────────
    ExerciseDef("treadmill-run", "Treadmill Run", "Run", Equipment.MACHINE, WorkoutType.CARDIO),
    ExerciseDef("outdoor-run", "Outdoor Run", "Run", Equipment.BODYWEIGHT, WorkoutType.CARDIO),
    ExerciseDef("cycling-stationary", "Stationary Bike", "Bike", Equipment.MACHINE, WorkoutType.CARDIO),
    ExerciseDef("cycling-outdoor", "Outdoor Cycling", "Bike", Equipment.BODYWEIGHT, WorkoutType.CARDIO),
    ExerciseDef("rower", "Rowing Machine", "Row", Equipment.MACHINE, WorkoutType.CARDIO),
    ExerciseDef("swimming", "Swimming", "Pool", Equipment.BODYWEIGHT, WorkoutType.CARDIO),
    ExerciseDef("elliptical", "Elliptical", "Bike", Equipment.MACHINE, WorkoutType.CARDIO),
    ExerciseDef("stair-climber", "Stair Climber", "Run", Equipment.MACHINE, WorkoutType.CARDIO),
    ExerciseDef("brisk-walk", "Brisk Walk", "Run", Equipment.BODYWEIGHT, WorkoutType.CARDIO),
    ExerciseDef("incline-treadmill", "Incline Treadmill", "Run", Equipment.MACHINE, WorkoutType.CARDIO),

    // ─────────── AEROBIC (HIIT & intervals) ───────────
    ExerciseDef("burpees", "Burpees", "HIIT", Equipment.BODYWEIGHT, WorkoutType.AEROBIC),
    ExerciseDef("mountain-climbers", "Mountain Climbers", "HIIT", Equipment.BODYWEIGHT, WorkoutType.AEROBIC),
    ExerciseDef("jumping-jacks", "Jumping Jacks", "HIIT", Equipment.BODYWEIGHT, WorkoutType.AEROBIC),
    ExerciseDef("high-knees", "High Knees", "HIIT", Equipment.BODYWEIGHT, WorkoutType.AEROBIC),
    ExerciseDef("box-jumps", "Box Jumps", "Plyo", Equipment.BODYWEIGHT, WorkoutType.AEROBIC),
    ExerciseDef("sprint-intervals", "Sprint Intervals", "HIIT", Equipment.BODYWEIGHT, WorkoutType.AEROBIC),
    ExerciseDef("tabata-circuit", "Tabata Circuit", "Interval", Equipment.BODYWEIGHT, WorkoutType.AEROBIC),
    ExerciseDef("battle-ropes", "Battle Ropes", "HIIT", Equipment.MACHINE, WorkoutType.AEROBIC),
    ExerciseDef("kettlebell-swing", "Kettlebell Swing", "HIIT", Equipment.DUMBBELL, WorkoutType.AEROBIC),
    ExerciseDef("jump-rope", "Jump Rope", "Plyo", Equipment.BODYWEIGHT, WorkoutType.AEROBIC),
    ExerciseDef("step-ups", "Step-Ups", "Plyo", Equipment.BODYWEIGHT, WorkoutType.AEROBIC),
    ExerciseDef("bear-crawl", "Bear Crawl", "HIIT", Equipment.BODYWEIGHT, WorkoutType.AEROBIC),

    // ─────────── YOGA ───────────
    ExerciseDef("sun-salutation", "Sun Salutation", "Flow", Equipment.BODYWEIGHT, WorkoutType.YOGA),
    ExerciseDef("downward-dog", "Downward Dog", "Pose", Equipment.BODYWEIGHT, WorkoutType.YOGA),
    ExerciseDef("warrior-two", "Warrior II", "Pose", Equipment.BODYWEIGHT, WorkoutType.YOGA),
    ExerciseDef("tree-pose", "Tree Pose", "Balance", Equipment.BODYWEIGHT, WorkoutType.YOGA),
    ExerciseDef("cobra-pose", "Cobra Pose", "Backbend", Equipment.BODYWEIGHT, WorkoutType.YOGA),
    ExerciseDef("childs-pose", "Child's Pose", "Restorative", Equipment.BODYWEIGHT, WorkoutType.YOGA),
    ExerciseDef("pigeon-pose", "Pigeon Pose", "Hip Opener", Equipment.BODYWEIGHT, WorkoutType.YOGA),
    ExerciseDef("vinyasa-flow", "Vinyasa Flow", "Flow", Equipment.BODYWEIGHT, WorkoutType.YOGA),
    ExerciseDef("bridge-pose", "Bridge Pose", "Backbend", Equipment.BODYWEIGHT, WorkoutType.YOGA),
    ExerciseDef("savasana", "Savasana", "Restorative", Equipment.BODYWEIGHT, WorkoutType.YOGA),
    ExerciseDef("triangle-pose", "Triangle Pose", "Pose", Equipment.BODYWEIGHT, WorkoutType.YOGA),
    ExerciseDef("chair-pose", "Chair Pose", "Pose", Equipment.BODYWEIGHT, WorkoutType.YOGA),
)
