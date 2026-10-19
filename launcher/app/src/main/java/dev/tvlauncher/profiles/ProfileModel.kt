package dev.tvlauncher.profiles

import dev.tvlauncher.system.PinRules
import org.json.JSONArray
import org.json.JSONObject

/**
 * One person on this TV: a name, an avatar colour (the initial is drawn on that disc) and an
 * optional four-digit PIN, kept only as a salted hash ([PinRules]). Everything else a person owns
 * (favorites, order, hidden apps, look, wallpaper, home layout, room lighting, Now Playing) lives
 * in that profile's own copy of the preference files ([ProfileStores]).
 */
data class Profile(
    val id: String,
    val name: String,
    val color: Int = 0,
    val pinHash: String? = null,
    val pinSalt: String? = null
) {
    val hasPin: Boolean get() = pinHash != null && pinSalt != null

    /** The avatar glyph: the first letter or digit of the name, upper-cased ("?" when there is none). */
    val initial: String get() = ProfileModel.initialOf(name)
}

/** Every profile, the one in use, and whether Home asks "Who's watching?" when it starts. */
data class ProfileState(
    val profiles: List<Profile>,
    val activeId: String,
    val askOnStart: Boolean = false
) {
    val active: Profile get() = profiles.firstOrNull { it.id == activeId } ?: profiles.first()

    fun byId(id: String): Profile? = profiles.firstOrNull { it.id == id }

    /** The top-bar avatar chip shows only when there is someone to switch to. */
    val showChip: Boolean get() = profiles.size > 1
}

/**
 * Pure profile rules (no Android types): create, rename, recolour, delete (never the last one),
 * switch, PIN set/check, and a tolerant JSON form. Every edit returns a [Result]; nothing throws.
 */
object ProfileModel {
    /** The profile that owns the launcher's original (pre-profiles) preference files. */
    const val HOME_ID = "home"
    const val HOME_NAME = "Home"
    const val MAX_PROFILES = 5
    const val MAX_NAME = 16

    /** Avatar discs: calm, distinct colours that read well on the dark glass. */
    val COLORS = listOf(
        0xFF4F8CFF.toInt(), 0xFFFF7A59.toInt(), 0xFF3DD68C.toInt(),
        0xFFB57BFF.toInt(), 0xFFFFC145.toInt(), 0xFF2EC4D6.toInt()
    )
    val COLOR_NAMES = listOf("Blue", "Coral", "Green", "Violet", "Amber", "Teal")

    private val ID = Regex("^[a-z0-9]{1,12}$")

    sealed class Result {
        data class Ok(val state: ProfileState, val profile: Profile? = null) : Result()
        data class Error(val message: String) : Result()
    }

    /** A fresh install (or the first run after this update): one profile, Home, using the existing files. */
    fun initial(): ProfileState = ProfileState(listOf(Profile(HOME_ID, HOME_NAME, 0)), HOME_ID, askOnStart = false)

    fun isValidId(id: String): Boolean = ID.matches(id)

    fun colorOf(p: Profile): Int = COLORS[Math.floorMod(p.color, COLORS.size)]

    fun colorName(index: Int): String = COLOR_NAMES[Math.floorMod(index, COLOR_NAMES.size)]

    fun initialOf(name: String): String =
        name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"

