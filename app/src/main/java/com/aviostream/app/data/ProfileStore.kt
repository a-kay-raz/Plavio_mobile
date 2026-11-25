package com.aviostream.app.data

import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.room.Room
import com.aviostream.app.data.db.AppDatabase
import com.aviostream.app.data.db.Profile
import com.aviostream.app.data.db.ProfileDatabase
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Step 56: local multi-profile, no server sync (Nuvio profiles/,
 * local half). A profile is a row in the global database plus its own
 * database file (library/progress) and its own settings file
 * (per-profile settings). Nuvio syncs profiles through Supabase RPCs;
 * Avio has no profile backend, so there is deliberately no sync
 * half here — profiles live on this device only.
 *
 * PINs are a local salted SHA-256 (Nuvio verifies server-side). No
 * biometric unlock and no remote avatar catalog or profile
 * backgrounds — avatars are the 12 built-in presets or the profile's
 * own photo, colors are Nuvio's 16-color palette.
 */
object ProfileStore {

    /** Nuvio MAX_PROFILES. */
    const val MAX_PROFILES = 6

    /** Nuvio PROFILE_COLORS, verbatim. */
    val PROFILE_COLORS = listOf(
        "#1E88E5", "#E53935", "#43A047", "#FB8C00",
        "#8E24AA", "#00ACC1", "#F4511E", "#3949AB",
        "#C0CA33", "#D81B60", "#00897B", "#5E35B1",
        "#7CB342", "#039BE5", "#FFB300", "#6D4C41"
    )

    private const val GLOBAL_PREFS = "avio_settings"
    private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    private const val KEY_PROFILES_MIGRATED = "profiles_migrated"
    private const val KEY_PREINSTALL_PREFIX = "preinstall_done_"

    private val unlockedIds = mutableSetOf<String>()

    fun profilePrefsName(profileId: String): String = "avio_settings_$profileId"

    fun profileDbName(profileId: String): String = "plavio-profile-$profileId.db"

    /** The profile's own photo file (JPEG, app-private storage). */
    fun photoFile(context: Context, profileId: String): File =
        File(context.filesDir, "profile_photo_$profileId.jpg")

    fun activeProfileId(context: Context): String? =
        context.getSharedPreferences(GLOBAL_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_PROFILE_ID, null)

    fun setActiveProfileId(context: Context, profileId: String) {
        context.getSharedPreferences(GLOBAL_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACTIVE_PROFILE_ID, profileId).apply()
    }

    fun openProfileDatabase(context: Context, profileId: String): ProfileDatabase =
        Room.databaseBuilder(context, ProfileDatabase::class.java, profileDbName(profileId))
            .build()

    // -- PIN (local salted SHA-256) -------------------------------------

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashed = digest.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return hashed.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(profile: Profile, pin: String): Boolean {
        val hash = profile.pinHash ?: return false
        val salt = profile.pinSalt ?: return false
        return hashPin(pin, salt) == hash
    }

    /** In-process cache: a profile unlocked once stays unlocked until the process dies. */
    fun isUnlocked(profileId: String): Boolean = profileId in unlockedIds

    fun markUnlocked(profileId: String) {
        unlockedIds += profileId
    }

    fun clearUnlocked() {
        unlockedIds.clear()
    }

    // -- Restart ---------------------------------------------------------

