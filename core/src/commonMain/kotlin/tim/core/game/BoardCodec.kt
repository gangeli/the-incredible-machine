package tim.core.game

/**
 * Turns the player's part of a board into a short text and back, for saving creations.
 * Format: parts as `TYPE x y flipped rotation needsPower` joined by `;`, then `|`, then links as
 * `KIND from to slack via...` joined by `;`. Unknown tokens are skipped so old saves keep loading.
 */
object BoardCodec {
    fun encode(board: Board): String {
        val parts = board.playerParts.joinToString(";") { p -> "${p.type.name} ${n(p.x)} ${n(p.y)} ${if (p.flipped) 1 else 0} ${p.rotation} ${if (p.needsPower) 1 else 0}" }
        val links = board.playerLinks.joinToString(";") { l -> (listOf(l.kind.name, l.from.toString(), l.to.toString(), n(l.slack)) + l.via.map { it.toString() }).joinToString(" ") }
        return "$parts|$links"
    }

    fun decode(text: String, fixed: List<Placement>, fixedLinks: List<Link> = emptyList()): Board {
        val board = Board(fixed, fixedLinks)
        val bar = text.indexOf('|')
        val partText = if (bar < 0) text else text.substring(0, bar)
        val linkText = if (bar < 0) "" else text.substring(bar + 1)
        for (chunk in partText.split(';')) {
            val f = chunk.trim().split(' ').filter { it.isNotEmpty() }
            if (f.size < 3) continue
            val type = PartType.values().firstOrNull { it.name == f[0] } ?: continue
            val x = f[1].toDoubleOrNull() ?: continue
            val y = f[2].toDoubleOrNull() ?: continue
            val flipped = f.getOrNull(3) == "1"
            val rotation = f.getOrNull(4)?.toIntOrNull()?.coerceIn(0, 3) ?: 0
            val needsPower = f.getOrNull(5) == "1"
            board.playerParts.add(Placement(type, x, y, flipped, rotation, needsPower))
        }
        val count = board.all.size
        for (chunk in linkText.split(';')) {
            val f = chunk.trim().split(' ').filter { it.isNotEmpty() }
            if (f.size < 3) continue
            val kind = LinkKind.values().firstOrNull { it.name == f[0] } ?: continue
            val from = f[1].toIntOrNull() ?: continue
            val to = f[2].toIntOrNull() ?: continue
            val slack = f.getOrNull(3)?.toDoubleOrNull() ?: 0.0
            val via = f.drop(4).mapNotNull { it.toIntOrNull() }
            if ((listOf(from, to) + via).any { it < 0 || it >= count }) continue
            board.playerLinks.add(Link(kind, from, to, via, slack))
        }
        return board
    }

    private fun n(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
