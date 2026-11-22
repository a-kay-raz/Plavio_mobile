package com.lagradost.cloudstream3.plugins

import android.content.Context
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Repository manifest exposed by CloudStream's repository API. */
data class Repository(
    val iconUrl: String?,
    val name: String,
    val description: String?,
    val manifestVersion: Int,
    val pluginLists: List<String>,
)

/**
 * CloudStream-compatible repository registry.
 *
 * Plavio owns repository discovery/download UI, but CloudStream extensions expect the
 * native RepositoryManager ABI to exist and persist RepositoryData under the
 * standard CloudStream key. This class intentionally implements only that ABI;
 * it does not maintain a second plugin catalog or provider pipeline.
 */
object RepositoryManager {
    const val REPOSITORIES_KEY = "REPOSITORIES_KEY"

    /** Plavio does not ship CloudStream prebuilt repositories. */
    val PREBUILT_REPOSITORIES: Array<RepositoryData> by lazy { emptyArray() }

    private val repoLock = Mutex()

    fun getRepositories(): Array<RepositoryData> =
        getKey<Array<RepositoryData>>(REPOSITORIES_KEY) ?: emptyArray()

    /**
     * Compatibility API used by CloudStream extensions that inspect a repository before adding it.
     * Repository discovery/installation remains owned by Plavio's CloudStream repository runtime.
     */
    suspend fun parseRepository(url: String): Repository? = runCatching {
        val normalizedUrl = url.trim()
        require(normalizedUrl.isNotBlank()) { "Repository URL is blank" }
        // Plavio: org.json instead of the AAR's Jackson mapper (see DataStore).
        val obj = JSONObject(app.get(normalizedUrl).text)
        val pluginLists = obj.optJSONArray("pluginLists")?.let { array ->
            List(array.length()) { index -> array.optString(index) }
                .map(String::trim).filter(String::isNotBlank)
        }.orEmpty()
        Repository(
            iconUrl = obj.optString("iconUrl").takeIf { it.isNotBlank() },
            name = obj.optString("name").takeIf { it.isNotBlank() } ?: normalizedUrl,
            description = obj.optString("description").takeIf { it.isNotBlank() },
            manifestVersion = obj.optInt("manifestVersion", 1),
            pluginLists = pluginLists,
        )
    }.getOrNull()

    suspend fun addRepository(repository: RepositoryData) {
        val normalized = repository.copy(url = repository.url.trim())
        if (normalized.url.isBlank()) return

        repoLock.withLock {
            val current = getRepositories()
            setKey(
                REPOSITORIES_KEY,
                (current + normalized).distinctBy { it.url.trim() },
            )
        }
    }

    suspend fun removeRepository(context: Context, repository: RepositoryData) {
        val normalizedUrl = repository.url.trim()
        if (normalizedUrl.isBlank()) return

        repoLock.withLock {
            val current = getRepositories()
            setKey(
                REPOSITORIES_KEY,
                current.filter { it.url.trim() != normalizedUrl }.toTypedArray(),
            )
        }
    }
}
