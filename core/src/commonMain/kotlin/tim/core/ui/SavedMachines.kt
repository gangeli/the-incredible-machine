package tim.core.ui

import tim.core.game.BoardCodec
import tim.core.game.Levels
import tim.core.game.MachineNamer

/**
 * The player's free-play creations. The build in progress is autosaved as the "current" machine so
 * free play always resumes where it left off; Save keeps a named copy that the gallery lists.
 */
class SavedMachines(private val storage: Storage) {
    class Entry(val id: Int, val name: String, val encoded: String)

    /** Autosaved free-play board (encoded), or null when nothing has been built yet. */
    var current: String?
        get() = storage.get("freeplay:current")?.takeIf { it.isNotEmpty() }
        set(v) = storage.put("freeplay:current", v ?: "")

    /** Which saved machine the current build came from, if any. */
    var currentId: Int?
        get() = storage.get("freeplay:currentId")?.toIntOrNull()
        set(v) = storage.put("freeplay:currentId", v?.toString() ?: "")

    private fun ids(): List<Int> = storage.get("machines:index")?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
    private fun writeIds(ids: List<Int>) = storage.put("machines:index", ids.joinToString(","))

    fun list(): List<Entry> = ids().mapNotNull { id -> storage.get("machines:$id")?.let { Entry(id, nameOf(id), it) } }
    fun get(id: Int): Entry? = storage.get("machines:$id")?.takeIf { it.isNotEmpty() }?.let { Entry(id, nameOf(id), it) }
    fun nameOf(id: Int): String = storage.get("machines:$id:name")?.takeIf { it.isNotEmpty() } ?: "Machine $id"

    /** Stores [encoded] under [id], or under a fresh id when null; returns the id used. */
    fun save(id: Int?, encoded: String): Int {
        val useId = id ?: run {
            val next = (storage.get("machines:next")?.toIntOrNull() ?: 1)
            storage.put("machines:next", (next + 1).toString())
            next
        }
        storage.put("machines:$useId", encoded)
        if (useId !in ids()) writeIds(ids() + useId)
        // machines name themselves after what they are made of until the player picks a name
        if (storage.get("machines:$useId:named") != "1") {
            val free = Levels.freeform()
            val board = runCatching { BoardCodec.decode(encoded, free.fixed, free.fixedLinks) }.getOrNull()
            if (board != null) storage.put("machines:$useId:name", unique(MachineNamer.name(board), useId))
        }
        return useId
    }

    /** The player's own name for a machine; blank names are ignored. */
    fun rename(id: Int, name: String) {
        if (name.isBlank()) return
        storage.put("machines:$id:name", name.trim().take(MachineNamer.MAX_LENGTH))
        storage.put("machines:$id:named", "1")
    }

    /** [base], or "[base] II", "[base] III"... when another machine already has that name. */
    private fun unique(base: String, id: Int): String {
        val taken = ids().filter { it != id }.map { nameOf(it) }.toSet()
        if (base !in taken) return base
        val numerals = listOf("II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")
        var n = 0
        while (true) {
            val suffix = if (n < numerals.size) numerals[n] else (n + 2).toString()
            val room = MachineNamer.MAX_LENGTH - suffix.length - 1
            var short = base.take(room)
            if (short.length < base.length && !base[short.length].isWhitespace() && short.contains(' ')) short = short.substringBeforeLast(' ')
            val candidate = "${short.trimEnd()} $suffix"
            if (candidate !in taken) return candidate
            n++
        }
    }

    fun delete(id: Int) {
        storage.put("machines:$id", "")
        writeIds(ids().filter { it != id })
        if (currentId == id) currentId = null
    }
}
