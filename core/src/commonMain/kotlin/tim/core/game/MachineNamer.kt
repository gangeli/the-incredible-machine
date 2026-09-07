package tim.core.game

import kotlin.math.abs

/**
 * Invents a name for a free-play creation from what it is built of.
 *
 * The grammar is `[adjective] [subject] [noun]`, where the subject is the part the build is mostly
 * about (frequency weighted by how interesting a part is: creatures beat balls beat bricks), the
 * noun comes from the combination of parts (a mouse and cheese make a "Cheese Express", balloons
 * and fans a "Flyer", a rocket a "Blaster") and adjectives lean on the contents too (seesaws are
 * "Wobbly", fans "Whirly"). Choices are hashed from the mix of parts, so the same build always gets
 * the same name and a new part can earn a new one. Names fit in [MAX_LENGTH] characters.
 */
object MachineNamer {
    const val MAX_LENGTH = 24

    private val plainAdjectives = listOf("Great", "Amazing", "Incredible", "Marvellous", "Silly", "Mighty", "Super", "Fantastic", "Wonky", "Nifty", "Clever", "Dizzy")
    private val generic = listOf("Contraption", "Machine", "Gizmo", "Whatsit", "Thingamajig", "Gadget", "Invention")

    /** The word a part goes by in a name. */
    fun word(t: PartType): String = when (t) {
        PartType.BOWLING_BALL -> "Bowling Ball"
        PartType.BASKETBALL -> "Basketball"
        PartType.BASEBALL -> "Baseball"
        PartType.TENNIS_BALL -> "Tennis Ball"
        PartType.SUPER_BALL -> "Super Ball"
        PartType.CANNONBALL -> "Cannonball"
        PartType.BALLOON -> "Balloon"
        PartType.BRICK_WALL, PartType.SMALL_WALL -> "Brick"
        PartType.WOOD_WALL -> "Plank"
        PartType.INCLINE, PartType.STEEP_INCLINE -> "Ramp"
        PartType.SEESAW -> "Seesaw"
        PartType.TRAMPOLINE -> "Trampoline"
        PartType.CONVEYOR -> "Conveyor"
        PartType.FAN -> "Fan"
        PartType.BUCKET -> "Bucket"
        PartType.CAGE -> "Cage"
        PartType.CANDLE -> "Candle"
        PartType.CANNON -> "Cannon"
        PartType.DYNAMITE -> "Dynamite"
        PartType.ROCKET -> "Rocket"
        PartType.BUMPER -> "Bumper"
        PartType.BOXING_GLOVE -> "Boxing Glove"
        PartType.SCISSORS -> "Scissors"
        PartType.BELLOWS -> "Bellows"
        PartType.PULLEY -> "Pulley"
        PartType.HOOK -> "Hook"
        PartType.MOTOR -> "Motor"
        PartType.MOUSE -> "Mort"
        PartType.CAT -> "Pokey"
        PartType.CHEESE -> "Cheese"
        PartType.SWITCH -> "Switch"
        PartType.OUTLET -> "Plug"
        PartType.FLASHLIGHT -> "Flashlight"
        PartType.HOOP -> "Hoop"
        PartType.BELL -> "Bell"
        PartType.STAR -> "Star"
        PartType.ROPE -> "Rope"
        PartType.BELT -> "Belt"
        PartType.WIRE -> "Wire"
    }

    /** How much a single part of this type pulls the name towards itself. */
    private fun interest(t: PartType): Int = when (t) {
        PartType.MOUSE, PartType.CAT -> 6
        PartType.ROCKET, PartType.CANNON, PartType.DYNAMITE -> 5
        PartType.CHEESE, PartType.BALLOON -> 5
        else -> when (t.category) {
            PartCategory.GOAL -> 5
            PartCategory.MACHINE -> 4
            PartCategory.TRIGGER -> 3
            PartCategory.BALL -> 2
            PartCategory.CREATURE -> 5
            PartCategory.LINK -> 1
            PartCategory.STRUCTURE -> 0
        }
    }

    fun name(board: Board): String = name(board.playerParts.map { it.type }, board.playerLinks.map { it.kind })