    /** Switch profile = restart the process on the new active profile. */
    fun restartApp(context: Context) {
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            } ?: return
        context.startActivity(intent)
        Process.killProcess(Process.myPid())
    }

    /**
     * T7: make sure an active profile is persisted and return its id.
     * Adopts the first profile when one exists but none is active
     * (a restore can leave rows with no active id); seeds a fresh
     * "Main" profile when the table is empty. Returns null only when
     * the database itself fails.
     */
    suspend fun ensureActiveProfile(
        context: Context,
        globalDb: AppDatabase,
        legacySettings: SettingsStore
    ): String? {
        activeProfileId(context)?.let { return it }
        globalDb.profileDao().getAll().firstOrNull()?.let {
            setActiveProfileId(context, it.id)
            return it.id
        }
        val now = System.currentTimeMillis()
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = legacySettings.profileUserName.ifBlank { "Main" },
            avatarColorHex = "#1E88E5",
            avatar = legacySettings.profileAvatar,
            pinHash = null,
            pinSalt = null,
            createdAt = now,
            sortOrder = now
        )
        globalDb.profileDao().upsert(profile)
        setActiveProfileId(context, profile.id)
        return profile.id
    }

    // -- One-time legacy migration ---------------------------------------

    /**
     * First launch after the profiles update: the single identity
     * becomes the "Main" profile. Its library/progress rows move from
     * the global tables into its own database file, its settings copy
     * into its own prefs file (device-level keys stay global), and its
     * photo is renamed to the per-profile file. Runs once; guarded by
     * the flag so a crash mid-migration retries it on next launch.
     */
    suspend fun migrateLegacyData(
        context: Context,
        globalDb: AppDatabase,
        legacySettings: SettingsStore
    ) {
        val globalPrefs = context.getSharedPreferences(GLOBAL_PREFS, Context.MODE_PRIVATE)
        if (globalPrefs.getBoolean(KEY_PROFILES_MIGRATED, false)) return
        if (globalDb.profileDao().count() > 0) {
            // Profiles already exist (e.g. a restore) — just mark done.
            // T7: a restore can leave profile rows but no persisted
            // active-profile id; returning without setting one made
            // the app throw in onCreate on every launch. Adopt the
            // first profile instead.
            runCatching { ensureActiveProfile(context, globalDb, legacySettings) }
            globalPrefs.edit().putBoolean(KEY_PROFILES_MIGRATED, true).apply()
            return
        }
        val now = System.currentTimeMillis()
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = legacySettings.profileUserName.ifBlank { "Main" },
            avatarColorHex = "#1E88E5",
            avatar = legacySettings.profileAvatar,
            pinHash = null,
            pinSalt = null,
            createdAt = now,
            sortOrder = now
        )
        globalDb.profileDao().upsert(profile)
        setActiveProfileId(context, profile.id)

        // Library/progress rows move to the profile database.
        val profileDb = openProfileDatabase(context, profile.id)
        try {
            globalDb.watchlistDao().getAll().forEach { profileDb.watchlistDao().upsert(it) }
            globalDb.watchedDao().getAll().forEach { profileDb.watchedDao().upsert(it) }
            globalDb.playbackDao().getAll().forEach { profileDb.playbackDao().upsert(it) }
            globalDb.customListDao().getLists().forEach { profileDb.customListDao().insertLists(listOf(it)) }
            globalDb.customListDao().getAllItems().forEach { profileDb.customListDao().insertItems(listOf(it)) }
            globalDb.collectionDao().getCollections().forEach { collection ->
                profileDb.collectionDao().upsertCollection(collection)
                globalDb.collectionDao().getFolders(collection.id).forEach { folder ->
                    profileDb.collectionDao().upsertFolder(folder)
                    globalDb.collectionDao().getSources(folder.id).forEach { source ->
                        profileDb.collectionDao().upsertSource(source)
                    }
                }
            }
        } finally {
            profileDb.close()
        }
        // The global rows are now dead weight — clear them so nothing
        // ever reads stale library data from the global tables again.
        globalDb.openHelper.writableDatabase.run {
            execSQL("DELETE FROM watchlist")
            execSQL("DELETE FROM watched")
            execSQL("DELETE FROM playback")
            execSQL("DELETE FROM custom_lists")
            execSQL("DELETE FROM custom_list_items")
            execSQL("DELETE FROM collections")
            execSQL("DELETE FROM collection_folders")
            execSQL("DELETE FROM collection_sources")
        }

        // Settings copy into the profile's own file (device-level keys
        // stay in the global file).
        val profilePrefs = context.getSharedPreferences(profilePrefsName(profile.id), Context.MODE_PRIVATE)
        val editor = profilePrefs.edit()
        globalPrefs.all.forEach { (key, value) ->
            if (key == KEY_ACTIVE_PROFILE_ID || key == KEY_PROFILES_MIGRATED ||
                key.startsWith(KEY_PREINSTALL_PREFIX)
            ) return@forEach
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST")
                editor.putStringSet(key, value as Set<String>)
            }
        }
        editor.apply()

        // The old single photo becomes this profile's photo.
        val legacyPhoto = File(context.filesDir, "profile_photo.jpg")
        if (legacyPhoto.exists()) {
            runCatching { legacyPhoto.renameTo(photoFile(context, profile.id)) }
        }

        globalPrefs.edit().putBoolean(KEY_PROFILES_MIGRATED, true).apply()
    }
}

/**
 * Instance API over the global database: observe / create / rename /
 * delete profiles. Switching itself is [ProfileStore.setActiveProfileId]
 * + [ProfileStore.restartApp] (the UI asks for the PIN first when the
 * target profile has one).
 */
class ProfilesRepository(private val db: AppDatabase) {

    fun observeProfiles(): Flow<List<Profile>> = db.profileDao().observeAll()

    suspend fun getAll(): List<Profile> = db.profileDao().getAll()

    suspend fun getById(id: String): Profile? = db.profileDao().getById(id)

    suspend fun create(
        name: String,
        avatarColorHex: String = "#1E88E5",
        avatar: String = "",
        pin: String? = null
    ): Profile? {
        if (db.profileDao().count() >= ProfileStore.MAX_PROFILES) return null
        val now = System.currentTimeMillis()
        val salt = if (pin != null) ProfileStore.newSalt() else null
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Profile" },
            avatarColorHex = avatarColorHex,
            avatar = avatar,
            pinHash = if (pin != null && salt != null) ProfileStore.hashPin(pin, salt) else null,
            pinSalt = salt,
            createdAt = now,
            sortOrder = now
        )
        db.profileDao().upsert(profile)
        return profile
    }

    suspend fun update(profile: Profile) {
        db.profileDao().upsert(profile)
    }

    /**
     * Delete a profile and its private files. Returns false when it is
     * the last profile (one must always remain). The caller switches
     * away first when deleting the active profile.
     */
    suspend fun delete(context: Context, profile: Profile): Boolean {
        if (db.profileDao().count() <= 1) return false
        db.profileDao().deleteById(profile.id)
        runCatching { context.deleteDatabase(ProfileStore.profileDbName(profile.id)) }
        runCatching { ProfileStore.photoFile(context, profile.id).delete() }
        runCatching {
            context.getSharedPreferences(
                ProfileStore.profilePrefsName(profile.id), Context.MODE_PRIVATE
            ).edit().clear().apply()
        }
        // S4: a deleted profile's secrets go with it.
        runCatching {
            context.getSharedPreferences(
                SettingsStore.secretPrefsName(ProfileStore.profilePrefsName(profile.id)),
                Context.MODE_PRIVATE
            ).edit().clear().apply()
        }
        return true
    }
}
