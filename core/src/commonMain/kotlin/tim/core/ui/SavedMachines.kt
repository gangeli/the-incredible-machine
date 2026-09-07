package tim.core.ui

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
        return useId
    }

    fun rename(id: Int, name: String) { if (name.isNotBlank()) storage.put("machines:$id:name", name.trim().take(24)) }

    fun delete(id: Int) {
        storage.put("machines:$id", "")
        writeIds(ids().filter { it != id })
        if (currentId == id) currentId = null
    }
}