    fun name(types: List<PartType>, links: List<LinkKind> = emptyList()): String {
        val counts = types.groupingBy { it }.eachCount()
        val hash = hashOf(counts, links)
        fun pick(list: List<String>, salt: Int) = list[abs(mix(hash, salt)) % list.size]
        fun has(vararg ts: PartType) = ts.any { (counts[it] ?: 0) > 0 }
        val ropes = links.count { it == LinkKind.ROPE }

        if (types.isEmpty()) return fit(listOf("${pick(plainAdjectives, 1)} Empty ${pick(generic, 2)}", "Empty ${pick(generic, 2)}"))

        // the noun: what kind of thing this combination of parts makes
        val nouns: List<String> = when {
            has(PartType.MOUSE) && has(PartType.CHEESE) -> listOf("Cheese Express", "Snack Run", "Cheese Chase")
            has(PartType.CAT) && has(PartType.MOUSE) -> listOf("Cat Chase", "Pounce", "Great Escape")
            has(PartType.MOUSE) -> listOf("Adventure", "Escape", "Scamper")
            has(PartType.CAT) -> listOf("Prowl", "Pounce", "Cat Nap")
            has(PartType.ROCKET, PartType.CANNON, PartType.DYNAMITE) -> listOf("Blaster", "Kaboom", "Launcher", "Fireworks")
            has(PartType.BALLOON, PartType.FAN, PartType.BELLOWS) -> listOf("Flyer", "Breeze", "Floater", "Whoosh")
            has(PartType.BELL) -> listOf("Bell Ringer", "Ding-Dong", "Chime")
            has(PartType.HOOP) -> listOf("Slam Dunk", "Swish", "Hoop Shot")
            has(PartType.STAR) -> listOf("Star Catcher", "Star Shot", "Twinkle")
            ropes > 0 || has(PartType.PULLEY, PartType.BUCKET, PartType.CAGE, PartType.HOOK) -> listOf("Hoist", "Lift", "Elevator")
            has(PartType.MOTOR, PartType.CONVEYOR, PartType.SWITCH, PartType.OUTLET) -> listOf("Factory", "Assembly Line", "Power Plant")
            has(PartType.SEESAW, PartType.TRAMPOLINE, PartType.BUMPER, PartType.BOXING_GLOVE) -> listOf("Bouncer", "Flinger", "Catapult")
            has(PartType.CANDLE, PartType.FLASHLIGHT) -> listOf("Night Light", "Glow", "Lantern")
            has(PartType.SCISSORS) -> listOf("Snipper", "Chop Shop")
            types.any { it.isBall } -> listOf("Ball Run", "Rally", "Roller")
            else -> generic
        }
        // subjects: the parts the build is mostly made of, most telling first
        val ranked = counts.entries
            .map { (t, n) -> t to (if (t.category == PartCategory.STRUCTURE) minOf(n, 5) else interest(t) * 3 + minOf(n, 5) * 2) }
            .sortedWith(compareByDescending<Pair<PartType, Int>> { it.second }.thenBy { it.first.ordinal })
            .map { word(it.first) }
            .distinct()
        // "Basketball Ball Run" reads badly: prefer a noun that does not echo the main subject
        val noun = pick(nouns.filter { !clashes(ranked.first(), it) }.ifEmpty { nouns }, 3)

        // adjectives lean on the contents; a plain one now and then keeps things fresh
        val flavour = ArrayList<String>()
        if (types.size >= 12) flavour += listOf("Giant", "Tremendous", "Enormous")
        if (types.count { it.isBall } >= 3) flavour += "Bouncy"
        if (has(PartType.FAN, PartType.BELLOWS)) flavour += listOf("Whirly", "Windy")
        if (has(PartType.SEESAW, PartType.TRAMPOLINE)) flavour += listOf("Wobbly", "Springy")
        if (has(PartType.MOUSE)) flavour += listOf("Sneaky", "Nibbly")
        if (has(PartType.CAT)) flavour += listOf("Pouncy", "Purry")
        if (has(PartType.ROCKET, PartType.CANNON)) flavour += listOf("Speedy", "Zoomy")
        if (has(PartType.DYNAMITE)) flavour += listOf("Explosive", "Boomy")
        if (has(PartType.BALLOON)) flavour += listOf("Floaty", "Lofty")
        if (has(PartType.CANDLE)) flavour += "Flickery"
        if (has(PartType.MOTOR, PartType.CONVEYOR)) flavour += listOf("Busy", "Whirring")
        if (types.count { it.category == PartCategory.STRUCTURE } >= 4) flavour += "Sturdy"
        val adjective = if (flavour.isNotEmpty() && abs(mix(hash, 4)) % 3 != 0) pick(flavour, 5) else pick(plainAdjectives, 5)

        val nounWords = wordsOf(noun)
        val subjects = ranked.filter { !clashes(it, noun) }
        val subject = subjects.firstOrNull()
        // a partner subject: walls are too dull to share the billing ("Mort and Brick Adventure")
        val second = subjects.drop(1).firstOrNull { it != "Brick" && it != "Plank" && !clashes(it, subject ?: "") }
        val owner = when { has(PartType.MOUSE) -> "Mort's"; has(PartType.CAT) -> "Pokey's"; else -> null }
        val ownerFree = subjects.firstOrNull { it != "Mort" && it != "Pokey" }

        val candidates = ArrayList<String>()
        val template = abs(mix(hash, 6)) % 4
        if (subject == "Mort" || subject == "Pokey") {
            // the creature owns the machine: "Mort's Sneaky Snack Run"
            val ownerS = "$subject's"
            val ordered = listOf(
                "$ownerS $adjective $noun",
                if (second != null) "$subject and $second $noun" else "$ownerS $noun",
                if (second != null) "$ownerS $second $noun" else "$ownerS $adjective $noun",
                "$ownerS $noun",
            )
            for (i in 0 until 4) candidates += ordered[(template + i) % 4]
            candidates += "$ownerS $noun"
        } else if (subject != null) {
            val ordered = listOf(
                "$adjective $subject $noun",
                if (second != null) "$subject and $second $noun" else "The $subject $noun",
                if (owner != null && ownerFree != null && "Mort" !in nounWords && "Pokey" !in nounWords) "$owner $ownerFree $noun" else "The $adjective $subject $noun",
                "The $subject $noun",
            )
            for (i in 0 until 4) candidates += ordered[(template + i) % 4]
            candidates += "$subject $noun"
        }
        candidates += "$adjective $noun"
        candidates += "The $noun"
        candidates += noun
        return fit(candidates)
    }

