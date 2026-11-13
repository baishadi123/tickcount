package io.github.zzpby.tickcount.data

import io.github.zzpby.tickcount.domain.SortOrder
import org.json.JSONObject

/** What an import should do with what is already on the device. */
enum class ImportMode { REPLACE, MERGE }

/**
 * How reading a backup file went, before anything has been applied.
 *
 * Reading is its own step rather than the first half of applying, because a file has to be
 * opened, and a password accepted, before there is any sense in asking what to do with it.
 */
sealed interface ReadOutcome {
    /**
     * The file opened, and holds a backup — together with what putting it back would do
     * to what is on the device now, which is what the screen asks about next.
     */
    data class Read(val backup: Backup, val preview: ImportPreview) : ReadOutcome

    /** The file is encrypted and the password did not open it. */
    data object WrongPassword : ReadOutcome

    /** The file is not a TickCount backup at all. */
    data object NotABackup : ReadOutcome

    /** A backup, but one that cannot be read — truncated, or written by something else. */
    data object Unreadable : ReadOutcome
}

/** What an import would do, counted before it is allowed to do it. */
data class ImportPreview(
    /** Undecided: what the file would bring in that is not here already, or that is. */
    val added: Int,
    val overwritten: Int,
    /** What is on the device now, which replacing would throw away. */
    val discarded: Int,
)

/** Everything a backup carries: the countdowns, the wishes, the capsules, the footprints, and how the app was set up. */
data class Backup(
    val events: List<CountdownEvent>,
    val settings: AppSettings,
    /** The wish list, which travels with the countdowns — it is half the app's content. */
    val wishes: List<Wish> = emptyList(),
    /** The time capsules, names and days only — the photos themselves stay behind. */
    val capsules: List<TimeCapsule> = emptyList(),
    /** The footprints, places and days only — the media itself stays behind, the same rule. */
    val footprints: List<Footprint> = emptyList(),
    /**
     * The capsules' voice letters, as file name → Base64. A voice is the one
     * media the album has no original of — there is nowhere else it could
     * come back from — so unlike every picture it travels inside the backup
     * itself. A minute of it is about a megabyte, and a life of letters
     * still fits in a file.
     */
    val voices: Map<String, String> = emptyMap(),
    /** The wall of voice tapes, the same reason the capsules travel. */
    val tapes: List<VoiceTape> = emptyList(),
    /** The tapes' recordings, as file name → Base64 — the voices' own rule. */
    val tapeVoices: Map<String, String> = emptyMap(),
)

/**
 * What putting [backup] back would do to [existing], for the dialog to say before the
 * user commits to it.
 *
 * Both modes are counted at once because the dialog offers both at once: the point is to
 * show what each would do to *this* device, rather than describing replace and merge in
 * the abstract and letting the user find out which one they wanted afterwards. A same-id
 * countdown counts as overwritten — that is the rule merging follows.
 */
fun previewOf(backup: Backup, existing: List<CountdownEvent>): ImportPreview {
    val here = existing.mapTo(mutableSetOf()) { it.id }
    val overwritten = backup.events.count { it.id in here }
    return ImportPreview(
        added = backup.events.size - overwritten,
        overwritten = overwritten,
        discarded = existing.size,
    )
}

/**
 * The backup file's shape.
 *
 * The countdowns are written by [EventCodec], the same one the store uses, so a backup
 * cannot drift from what the app actually keeps. The settings travel with them because a
 * backup is normally made in order to put everything back the way it was on another
 * device, and a list restored into a differently-themed app is only half restored.
 */
object BackupCodec {

    private const val APP = "tickcount"
    private const val FORMAT = 1

    private const val KEY_APP = "app"
    private const val KEY_FORMAT = "format"
    private const val KEY_EXPORTED_AT = "exportedAt"
    private const val KEY_EVENTS = "events"
    private const val KEY_WISHES = "wishes"
    private const val KEY_CAPSULES = "capsules"
    private const val KEY_FOOTPRINTS = "footprints"
    private const val KEY_SETTINGS = "settings"
    private const val KEY_VOICES = "voices"
    private const val KEY_TAPES = "tapes"
    private const val KEY_TAPE_VOICES = "tapeVoices"

    /** [exportedAt] is a parameter rather than read here, so that encoding is repeatable. */
    fun encode(backup: Backup, exportedAt: Long = System.currentTimeMillis()): String =
        JSONObject()
            .put(KEY_APP, APP)
            .put(KEY_FORMAT, FORMAT)
            .put(KEY_EXPORTED_AT, exportedAt)
            .put(KEY_EVENTS, EventCodec.toJson(backup.events))
            .put(KEY_WISHES, WishCodec.encode(backup.wishes))
            .put(KEY_CAPSULES, CapsuleCodec.encode(backup.capsules))
            .put(KEY_FOOTPRINTS, FootprintCodec.encode(backup.footprints))
            .put(KEY_SETTINGS, settingsToJson(backup.settings))
            .apply {
                if (backup.voices.isNotEmpty()) {
                    put(
                        KEY_VOICES,
                        JSONObject().apply { backup.voices.forEach { (name, content) -> put(name, content) } },
                    )
                }
                if (backup.tapes.isNotEmpty()) {
                    put(KEY_TAPES, VoiceTapeCodec.encode(backup.tapes))
                }
                if (backup.tapeVoices.isNotEmpty()) {
                    put(
                        KEY_TAPE_VOICES,
                        JSONObject().apply { backup.tapeVoices.forEach { (name, content) -> put(name, content) } },
                    )
                }
            }
            // Indented: a backup written in plain text is meant to be legible, and one
            // that is not is encrypted before it is written anyway.
            .toString(2)

