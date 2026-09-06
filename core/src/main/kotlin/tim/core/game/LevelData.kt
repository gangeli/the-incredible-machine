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
        level("l04", "Mort is hungry", "Get Mort to the cheese") {
            floor(0.0, 256.0)
            floor(320.0, 640.0)
            fixed(T.MOUSE, 40.0, 368.0)
            fixed(T.CHEESE, 552.0, 368.0)
            tray(T.WOOD_WALL, 1)
            solve(T.WOOD_WALL, 256.0, 384.0)
            goal = Goal.MouseEatsCheese
            hint = "Mort can't jump. Fill the hole with the plank."
        },
        level("l05", "Boing!", "Get the ball to the star") {
            floor()
            fixed(T.BASKETBALL, 144.0, 96.0)
            val star = fixed(T.STAR, 144.0, 8.0)
            tray(T.TRAMPOLINE, 1)
            solve(T.TRAMPOLINE, 128.0, 360.0)
            goal = Goal.Activate(star)
            hint = "A trampoline bounces the ball higher than where it started."
        },
        level("l06", "Moving walkway", "Get the ball to the star") {
            floor()
            fixed(T.WOOD_WALL, 0.0, 240.0)
            fixed(T.WOOD_WALL, 160.0, 240.0)
            fixed(T.WOOD_WALL, 224.0, 240.0)
            fixed(T.BOWLING_BALL, 96.0, 96.0)
            val star = fixed(T.STAR, 256.0, 208.0)
            wallV(288.0, 208.0, 240.0)
            tray(T.CONVEYOR, 1)
            solve(T.CONVEYOR, 64.0, 232.0)
            goal = Goal.Activate(star)
            hint = "The conveyor belt carries things along. Fill the gap with it."
        },
        level("l07", "Trap Pokey", "Trap the cat under the cage") {
            floor()
            val cat = fixed(T.CAT, 296.0, 352.0)
            tray(T.CAGE, 1)
            solve(T.CAGE, 296.0, 96.0)
            goal = Goal.Trapped(cat)
            hint = "The cage falls straight down. Hold it right above Pokey."
        },
        level("l08", "Fire!", "Ring the bell") {
            floor()
            fixed(T.CANNON, 80.0, 344.0)
            val bell = fixed(T.BELL, 560.0, 336.0)
            tray(T.CANDLE, 1)
            solve(T.CANDLE, 64.0, 352.0)
            goal = Goal.Activate(bell)
            hint = "The candle flame lights the cannon's fuse. Put it right behind the cannon."
        },
        level("l09", "Blast off", "Launch the rocket") {
            floor()
            val rocket = fixed(T.ROCKET, 304.0, 320.0)
            tray(T.CANDLE, 1)
            solve(T.CANDLE, 280.0, 352.0)
            goal = Goal.Activate(rocket)
            hint = "Light the rocket's fuse: stand the candle right next to it."
        },
        level("l10", "Kaboom", "Blow up the dynamite") {
            floor()
            val dyn = fixed(T.DYNAMITE, 320.0, 352.0)
            fixed(T.BASKETBALL, 360.0, 352.0)
            tray(T.CANDLE, 1)
            solve(T.CANDLE, 304.0, 352.0)
            goal = Goal.Activate(dyn)
            hint = "The candle lights the fuse. Put it right next to the dynamite."
        },
        level("l11", "Switch it on", "Get the ball to the star") {
            floor()
            val sw = fixed(T.SWITCH, 120.0, 344.0)
            val fan = fixed(T.FAN, 336.0, 344.0)
            wire(sw, fan)
            fixed(T.TENNIS_BALL, 400.0, 368.0)
            val star = fixed(T.STAR, 592.0, 352.0)
            tray(T.BASEBALL, 1)
            solve(T.BASEBALL, 128.0, 120.0)
            goal = Goal.Activate(star)
            hint = "Drop the baseball on the switch to turn on the fan."
        },
        level("l12", "Seesaw", "Get the baseball to the star") {
            floor()
            fixed(T.SEESAW, 160.0, 352.0)
            fixed(T.BOWLING_BALL, 216.0, 40.0)
            val star = fixed(T.STAR, 80.0, 208.0)
            tray(T.BASEBALL, 1)
            solve(T.BASEBALL, 168.0, 320.0)
            goal = Goal.Activate(star)
            hint = "Put the baseball on the low end of the seesaw. The bowling ball will fling it."
        },
        level("l13", "Swish", "Get the ball through the hoop") {
            floor()
            fixed(T.WOOD_WALL, 40.0, 200.0)
            fixed(T.BASKETBALL, 48.0, 136.0)
            val hoop = fixed(T.HOOP, 128.0, 240.0, flipped = true)
            tray(T.INCLINE, 1)
            solve(T.INCLINE, 40.0, 168.0)
            goal = Goal.BallInto(hoop)
            hint = "Same trick as before: a ramp under the ball."
        },
        level("l14", "Punch", "Get the ball to the star") {
            floor()
            fixed(T.BASEBALL, 136.0, 40.0)
            fixed(T.BASKETBALL, 200.0, 352.0)
            val star = fixed(T.STAR, 560.0, 352.0)
            tray(T.BOXING_GLOVE, 1)
            solve(T.BOXING_GLOVE, 128.0, 344.0)
            goal = Goal.Activate(star)
            hint = "The glove punches when something lands on it."
        },
        level("l15", "Snip", "Lower the bucket to the floor") {
            floor()
            val hook = fixed(T.HOOK, 296.0, 0.0)
            val bucket = fixed(T.BUCKET, 280.0, 200.0)
            rope(hook, bucket)
            fixed(T.WOOD_WALL, 224.0, 128.0)
            fixed(T.BASEBALL, 272.0, 24.0)
            tray(T.SCISSORS, 1)
            solve(T.SCISSORS, 272.0, 96.0)
            goal = Goal.Reach(bucket, AABB(0.0, 340.0, 640.0, 400.0))
            hint = "Scissors cut the rope when something lands on their handles."
        },
        level("l16", "Windy", "Get the balloon to the star") {
            floor()
            for (i in 0 until 4) fixed(T.WOOD_WALL, 80.0 + i * 64, 160.0)
            fixed(T.BALLOON, 96.0, 320.0)
            val star = fixed(T.STAR, 272.0, 176.0)
            tray(T.FAN, 1)
            solve(T.FAN, 32.0, 176.0)
            goal = Goal.Activate(star)
            hint = "The balloon floats up to the ceiling. Blow it sideways with the fan."
        },
        level("l17", "Bounce back", "Get the ball to the star") {
            floor()
            fixed(T.INCLINE, 0.0, 352.0)
            fixed(T.BASKETBALL, 32.0, 320.0)
            val star = fixed(T.STAR, 0.0, 312.0)
            tray(T.BUMPER, 1)
            solve(T.BUMPER, 552.0, 352.0)
            goal = Goal.Activate(star)
            hint = "The bumper bounces the ball back the way it came."
        },
        level("l18", "Two ramps", "Get the ball into the bucket") {
            floor()
            fixed(T.WOOD_WALL, 0.0, 120.0)
            fixed(T.BOWLING_BALL, 8.0, 56.0)
            fixed(T.WOOD_WALL, 96.0, 240.0)
            fixed(T.WOOD_WALL, 160.0, 240.0)
            val bucket = fixed(T.BUCKET, 336.0, 336.0)
            tray(T.INCLINE, 2)
            solve(T.INCLINE, 0.0, 88.0)
            solve(T.INCLINE, 136.0, 208.0)
            goal = Goal.BallInto(bucket)
            hint = "One ramp on each shelf keeps the ball rolling."
        },
        level("l19", "Cat and mouse", "Get Mort to the cheese") {
            floor()
            fixed(T.MOUSE, 40.0, 368.0)
            fixed(T.CHEESE, 248.0, 368.0)
            val cat = fixed(T.CAT, 328.0, 352.0, flipped = true)
            tray(T.CAGE, 1)
            solve(T.CAGE, 328.0, 96.0)
            goal = Goal.MouseEatsCheese
            hint = "Pokey will chase Mort. Trap Pokey first!"
            timeLimit = 20.0
        },
        level("l20", "Staircase", "Get the ball to the star") {
            floor()
            fixed(T.WOOD_WALL, 0.0, 96.0)
            fixed(T.BOWLING_BALL, 8.0, 32.0)
            fixed(T.WOOD_WALL, 128.0, 208.0)
            fixed(T.WOOD_WALL, 192.0, 208.0)
            floor(368.0, 640.0, 320.0)
            val star = fixed(T.STAR, 592.0, 288.0)
            tray(T.INCLINE, 1)
            tray(T.WOOD_WALL, 1)
            solve(T.INCLINE, 0.0, 64.0)
            solve(T.WOOD_WALL, 304.0, 320.0)
            goal = Goal.Activate(star)
            hint = "Ramps keep the ball rolling; the plank bridges the gap."
        },
        level("l21", "Blow it up", "Pop the balloon") {
            floor()
            for (i in 0 until 5) fixed(T.WOOD_WALL, 80.0 + i * 64, 160.0)
            fixed(T.BALLOON, 96.0, 320.0)
            fixed(T.WOOD_WALL, 272.0, 224.0)
            fixed(T.CANDLE, 280.0, 192.0)
            tray(T.FAN, 1)
            solve(T.FAN, 32.0, 176.0)
            goal = Goal.PopAllBalloons
            hint = "Blow the balloon along the ceiling into the candle flame."
        },
        level("l22", "On the moon", "Get the ball to the star") {
            gravity = 160.0
            floor()
            fixed(T.BOWLING_BALL, 144.0, 200.0)
            val star = fixed(T.STAR, 144.0, 24.0)
            tray(T.TRAMPOLINE, 1)
            solve(T.TRAMPOLINE, 128.0, 360.0)
            goal = Goal.Activate(star)
            hint = "We are on the moon: everything falls slowly and bounces high!"
        },
        level("l23", "Take aim", "Ring the bell") {
            floor()
            for (i in 0 until 3) fixed(T.WOOD_WALL, 160.0 + i * 64, 232.0)
            fixed(T.CANDLE, 168.0, 200.0)
            fixed(T.WOOD_WALL, 528.0, 256.0)
            val bell = fixed(T.BELL, 536.0, 208.0)
            tray(T.CANNON, 1)
            solve(T.CANNON, 184.0, 192.0)
            goal = Goal.Activate(bell)
            hint = "The candle lights the cannon's fuse. Make sure the cannon points at the bell!"
        },
        level("l24", "Burn the rope", "Trap the cat under the cage") {
            floor()
            val hook = fixed(T.HOOK, 296.0, 0.0)
            val cage = fixed(T.CAGE, 280.0, 120.0)
            rope(hook, cage)
            val cat = fixed(T.CAT, 280.0, 352.0)
            fixed(T.WOOD_WALL, 256.0, 96.0)
            tray(T.CANDLE, 1)
            solve(T.CANDLE, 296.0, 64.0)
            goal = Goal.Trapped(cat)
            hint = "A candle flame burns through rope. Put it under the rope."
        },
        level("l25", "Big bang", "Get the ball to the star") {
            floor()
            fixed(T.DYNAMITE, 296.0, 352.0)
            fixed(T.BASKETBALL, 336.0, 352.0)
            val star = fixed(T.STAR, 552.0, 352.0)
            tray(T.CANDLE, 1)
            solve(T.CANDLE, 280.0, 352.0)
            goal = Goal.Activate(star)
            hint = "The explosion will throw the ball. Light the fuse!"
        },
        level("l26", "Rocket rescue", "Launch the rocket") {
            floor()
            fixed(T.CANDLE, 400.0, 352.0)
            tray(T.ROCKET, 1)
            solve(T.ROCKET, 416.0, 320.0)
            goal = Goal.ActivateAll(T.ROCKET)
            hint = "Stand the rocket right next to the candle."
        },
        level("l27", "Mort's ride", "Get Mort to the cheese") {
            floor(0.0, 224.0)
            floor(320.0, 640.0)
            fixed(T.MOUSE, 40.0, 368.0)
            fixed(T.CHEESE, 560.0, 368.0)
            tray(T.CONVEYOR, 1)
            solve(T.CONVEYOR, 224.0, 376.0)
            goal = Goal.MouseEatsCheese
            hint = "Bridge the gap with the conveyor. Which way should it go? Flip it!"
        },
        level("l28", "Ding dong", "Ring the bell") {
            floor()
            fixed(T.WOOD_WALL, 0.0, 168.0)
            fixed(T.BOWLING_BALL, 8.0, 104.0)
            fixed(T.WOOD_WALL, 224.0, 80.0)
            val bell = fixed(T.BELL, 232.0, 96.0)
            tray(T.INCLINE, 1)
            tray(T.TRAMPOLINE, 1)
            solve(T.INCLINE, 0.0, 136.0)
            solve(T.TRAMPOLINE, 152.0, 360.0)
            goal = Goal.Activate(bell)
            hint = "Roll the ball off the shelf onto the trampoline; it bounces up to the bell."
        },
        level("l29", "Other way round", "Get the ball into the bucket") {
            floor()
            fixed(T.WOOD_WALL, 536.0, 200.0)
            fixed(T.BASKETBALL, 560.0, 136.0)
            val bucket = fixed(T.BUCKET, 416.0, 336.0)
            tray(T.INCLINE, 1)
            solve(T.INCLINE, 536.0, 168.0, flipped = true)
            goal = Goal.BallInto(bucket)
            hint = "The ramp must slope the other way. Tap it and press the flip button!"
        },
        level("l30", "Turn around, Mort", "Get Mort to the cheese") {
            floor(0.0, 480.0)
            fixed(T.MOUSE, 400.0, 368.0)
            fixed(T.CHEESE, 40.0, 368.0)
            tray(T.SMALL_WALL, 1)
            solve(T.SMALL_WALL, 464.0, 352.0, rotation = 1)
            goal = Goal.MouseEatsCheese
            hint = "Mort walks until he bumps into something. Stand the brick up on its end to turn him around."
            timeLimit = 20.0
        },
        level("l31", "Double trouble", "Get both balls into the buckets") {
            floor()
            fixed(T.WOOD_WALL, 40.0, 200.0)
            fixed(T.BASKETBALL, 48.0, 136.0)
            val b1 = fixed(T.BUCKET, 176.0, 336.0)
            fixed(T.WOOD_WALL, 536.0, 200.0)
            fixed(T.BASKETBALL, 560.0, 136.0)
            val b2 = fixed(T.BUCKET, 416.0, 336.0)
            tray(T.INCLINE, 2)
            solve(T.INCLINE, 40.0, 168.0)
            solve(T.INCLINE, 536.0, 168.0, flipped = true)
            goal = Goal.All(listOf(Goal.BallInto(b1), Goal.BallInto(b2)))
            hint = "One ramp for each ball. One of them needs flipping."
        },
        level("l32", "Rope trick", "Trap the cat under the cage") {
            floor()
            val hook = fixed(T.HOOK, 296.0, 0.0)
            val cage = fixed(T.CAGE, 280.0, 136.0)
            rope(hook, cage)
            val cat = fixed(T.CAT, 280.0, 352.0)
            fixed(T.WOOD_WALL, 208.0, 128.0)
            tray(T.SCISSORS, 1)
            tray(T.BASEBALL, 1)
            solve(T.SCISSORS, 272.0, 96.0)
            solve(T.BASEBALL, 272.0, 24.0)
            goal = Goal.Trapped(cat)
            hint = "Scissors on the shelf next to the rope, and a ball to land on them."
        },
        level("l33", "Chain reaction", "Pop the balloon") {
            floor()
            for (i in 0 until 5) fixed(T.WOOD_WALL, 80.0 + i * 64, 160.0)
            fixed(T.BALLOON, 96.0, 320.0)
            fixed(T.WOOD_WALL, 272.0, 224.0)
            val sw = fixed(T.SWITCH, 480.0, 344.0)
            val fan = fixed(T.FAN, 32.0, 176.0)
            wire(sw, fan)
            tray(T.CANDLE, 1)
            tray(T.BASEBALL, 1)
            solve(T.CANDLE, 280.0, 192.0)
            solve(T.BASEBALL, 488.0, 120.0)
            goal = Goal.PopAllBalloons
            hint = "The ball turns on the fan, the fan blows the balloon, the candle pops it."
        },
        level("l34", "Slam dunk", "Get the ball through the hoop") {
            floor()
            fixed(T.SEESAW, 160.0, 352.0)
            val hoop = fixed(T.HOOP, 32.0, 256.0, flipped = true)
            tray(T.BASKETBALL, 1)
            tray(T.BOWLING_BALL, 1)
            solve(T.BASKETBALL, 164.0, 340.0)
            solve(T.BOWLING_BALL, 216.0, 40.0)
            goal = Goal.BallInto(hoop)
            hint = "Basketball on the low end, bowling ball high above the other end."
            timeLimit = 20.0
        },
        level("l35", "Free Mort", "Get Mort to the cheese") {
            floor()
            val p1 = fixed(T.PULLEY, 176.0, 24.0)
            val p2 = fixed(T.PULLEY, 432.0, 24.0)
            val cage = fixed(T.CAGE, 160.0, 328.0)
            fixed(T.MOUSE, 176.0, 368.0, flipped = true)
            val bucket = fixed(T.BUCKET, 416.0, 200.0)
            rope(cage, bucket, p1, p2)
            fixed(T.CHEESE, 40.0, 368.0)
            tray(T.BOWLING_BALL, 1)
            solve(T.BOWLING_BALL, 424.0, 56.0)
            goal = Goal.MouseEatsCheese
            hint = "Something heavy in the bucket will pull the cage up."
            timeLimit = 25.0
        },
        level("l36", "Power up", "Get the ball to the star") {
            floor()
            val sw = fixed(T.SWITCH, 120.0, 344.0)
            val motor = fixed(T.MOTOR, 200.0, 344.0)
            wire(sw, motor)
            val belt = fixed(T.CONVEYOR, 280.0, 360.0)
            belt(motor, belt)
            fixed(T.BOWLING_BALL, 296.0, 328.0)
            val star = fixed(T.STAR, 520.0, 352.0)
            tray(T.BASEBALL, 1)
            solve(T.BASEBALL, 128.0, 120.0)
            goal = Goal.Activate(star)
            hint = "The switch powers the motor, the motor drives the belt."
        },
        level("l37", "Belt it", "Get the ball to the star") {
            floor()
            val outlet = fixed(T.OUTLET, 96.0, 352.0)
            val motor = fixed(T.MOTOR, 136.0, 344.0)
            wire(outlet, motor)
            val conveyor = fixed(T.CONVEYOR, 232.0, 360.0, needsPower = true)
            fixed(T.BOWLING_BALL, 248.0, 328.0)
            val star = fixed(T.STAR, 520.0, 352.0)
            tray(T.BELT, 1)
            solveBelt(motor, conveyor)
            goal = Goal.Activate(star)
            hint = "The belt is stuck. Tap the belt tool, then the motor, then the conveyor."
        },
        level("l38", "Wire it", "Get the ball to the star") {
            floor()
            val sw = fixed(T.SWITCH, 120.0, 344.0)
            val fan = fixed(T.FAN, 336.0, 344.0, needsPower = true)
            fixed(T.TENNIS_BALL, 400.0, 368.0)
            val star = fixed(T.STAR, 592.0, 352.0)
            tray(T.WIRE, 1)
            tray(T.BASEBALL, 1)
            solveWire(sw, fan)
            solve(T.BASEBALL, 128.0, 120.0)
            goal = Goal.Activate(star)
            hint = "The fan needs power: wire it to the switch, then drop the ball on the switch."
        },
        level("l39", "Hang the bucket", "Get the ball into the bucket") {
            floor()
            fixed(T.WOOD_WALL, 40.0, 200.0)
            fixed(T.INCLINE, 40.0, 168.0)
            fixed(T.BASKETBALL, 48.0, 136.0)
            val hook = fixed(T.HOOK, 136.0, 0.0)
            val bucket = fixed(T.BUCKET, 120.0, 240.0)
            tray(T.ROPE, 1)
            solveRope(hook, bucket)
            goal = Goal.BallInto(bucket)
            hint = "The bucket will fall! Tie it to the hook with the rope."
        },
        level("l40", "Pull the seesaw", "Get the ball to the star") {
            floor()
            val seesaw = fixed(T.SEESAW, 200.0, 352.0)
            fixed(T.BASKETBALL, 208.0, 320.0)
            val p1 = fixed(T.PULLEY, 200.0, 24.0)
            val p2 = fixed(T.PULLEY, 424.0, 24.0)
            val bucket = fixed(T.BUCKET, 408.0, 200.0)
            val star = fixed(T.STAR, 120.0, 224.0)
            tray(T.ROPE, 1)
            tray(T.BOWLING_BALL, 1)
            solveRope(seesaw, bucket, p1, p2)
            solve(T.BOWLING_BALL, 416.0, 56.0)
            goal = Goal.Activate(star)
            hint = "Tie the seesaw to the bucket over the pulleys, then drop the heavy ball in the bucket."
            timeLimit = 20.0
        },
    )

    val demo: Level = levels[11]
    /** Solutions worth watching on the title screen. */
    val demos: List<Level> = listOf("l12", "l18", "l28", "l34", "l25", "l14").map { id -> levels.first { it.id == id } }
}