    private fun fit(candidates: List<String>): String = candidates.firstOrNull { it.length <= MAX_LENGTH } ?: candidates.last().take(MAX_LENGTH).trimEnd()

    private fun wordsOf(s: String): Set<String> = s.split(' ', '-', '\'').filter { it.isNotEmpty() }.map { it.lowercase().removeSuffix("s") }.toSet()

    /** Two phrases clash when a word of one is (part of) a word of the other: "Basketball" and "Ball Run". */
    private fun clashes(a: String, b: String): Boolean {
        val wa = wordsOf(a); val wb = wordsOf(b)
        return wa.any { x -> wb.any { y -> x == y || (x.length >= 3 && y.length >= 3 && (x.contains(y) || y.contains(x))) } }
    }

    /**
     * Order-independent hash of the mix of parts and links, so moving things about keeps the name.
     * Counts are bucketed (one, a couple, several) so a fourth ball does not rename the machine.
     */
    private fun hashOf(counts: Map<PartType, Int>, links: List<LinkKind>): Int {
        var h = 17
        for (t in PartType.values()) { val n = counts[t] ?: 0; if (n > 0) h = h * 31 + t.ordinal * 7919 + minOf(n, 3) }
        for (k in LinkKind.values()) h = h * 31 + links.count { it == k }
        return h
    }

    private fun mix(h: Int, salt: Int): Int {
        var x = h * 1103515245 + salt * 12345 + 0x5bd1e995.toInt()
        x = x xor (x ushr 15); x *= 0x27d4eb2d; x = x xor (x ushr 13)
        return x
    }
}