    /** Dark text on the light discs (amber, green, teal), white on the others. */
    fun glyphIsDark(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.2126 * r + 0.7152 * g + 0.0722 * b) > 150
    }

    /** Trimmed, inner whitespace collapsed, at most [MAX_NAME] characters. */
    fun cleanName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME).trim()

    /** Null when [raw] is a usable name for a profile other than [exceptId]; otherwise one friendly line. */
    fun nameProblem(state: ProfileState, raw: String, exceptId: String? = null): String? {
        val n = cleanName(raw)
        if (n.isEmpty()) return "Type a name."
        if (n.none { it.isLetterOrDigit() }) return "Use at least one letter or number."
        if (state.profiles.any { it.id != exceptId && it.name.equals(n, ignoreCase = true) }) return "That name is already used."
        return null
    }

    /** The first avatar colour nobody uses yet (cycling when all are taken). */
    fun nextColor(state: ProfileState): Int {
        val used = state.profiles.map { Math.floorMod(it.color, COLORS.size) }.toSet()
        return COLORS.indices.firstOrNull { it !in used } ?: (state.profiles.size % COLORS.size)
    }

    /** A new id not used yet, built from [random] (lowercase letters and digits). */
    fun newId(state: ProfileState, random: () -> Int): String {
        val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
        repeat(64) {
            val id = "p" + (1..6).map { alphabet[Math.floorMod(random(), alphabet.length)] }.joinToString("")
            if (state.byId(id) == null) return id
        }
        var i = 2
        while (state.byId("p$i") != null) i++
        return "p$i"
    }

    fun create(state: ProfileState, rawName: String, id: String, color: Int = nextColor(state)): Result {
        if (state.profiles.size >= MAX_PROFILES) return Result.Error("Up to $MAX_PROFILES profiles.")
        if (!isValidId(id) || state.byId(id) != null) return Result.Error("Could not add the profile.")
        nameProblem(state, rawName)?.let { return Result.Error(it) }
        val p = Profile(id, cleanName(rawName), Math.floorMod(color, COLORS.size))
        return Result.Ok(state.copy(profiles = state.profiles + p), p)
    }

    fun rename(state: ProfileState, id: String, rawName: String): Result {
        val p = state.byId(id) ?: return Result.Error("That profile is gone.")
        nameProblem(state, rawName, exceptId = id)?.let { return Result.Error(it) }
        val n = p.copy(name = cleanName(rawName))
        return Result.Ok(replace(state, n), n)
    }

    fun recolor(state: ProfileState, id: String, color: Int): Result {
        val p = state.byId(id) ?: return Result.Error("That profile is gone.")
        val n = p.copy(color = Math.floorMod(color, COLORS.size))
        return Result.Ok(replace(state, n), n)
    }

    /** The last profile stays. Deleting the one in use switches to the first remaining one. */
    fun delete(state: ProfileState, id: String): Result {
        if (state.byId(id) == null) return Result.Error("That profile is gone.")
        if (state.profiles.size <= 1) return Result.Error("The last profile stays.")
        val rest = state.profiles.filter { it.id != id }
        val active = if (state.activeId == id) rest.first().id else state.activeId
        return Result.Ok(state.copy(profiles = rest, activeId = active))
    }

    fun switchTo(state: ProfileState, id: String): Result {
        val p = state.byId(id) ?: return Result.Error("That profile is gone.")
        return Result.Ok(state.copy(activeId = id), p)
    }

    fun setPin(state: ProfileState, id: String, pin: String, salt: String): Result {
        val p = state.byId(id) ?: return Result.Error("That profile is gone.")
        if (!PinRules.valid(pin)) return Result.Error("Use four digits.")
        if (salt.isBlank()) return Result.Error("Could not save the PIN.")
        val n = p.copy(pinHash = PinRules.hash(pin, salt), pinSalt = salt)
        return Result.Ok(replace(state, n), n)
    }

    fun clearPin(state: ProfileState, id: String): Result {
        val p = state.byId(id) ?: return Result.Error("That profile is gone.")
        val n = p.copy(pinHash = null, pinSalt = null)
        return Result.Ok(replace(state, n), n)
    }

    /** True when [p] has no PIN, or [pin] is its PIN. */
    fun pinOk(p: Profile, pin: String?): Boolean =
        !p.hasPin || (pin != null && PinRules.matches(pin, p.pinSalt!!, p.pinHash))

    /** "Ask on start" only matters with two or more profiles. */
    fun shouldAsk(state: ProfileState): Boolean = state.askOnStart && state.profiles.size > 1

    private fun replace(state: ProfileState, p: Profile) =
        state.copy(profiles = state.profiles.map { if (it.id == p.id) p else it })

    // ---- JSON -----------------------------------------------------------------------------------

    fun toJson(state: ProfileState): String {
        val arr = JSONArray()
        for (p in state.profiles) {
            val o = JSONObject().put("id", p.id).put("name", p.name).put("color", p.color)
            if (p.hasPin) o.put("pinHash", p.pinHash).put("pinSalt", p.pinSalt)
            arr.put(o)
        }
        return JSONObject().put("v", 1).put("profiles", arr).put("active", state.activeId)
            .put("askOnStart", state.askOnStart).toString()
    }

    /**
     * Tolerant reader: missing or broken JSON gives [initial] (so an existing install becomes the
     * single "Home" profile with all of its settings untouched). Bad entries are dropped, duplicate
     * ids keep the first, names are cleaned, at most [MAX_PROFILES] are kept, and an unknown active
     * id falls back to the first profile.
     */
    fun fromJson(json: String?): ProfileState {
        if (json.isNullOrBlank()) return initial()
        val o = try { JSONObject(json) } catch (e: Exception) { return initial() }
        val arr = o.optJSONArray("profiles") ?: return initial()
        val out = ArrayList<Profile>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val id = e.optString("id")
            if (!isValidId(id) || out.any { it.id == id }) continue
            val name = cleanName(e.optString("name")).ifEmpty { if (id == HOME_ID) HOME_NAME else "Profile ${out.size + 1}" }
            val hash = e.optString("pinHash").takeIf { it.matches(Regex("^[0-9a-f]{64}$")) }
            val salt = e.optString("pinSalt").takeIf { it.isNotBlank() && hash != null }
            out += Profile(id, name, Math.floorMod(e.optInt("color", 0), COLORS.size), if (salt != null) hash else null, salt)
            if (out.size >= MAX_PROFILES) break
        }
        if (out.isEmpty()) return initial()
        val active = o.optString("active").takeIf { a -> out.any { it.id == a } } ?: out.first().id
        return ProfileState(out, active, o.optBoolean("askOnStart", false))
    }
}
