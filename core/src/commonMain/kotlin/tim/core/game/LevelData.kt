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
            val ball = fixed(T.BOWLING_BALL, 8.0, 224.0)
            val star = fixed(T.STAR, 560.0, 272.0)
            tray(T.WOOD_WALL, 1)
            solve(T.WOOD_WALL, 288.0, 304.0)
            goal = Goal.Touch(star, ball)
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
            val ball = fixed(T.BASKETBALL, 144.0, 96.0)
            val star = fixed(T.STAR, 144.0, 8.0)
            tray(T.TRAMPOLINE, 1)
            solve(T.TRAMPOLINE, 128.0, 360.0)
            goal = Goal.Touch(star, ball)
            hint = "A trampoline bounces the ball higher than where it started."
        },
        level("l06", "Mind the gap", "Get the ball to the star") {
            floor()
            fixed(T.WOOD_WALL, 0.0, 240.0)
            fixed(T.INCLINE, 0.0, 208.0)
            fixed(T.WOOD_WALL, 128.0, 240.0)
            fixed(T.WOOD_WALL, 192.0, 240.0)
            val ball = fixed(T.BOWLING_BALL, 8.0, 96.0)
            val star = fixed(T.STAR, 224.0, 208.0)
            wallV(256.0, 208.0, 240.0)
            tray(T.WOOD_WALL, 1)
            solve(T.WOOD_WALL, 64.0, 240.0)
            goal = Goal.Touch(star, ball)
            hint = "The ball rolls down the ramp but the shelf has a hole. Fill it with the plank."
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
            goal = Goal.TouchType(bell, T.CANNONBALL)
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
            val tennis = fixed(T.TENNIS_BALL, 400.0, 368.0)
            val star = fixed(T.STAR, 592.0, 352.0)
            tray(T.BASEBALL, 1)
            solve(T.BASEBALL, 128.0, 120.0)
            goal = Goal.Touch(star, tennis)
            hint = "Drop the baseball on the switch to turn on the fan."
        },
        level("l12", "Seesaw", "Get the baseball to the star") {
            floor()
            fixed(T.SEESAW, 160.0, 352.0)
            fixed(T.BOWLING_BALL, 216.0, 40.0)
            val star = fixed(T.STAR, 80.0, 208.0)
            tray(T.BASEBALL, 1)
            solve(T.BASEBALL, 168.0, 320.0)
            goal = Goal.TouchType(star, T.BASEBALL)
            hint = "Put the baseball on the low end of the seesaw. The bowling ball will fling it."
        },
        level("l13", "Swish", "Get the ball through the hoop") {
            floor()
            // the ball drops out of a chute in the ceiling; the hoop just has to be under it
            fixed(T.WOOD_WALL, 276.0, 0.0, rotation = 1)
            fixed(T.WOOD_WALL, 332.0, 0.0, rotation = 1)
            fixed(T.BASKETBALL, 296.0, 16.0)
            tray(T.HOOP, 1)
            solve(T.HOOP, 280.0, 232.0)
            goal = Goal.BallInto(fixed.size, T.BASKETBALL)
            hint = "The ball drops straight down. Put the hoop right under it."
        },
        level("l14", "Punch", "Get the ball to the star") {
            floor()
            fixed(T.BASEBALL, 136.0, 40.0)
            val ball = fixed(T.BASKETBALL, 200.0, 352.0)
            val star = fixed(T.STAR, 560.0, 352.0)
            tray(T.BOXING_GLOVE, 1)
            solve(T.BOXING_GLOVE, 128.0, 344.0)
            goal = Goal.Touch(star, ball)
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
            val balloon = fixed(T.BALLOON, 96.0, 320.0)
            val star = fixed(T.STAR, 272.0, 176.0)
            tray(T.FAN, 1)
            solve(T.FAN, 32.0, 176.0)
            goal = Goal.Touch(star, balloon)
            hint = "The balloon floats up to the ceiling. Blow it sideways with the fan."
        },
        level("l17", "Bounce back", "Get the ball to the star") {
            floor()
            fixed(T.INCLINE, 0.0, 352.0)
            val ball = fixed(T.BASKETBALL, 32.0, 320.0)
            val star = fixed(T.STAR, 0.0, 312.0)
            tray(T.BUMPER, 1)
            solve(T.BUMPER, 552.0, 352.0)
            goal = Goal.Touch(star, ball)
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
            val ball = fixed(T.BOWLING_BALL, 8.0, 32.0)
            fixed(T.WOOD_WALL, 128.0, 208.0)
            fixed(T.WOOD_WALL, 192.0, 208.0)
            floor(368.0, 640.0, 320.0)
            val star = fixed(T.STAR, 592.0, 288.0)
            tray(T.INCLINE, 1)
            tray(T.WOOD_WALL, 1)
            solve(T.INCLINE, 0.0, 64.0)
            solve(T.WOOD_WALL, 304.0, 320.0)
            goal = Goal.Touch(star, ball)
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
        level("l41", "Puff", "Pop the balloon") {
            floor()
            // the balloon floats up under a row of planks; a ball drops out of a chute on the left
            for (i in 0 until 4) fixed(T.WOOD_WALL, 200.0 + i * 64, 160.0)
            fixed(T.BALLOON, 200.0, 180.0)
            fixed(T.WOOD_WALL, 360.0, 224.0)
            fixed(T.WOOD_WALL, 108.0, 0.0, rotation = 1)
            fixed(T.WOOD_WALL, 164.0, 0.0, rotation = 1)
            fixed(T.BOWLING_BALL, 128.0, 16.0)
            tray(T.BELLOWS, 1)
            tray(T.CANDLE, 1)
            solve(T.BELLOWS, 112.0, 184.0)
            solve(T.CANDLE, 376.0, 192.0)
            goal = Goal.PopAllBalloons
            hint = "The bellows puff when something lands on them. Catch the ball with them, aim at the balloon, and put the candle on the shelf where the balloon will float."
        },
        level("l22", "On the moon", "Get the ball to the star") {
            gravity = 160.0
            floor()
            val ball = fixed(T.BOWLING_BALL, 144.0, 200.0)
            val star = fixed(T.STAR, 144.0, 24.0)
            tray(T.TRAMPOLINE, 1)
            solve(T.TRAMPOLINE, 128.0, 360.0)
            goal = Goal.Touch(star, ball)
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
            goal = Goal.TouchType(bell, T.CANNONBALL)
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
            val ball = fixed(T.BASKETBALL, 336.0, 352.0)
            val star = fixed(T.STAR, 552.0, 352.0)
            tray(T.CANDLE, 1)
            solve(T.CANDLE, 280.0, 352.0)
            goal = Goal.Touch(star, ball)
            hint = "The explosion will throw the ball. Light the fuse!"
        },
        level("l26", "Rocket mail", "Ring the bell") {
            floor()
            // a cannon on a shelf points at the bell; its fuse hangs off the back end
            fixed(T.WOOD_WALL, 320.0, 160.0)
            fixed(T.CANNON, 320.0, 120.0)
            val bell = fixed(T.BELL, 568.0, 112.0)
            tray(T.ROCKET, 1)
            tray(T.CANDLE, 1)
            solve(T.ROCKET, 312.0, 320.0)
            solve(T.CANDLE, 296.0, 352.0)
            goal = Goal.TouchType(bell, T.CANNONBALL)
            hint = "A candle lights the rocket, and the rocket's flame lights anything it flies past... like a fuse."
        },
        level("l27", "Belt up", "Get the ball into the bucket") {
            floor()
            // a conveyor never moves on its own: it needs a belt from a motor
            fixed(T.WOOD_WALL, 128.0, 256.0)
            val motor = fixed(T.MOTOR, 136.0, 216.0)
            fixed(T.WOOD_WALL, 232.0, 256.0)
            fixed(T.WOOD_WALL, 296.0, 256.0)
            val belt = fixed(T.CONVEYOR, 232.0, 232.0)
            fixed(T.BOWLING_BALL, 248.0, 200.0)
            val bucket = fixed(T.BUCKET, 436.0, 340.0)
            tray(T.BELT, 1)
            solveBelt(motor, belt)
            goal = Goal.BallInto(bucket)
            hint = "The conveyor is still. Tap the belt tool, then the motor, then the conveyor."
        },
        level("l28", "Ding dong", "Ring the bell") {
            floor()
            fixed(T.WOOD_WALL, 0.0, 168.0)
            val ball = fixed(T.BOWLING_BALL, 8.0, 104.0)
            fixed(T.WOOD_WALL, 224.0, 80.0)
            val bell = fixed(T.BELL, 232.0, 96.0)
            tray(T.INCLINE, 1)
            tray(T.TRAMPOLINE, 1)
            solve(T.INCLINE, 0.0, 136.0)
            solve(T.TRAMPOLINE, 152.0, 360.0)
            goal = Goal.Touch(bell, ball)
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
        level("l42", "Mort's big day", "Get Mort to the cheese") {
            floor(0.0, 224.0)
            floor(320.0, 640.0)
            fixed(T.MOUSE, 120.0, 368.0, flipped = true)
            fixed(T.CHEESE, 520.0, 368.0)
            fixed(T.CAT, 592.0, 352.0, flipped = true)
            tray(T.SMALL_WALL, 1)
            tray(T.BRICK_WALL, 1)
            tray(T.CAGE, 1)
            solve(T.SMALL_WALL, 80.0, 352.0, rotation = 1)
            solve(T.BRICK_WALL, 224.0, 384.0)
            solve(T.CAGE, 592.0, 328.0)
            goal = Goal.MouseEatsCheese
            hint = "Three jobs: turn Mort around, bridge the gap, and keep Pokey out of the way."
            timeLimit = 30.0
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
            fixed(T.BALLOON, 96.0, 180.0)
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
            val hoop = fixed(T.HOOP, 28.0, 256.0)
            tray(T.BASKETBALL, 1)
            tray(T.BOWLING_BALL, 1)
            solve(T.BASKETBALL, 164.0, 340.0)
            solve(T.BOWLING_BALL, 216.0, 40.0)
            goal = Goal.BallInto(hoop, T.BASKETBALL)
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
        level("l43", "Rube's big machine", "Get Mort to the cheese") {
            floor()
            val p1 = fixed(T.PULLEY, 176.0, 24.0)
            val p2 = fixed(T.PULLEY, 372.0, 24.0)
            val cage = fixed(T.CAGE, 160.0, 328.0)
            fixed(T.MOUSE, 176.0, 368.0, flipped = true)
            val bucket = fixed(T.BUCKET, 356.0, 200.0)
            rope(cage, bucket, p1, p2)
            fixed(T.CHEESE, 56.0, 368.0)
            fixed(T.CAT, 0.0, 352.0)
            // a stalled conveyor holds a heavy ball above the bucket; its motor is waiting for power
            val belt = fixed(T.CONVEYOR, 472.0, 160.0, flipped = true)
            fixed(T.BOWLING_BALL, 520.0, 128.0)
            val motor = fixed(T.MOTOR, 552.0, 120.0, flipped = true, needsPower = true)
            belt(motor, belt)
            val sw = fixed(T.SWITCH, 612.0, 344.0)
            tray(T.BASEBALL, 1)
            tray(T.WIRE, 1)
            tray(T.CAGE, 1)
            solve(T.BASEBALL, 616.0, 120.0)
            solveWire(sw, motor)
            solve(T.CAGE, 0.0, 328.0)
            goal = Goal.MouseEatsCheese
            hint = "Wire the switch to the motor, drop a ball on the switch, and keep Pokey away from the cheese."
            timeLimit = 30.0
        },
        level("l36", "Power up", "Get the ball to the star") {
            floor()
            val sw = fixed(T.SWITCH, 120.0, 344.0)
            val motor = fixed(T.MOTOR, 200.0, 344.0)
            wire(sw, motor)
            val belt = fixed(T.CONVEYOR, 280.0, 360.0)
            belt(motor, belt)
            val ball = fixed(T.BOWLING_BALL, 296.0, 328.0)
            val star = fixed(T.STAR, 520.0, 352.0)
            tray(T.BASEBALL, 1)
            solve(T.BASEBALL, 128.0, 120.0)
            goal = Goal.Touch(star, ball)
            hint = "The switch powers the motor, the motor drives the belt."
        },
        level("l37", "Which way round?", "Get the ball into the bucket") {
            floor()
            fixed(T.WOOD_WALL, 128.0, 256.0)
            fixed(T.WOOD_WALL, 232.0, 256.0)
            fixed(T.WOOD_WALL, 296.0, 256.0)
            val belt = fixed(T.CONVEYOR, 232.0, 232.0)
            fixed(T.BOWLING_BALL, 248.0, 200.0)
            val bucket = fixed(T.BUCKET, 436.0, 340.0)
            tray(T.MOTOR, 1)
            tray(T.BELT, 1)
            val motor = solve(T.MOTOR, 136.0, 216.0)
            solveBelt(motor, belt)
            goal = Goal.BallInto(bucket)
            hint = "Put the motor on the shelf and tie the belt. The belt runs the way the motor faces: tap the motor to flip it."
        },
        level("l38", "Wire it", "Get the ball to the star") {
            floor()
            val sw = fixed(T.SWITCH, 120.0, 344.0)
            val fan = fixed(T.FAN, 336.0, 344.0, needsPower = true)
            val tennis = fixed(T.TENNIS_BALL, 400.0, 368.0)
            val star = fixed(T.STAR, 592.0, 352.0)
            tray(T.WIRE, 1)
            tray(T.BASEBALL, 1)
            solveWire(sw, fan)
            solve(T.BASEBALL, 128.0, 120.0)
            goal = Goal.Touch(star, tennis)
            hint = "The fan needs power: wire it to the switch, then drop the ball on the switch."
        },
        level("l44", "Power chain", "Pop the balloon") {
            floor()
            val outlet = fixed(T.OUTLET, 24.0, 352.0)
            val motor = fixed(T.MOTOR, 56.0, 344.0, needsPower = true)
            val belt = fixed(T.CONVEYOR, 120.0, 296.0)
            fixed(T.BOWLING_BALL, 128.0, 264.0)
            val sw = fixed(T.SWITCH, 292.0, 344.0)
            val fan = fixed(T.FAN, 352.0, 176.0, needsPower = true)
            for (i in 0 until 4) fixed(T.WOOD_WALL, 384.0 + i * 64, 160.0)
            fixed(T.BALLOON, 400.0, 320.0)
            fixed(T.WOOD_WALL, 560.0, 224.0)
            fixed(T.CANDLE, 576.0, 192.0)
            tray(T.WIRE, 2)
            tray(T.BELT, 1)
            solveWire(outlet, motor)
            solveBelt(motor, belt)
            solveWire(sw, fan)
            goal = Goal.PopAllBalloons
            hint = "Power flows from the outlet: a wire to the motor, a belt to the conveyor, and a wire from the switch to the fan."
            timeLimit = 25.0
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
            val ball = fixed(T.BASKETBALL, 208.0, 320.0)
            val p1 = fixed(T.PULLEY, 200.0, 24.0)
            val p2 = fixed(T.PULLEY, 424.0, 24.0)
            val bucket = fixed(T.BUCKET, 408.0, 200.0)
            val star = fixed(T.STAR, 120.0, 224.0)
            tray(T.ROPE, 1)
            tray(T.BOWLING_BALL, 1)
            solveRope(seesaw, bucket, p1, p2)
            solve(T.BOWLING_BALL, 416.0, 56.0)
            goal = Goal.Touch(star, ball)
            hint = "Tie the seesaw to the bucket over the pulleys, then drop the heavy ball in the bucket."
            timeLimit = 20.0
        },
    )

    val demo: Level = levels[11]
    /** Solutions worth watching on the title screen. */
    val demos: List<Level> = listOf("l12", "l18", "l28", "l34", "l25", "l14").map { id -> levels.first { it.id == id } }
}
