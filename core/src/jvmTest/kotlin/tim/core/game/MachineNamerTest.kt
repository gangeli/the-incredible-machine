package tim.core.game

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MachineNamerTest {
    private fun board(vararg types: PartType, links: List<LinkKind> = emptyList()) = MachineNamer.name(types.toList(), links)

    @Test
    fun `names are stable, short and depend on the mix of parts rather than positions`() {
        val free = Levels.freeform()
        val a = BoardCodec.decode("BASKETBALL 8 16|", free.fixed, free.fixedLinks)
        val moved = BoardCodec.decode("BASKETBALL 200 100|", free.fixed, free.fixedLinks)
        assertEquals(MachineNamer.name(a), MachineNamer.name(moved))
        assertEquals(MachineNamer.name(a), MachineNamer.name(a))
        assertNotEquals(board(PartType.BASKETBALL), board(PartType.BASKETBALL, PartType.BALLOON))
        val all = PartType.values().filter { !it.isTool }
        for (n in 1..all.size) {
            val name = MachineNamer.name(all.take(n))
            assertTrue(name.length in 3..MachineNamer.MAX_LENGTH, name)
            val words = name.split(' ').map { it.lowercase().removeSuffix("s").removeSuffix("'") }
            for (i in 1 until words.size) assertNotEquals(words[i - 1], words[i], "doubled word in '$name'")
        }
    }

    @Test
    fun `the subject is what the build is mostly made of and bricks never upstage anything`() {
        assertTrue(board(PartType.BASKETBALL).contains("Basketball"), board(PartType.BASKETBALL))
        assertTrue(board(PartType.BALLOON, PartType.BALLOON, PartType.BRICK_WALL, PartType.BRICK_WALL, PartType.BRICK_WALL).contains("Balloon"))
        val bricks = board(PartType.BRICK_WALL, PartType.BRICK_WALL, PartType.BRICK_WALL, PartType.BRICK_WALL, PartType.BRICK_WALL, PartType.BRICK_WALL, PartType.MOUSE)
        assertTrue(bricks.contains("Mort") || bricks.contains("Adventure") || bricks.contains("Escape") || bricks.contains("Scamper"), bricks)
        assertFalse(bricks.contains("Brick"), bricks)
        assertTrue(board(PartType.BRICK_WALL, PartType.BRICK_WALL).contains("Brick"), "only bricks: ${board(PartType.BRICK_WALL, PartType.BRICK_WALL)}")
        assertTrue(board(PartType.INCLINE).contains("Ramp"))
    }

    @Test
    fun `combinations pick the noun`() {
        val cheese = board(PartType.MOUSE, PartType.CHEESE, PartType.INCLINE)
        assertTrue(listOf("Cheese Express", "Snack Run", "Cheese Chase").any { cheese.contains(it) }, cheese)
        assertTrue(cheese.startsWith("Mort's") || cheese.startsWith("Mort and"), "the mouse owns his machine: $cheese")
        val boom = board(PartType.ROCKET, PartType.CANDLE, PartType.BOWLING_BALL)
        assertTrue(listOf("Blaster", "Kaboom", "Launcher", "Fireworks").any { boom.contains(it) }, boom)
        val fly = board(PartType.BALLOON, PartType.FAN, PartType.OUTLET)
        assertTrue(listOf("Flyer", "Breeze", "Floater", "Whoosh").any { fly.contains(it) }, fly)
        val hoist = board(PartType.BUCKET, PartType.PULLEY, PartType.BOWLING_BALL, links = listOf(LinkKind.ROPE))
        assertTrue(listOf("Hoist", "Lift", "Elevator").any { hoist.contains(it) }, hoist)
        val empty = MachineNamer.name(emptyList())
        assertTrue(empty.contains("Empty"), empty)
    }

    @Test
    fun `a variety of builds get a variety of names`() {
        val names = HashSet<String>()
        val types = PartType.values().filter { !it.isTool }
        for (i in types.indices) for (j in i + 1 until types.size) names += board(types[i], types[j])
        val pairs = types.size * (types.size - 1) / 2
        assertTrue(names.size > pairs / 3, "${names.size} distinct names for $pairs pairs")
    }
}
