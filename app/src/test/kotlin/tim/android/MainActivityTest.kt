package tim.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tim.core.ui.LevelSelectScreen
import tim.core.ui.PlayScreen
import tim.core.ui.TitleScreen
import java.io.File
import java.io.FileOutputStream

/**
 * Launches the real Activity under Robolectric with native graphics, renders frames through the
 * Android Canvas painter, and drives the game with synthetic touches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityTest {
    private val outDir = File(System.getProperty("tim.snapDir") ?: "build/snaps")

    private fun render(view: View, name: String): Bitmap {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        outDir.mkdirs()
        FileOutputStream(File(outDir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    private fun distinctColours(b: Bitmap): Int {
        val seen = HashSet<Int>()
        for (y in 0 until b.height step 8) for (x in 0 until b.width step 8) seen.add(b.getPixel(x, y))
        return seen.size
    }

    private fun tap(view: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, x, y, 0))
    }

    @Test
    fun activityRendersTitleAndNavigatesToAPuzzle() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val view = activity.findViewById<View>(android.R.id.content).let { (it as android.view.ViewGroup).getChildAt(0) } as GameView
        view.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1280, 800)
        val game = view.game
        // first frame
        render(view, "android-title")
        assertTrue(game.screen is TitleScreen)
        val title = render(view, "android-title")
        assertTrue("title screen should have many colours", distinctColours(title) > 20)
        // Play -> level select
        tap(view, 640f, 800f * 0.56f + 55f)
        render(view, "android-tick")
        assertTrue("expected level select, got ${game.screen}", game.screen is LevelSelectScreen)
        render(view, "android-levels")
        // first level tile
        tap(view, 200f, 190f)
        render(view, "android-tick")
        assertTrue("expected play screen, got ${game.screen}", game.screen is PlayScreen)
        val play = render(view, "android-play")
        assertTrue(distinctColours(play) > 20)
        // press the big play button at the bottom of the tray and make sure the machine runs
        val ps = game.screen as PlayScreen
        tap(view, ps.layout.tray.center.x.toFloat(), 800f - 70f)
        render(view, "android-tick")
        assertTrue("machine should be running", ps.running)
        val before = render(view, "android-running-0")
        // advance the simulation deterministically (real time barely passes between test frames)
        repeat(60) { game.update(1.0 / 30) }
        val after = render(view, "android-running-1")
        assertTrue("machine time should advance", ps.runTime > 1.0)
        var diff = 0
        for (y in 0 until after.height step 4) for (x in 0 until after.width step 4) if (before.getPixel(x, y) != after.getPixel(x, y)) diff++
        assertTrue("frames should differ once the ball moves (diff=$diff)", diff > 20)
        controller.pause().stop().destroy()
    }
}

/** Plays every puzzle's stored solution through the real Activity and Android Canvas painter, then taps Next. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AllLevelsOnAndroidTest {
    private val outDir = File(System.getProperty("tim.snapDir") ?: "build/snaps")

    private fun render(view: View, name: String? = null): Bitmap {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        if (name != null) { outDir.mkdirs(); FileOutputStream(File(outDir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        return bmp
    }

    private fun tap(view: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, x, y, 0))
    }

    @Test
    fun everyLevelSolvesRendersAndAdvances() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val view = activity.findViewById<View>(android.R.id.content).let { (it as android.view.ViewGroup).getChildAt(0) } as GameView
        view.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1280, 800)
        val game = view.game
        game.update(0.02)
        val levels = tim.core.game.Levels.all
        for ((i, level) in levels.withIndex()) {
            game.startLevel(i); game.update(0.02)
            val ps = game.screen as PlayScreen
            ps.board.playerParts.addAll(level.solution)
            ps.board.playerLinks.addAll(level.solutionLinks)
            render(view)
            val play = ps.playButtonRect()
            tap(view, play.center.x.toFloat(), play.center.y.toFloat())
            game.update(0.02)
            assertTrue("${level.id}: machine should run", ps.running)
            var t = 0.0
            var frames = 0
            while (!ps.won && t < level.timeLimit + 2) {
                game.update(1.0 / 60); t += 1.0 / 60
                if (++frames % 20 == 0) render(view)
            }
            assertTrue("${level.id}: not solved on Android", ps.won)
            render(view, if (i == 15) "android-l16-won" else null)
            val (_, next) = ps.winButtons()
            tap(view, next.center.x.toFloat(), next.center.y.toFloat())
            game.update(0.02)
            if (i + 1 < levels.size) {
                val s = game.screen
                assertTrue("${level.id}: Next should open level ${i + 2}, got $s", s is PlayScreen && s.levelIndex == i + 1)
                render(view, if (i == 15) "android-l17-start" else null)
            } else {
                assertTrue(game.screen is LevelSelectScreen)
            }
        }
        controller.pause().stop().destroy()
    }
}