    /** Throws when [text] is not a backup this version understands. */
    fun decode(text: String): Backup {
        val root = JSONObject(text)
        require(root.optString(KEY_APP) == APP) { "not a TickCount backup" }
        return Backup(
            events = root.optJSONArray(KEY_EVENTS)?.let(EventCodec::fromJson) ?: emptyList(),
            wishes = root.optJSONArray(KEY_WISHES)?.let(WishCodec::decode) ?: emptyList(),
            capsules = root.optJSONArray(KEY_CAPSULES)?.let(CapsuleCodec::decode) ?: emptyList(),
            footprints = root.optJSONArray(KEY_FOOTPRINTS)?.let(FootprintCodec::decode) ?: emptyList(),
            // An older backup may predate a setting; the defaults are the right answer
            // for anything it does not carry.
            settings = root.optJSONObject(KEY_SETTINGS)?.let(::settingsFromJson) ?: AppSettings(),
            // A backup older than voice letters, or one with none recorded, simply
            // carries nothing — the field's default is the same silence.
            voices = root.optJSONObject(KEY_VOICES)?.let { letters ->
                buildMap {
                    for (name in letters.keys()) {
                        letters.optString(name).takeIf { it.isNotEmpty() }?.let { put(name, it) }
                    }
                }
            } ?: emptyMap(),
            // The tape wall rides the same rule: absent in older backups,
            // and then simply empty.
            tapes = root.optJSONArray(KEY_TAPES)?.let(VoiceTapeCodec::decode) ?: emptyList(),
            tapeVoices = root.optJSONObject(KEY_TAPE_VOICES)?.let { recordings ->
                buildMap {
                    for (name in recordings.keys()) {
                        recordings.optString(name).takeIf { it.isNotEmpty() }?.let { put(name, it) }
                    }
                }
            } ?: emptyMap(),
        )
    }

    private fun settingsToJson(settings: AppSettings): JSONObject = JSONObject()
        .put("theme", settings.preset.name)
        .put("hue", settings.customHue.toDouble())
        .put("sort", settings.sortOrder.name)
        .put("language", settings.language.name)
        .put("widgetPanel", settings.widgetPanel.name)
        .put("widgetTextSize", settings.widgetTextSize.name)
        .put("appBackground", settings.appBackground ?: "")
        .put("appBackgroundOn", settings.appBackgroundEnabled)
        .put("appBackgroundZoom", settings.appBackgroundZoom.toDouble())
        .put("appBackgroundFocusX", settings.appBackgroundFocusX.toDouble())
        .put("appBackgroundFocusY", settings.appBackgroundFocusY.toDouble())

    private fun settingsFromJson(json: JSONObject): AppSettings = AppSettings(
        preset = json.optString("theme").toEnum(ThemePreset.entries, AppSettings().preset),
        customHue = json.optDouble("hue", DEFAULT_HUE.toDouble()).toFloat(),
        sortOrder = json.optString("sort").toEnum(SortOrder.entries, AppSettings().sortOrder),
        language = json.optString("language").toEnum(AppLanguage.entries, AppSettings().language),
        widgetPanel = json.optString("widgetPanel")
            .toEnum(WidgetPanel.entries, AppSettings().widgetPanel),
        widgetTextSize = json.optString("widgetTextSize")
            .toEnum(WidgetTextSize.entries, AppSettings().widgetTextSize),
        appBackground = json.optString("appBackground").takeIf { it.isNotEmpty() },
        appBackgroundEnabled = json.optBoolean("appBackgroundOn", true),
        appBackgroundZoom = json.optDouble(
            "appBackgroundZoom",
            CountdownEvent.DEFAULT_BACKGROUND_ZOOM.toDouble(),
        ).toFloat(),
        appBackgroundFocusX = json.optDouble(
            "appBackgroundFocusX",
            CountdownEvent.DEFAULT_BACKGROUND_FOCUS.toDouble(),
        ).toFloat(),
        appBackgroundFocusY = json.optDouble(
            "appBackgroundFocusY",
            CountdownEvent.DEFAULT_BACKGROUND_FOCUS.toDouble(),
        ).toFloat(),
    )

    /** The named constant, or [fallback] when the name is missing or from a newer build. */
    private fun <T : Enum<T>> String.toEnum(values: List<T>, fallback: T): T =
        values.firstOrNull { it.name == this } ?: fallback
}
