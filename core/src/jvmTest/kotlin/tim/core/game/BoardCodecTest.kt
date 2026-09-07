package tim.core.game

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tim.core.game.PartType as T

class BoardCodecTest {
    @Test
    fun `every part type, flips, rotations, power flags and links survive a round trip`() {
        val free = Levels.freeform()
        val board = Board(free.fixed, free.fixedLinks)
        T.values().forEachIndexed { i, t -> board.playerParts.add(Placement(t, i * 8.0, 100.0 + (i % 3) * 4.0, flipped = i % 2 == 0, rotation = if (t.rotatable) i % 4 else 0, needsPower = i % 5 == 0)) }
        val n = board.fixed.size
        val pulley = n + T.values().indexOf(T.PULLEY)
        board.playerLinks.add(Link(LinkKind.ROPE, n + T.values().indexOf(T.BUCKET), n + T.values().indexOf(T.CAGE), listOf(pulley), 12.5))
        board.playerLinks.add(Link(LinkKind.BELT, n + T.values().indexOf(T.MOTOR), n + T.values().indexOf(T.CONVEYOR)))
        board.playerLinks.add(Link(LinkKind.WIRE, n + T.values().indexOf(T.SWITCH), n + T.values().indexOf(T.FAN)))
        val text = BoardCodec.encode(board)
        val back = BoardCodec.decode(text, free.fixed, free.fixedLinks)
        assertEquals(board.playerParts, back.playerParts)
        assertEquals(board.playerLinks, back.playerLinks)
        assertEquals(text, BoardCodec.encode(back), "encoding is stable")
    }

    @Test
    fun `garbage and stale saves decode to what can be understood`() {
        val free = Levels.freeform()
        assertTrue(BoardCodec.decode("", free.fixed).playerParts.isEmpty())
        val b = BoardCodec.decode("BASKETBALL 8 16;NOT_A_PART 1 2;INCLINE x y;WOOD_WALL 64 200 0 1|ROPE 0 999;WIRE 8 9 0", free.fixed)
        assertEquals(listOf(Placement(T.BASKETBALL, 8.0, 16.0), Placement(T.WOOD_WALL, 64.0, 200.0, rotation = 1)), b.playerParts)
        assertEquals(listOf(Link(LinkKind.WIRE, 8, 9)), b.playerLinks, "links pointing past the board are dropped")
    }
}
