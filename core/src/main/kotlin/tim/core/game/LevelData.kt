package tim.core.game

import tim.core.physics.AABB
import tim.core.game.PartType as T

/** The puzzle progression. Each level introduces one idea; solutions are verified by tests. */
object LevelData {

    val levels: List<Level> = listOf(
        level("l01", "Roll it in", "Get the ball into the bucket") {
            floor()
            fixed(T.WOOD_WALL, 40.0, 200.0)
            fixed(T.BASKETBALL, 48.0, 136.0)
            val bucket = fixed(T.BUCKET, 176.0, 336.0)
            tray(T.INCLINE, 1)
            solve(T.INCLINE, 40.0, 168.0)
            goal = Goal.BallInto(bucket)
            hint = "Put the ramp under the ball so it rolls off the shelf."
        },
        level("l02", "Mind the gap", "Get the ball to the star") {
            floor(0.0, 288.0, 304.0)
            floor(352.0, 640.0, 304.0)
            floor()
            fixed(T.INCLINE, 0.0, 272.0)
            fixed(T.BOWLING_BALL, 8.0, 224.0)
            val star = fixed(T.STAR, 560.0, 272.0)
            tray(T.WOOD_WALL, 1)
            solve(T.WOOD_WALL, 288.0, 304.0)
            goal = Goal.Activate(star)
            hint = "Fill the hole with the plank."
        },
        level("l03", "Pop!", "Pop the balloon") {
            floor()
            wallV(0.0, 0.0, 384.0)
            fixed(T.WOOD_WALL, 32.0, 176.0)   // ceiling the balloon floats up against
            fixed(T.WOOD_WALL, 80.0, 240.0)   // shelf for the candle
            fixed(T.CANDLE, 80.0, 208.0)
            tray(T.BALLOON, 1)
            solve(T.BALLOON, 48.0, 320.0)
            goal = Goal.PopAllBalloons
            hint = "Balloons float up. Put the balloon under the candle flame."
        },
    )

    val demo: Level = levels[0]
}
