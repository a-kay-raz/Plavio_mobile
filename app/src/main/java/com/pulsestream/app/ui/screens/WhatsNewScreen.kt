package com.pulsestream.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulsestream.app.BuildConfig
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * One commit from the Plavio repo's main branch — mirrors Nuvio's
 * InfuseCommit. Only identity, title, author and date are shown; the
 * full commit detail lives on GitHub.
 */
internal data class PlavioCommit(
    val sha: String,
    val shortSha: String,
    val title: String,
    val authorName: String,
    val authorLogin: String?,
    /** ISO-8601 date as returned by the GitHub API, e.g. 2026-11-21T10:07:38Z. */
    val date: String?,
    val htmlUrl: String?,
)

/**
 * Loads the latest commits on main from GitHub (Nuvio
 * WhatsNewCommitsRepository, repointed to this repo). This deliberately
 * does NOT read releases or changelogs: release notes stay on GitHub
 * only. Parsed with JsonElement — the serialization compiler plugin is
 * not applied to this module, so no @Serializable DTOs.
 */
internal object WhatsNewCommitsRepository {
    private const val COMMITS_URL =
        "https://api.github.com/repos/a-kay-raz/Plavio_mobile/commits?sha=main&per_page=30"

    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): Result<List<PlavioCommit>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(COMMITS_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Plavio/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { resp ->
                check(resp.isSuccessful) { "GitHub commits request failed: ${resp.code}" }
                val body = resp.body?.string() ?: error("Empty response")
                json.parseToJsonElement(body).jsonArray.mapNotNull { el ->
                    val obj = el as? JsonObject ?: return@mapNotNull null
                    val sha = obj.str("sha") ?: return@mapNotNull null
                    val commitObj = obj["commit"] as? JsonObject
                    val message = commitObj?.str("message") ?: sha
                    val authorObj = commitObj?.get("author") as? JsonObject
                    val authorLogin = (obj["author"] as? JsonObject)?.str("login")
                    val authorName = authorObj?.str("name") ?: authorLogin ?: "Unknown"
                    PlavioCommit(
                        sha = sha,
                        shortSha = sha.take(7),
                        title = message.substringBefore("\n").trim().ifBlank { sha },
                        authorName = authorName,
                        authorLogin = authorLogin,
                        date = authorObj?.str("date"),
                        htmlUrl = obj.str("html_url"),
                    )
                }
            }
        }
    }

    // Same defensive read as RelatedContent: a wrong-typed field defaults
    // instead of jsonPrimitive throwing and killing the parse.
    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

/**
 * What's New, showing only the latest commits on main (Nuvio
 * WhatsNewSettingsScreen, Plavio components). No channel picker, no
 * release notes: tapping a commit opens it on GitHub.
 */
@Composable
fun WhatsNewScreen(onBack: () -> Unit = {}) {
    var state by remember { mutableStateOf<WhatsNewLoadState>(WhatsNewLoadState.Loading) }
    var refreshKey by rememberSaveable { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        state = WhatsNewLoadState.Loading
        isRefreshing = true
        try {
            state = WhatsNewCommitsRepository.load().fold(
                onSuccess = { WhatsNewLoadState.Content(it) },
                onFailure = { WhatsNewLoadState.Error },
            )
        } finally {
            isRefreshing = false
        }
    }

    GlowBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "What's New",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (isRefreshing) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = LocalPalette.current.accent
                            )
                        }
                    } else {
                        IconButton(onClick = { refreshKey++ }) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Refresh",
                                tint = TextPrimary
                            )
                        }
                    }
                }
            }
            item(key = "release_card") {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NewReleases,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = LocalPalette.current.accent
                            )
                            Text(
                                text = "Plavio v${BuildConfig.VERSION_NAME}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Latest commits on main — tap a commit to open it on GitHub.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
            when (val value = state) {
                WhatsNewLoadState.Loading -> item(key = "loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = LocalPalette.current.accent)
                    }
                }

                WhatsNewLoadState.Error -> item(key = "error") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Couldn't load the latest commits.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        TextButton(onClick = { refreshKey++ }) {
                            Text("Retry", color = LocalPalette.current.accent)
                        }
                    }
                }

                is WhatsNewLoadState.Content -> {
                    if (value.commits.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = "No commits yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        items(value.commits, key = { it.sha }) { commit ->
                            CommitCard(commit = commit)
                        }
                    }
                }
            }
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

private sealed interface WhatsNewLoadState {
    data object Loading : WhatsNewLoadState
    data object Error : WhatsNewLoadState
    data class Content(val commits: List<PlavioCommit>) : WhatsNewLoadState
}

@Composable
private fun CommitCard(commit: PlavioCommit) {
    val context = LocalContext.current
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = commit.shortSha,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Text(
                text = commit.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = listOfNotNull(
                    commit.authorLogin?.let { "@$it" } ?: commit.authorName,
                    commit.date?.take(10)?.takeIf { it.isNotBlank() },
                ).joinToString(" • "),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            commit.htmlUrl?.let { url ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = TextSecondary
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("Open on GitHub", color = TextSecondary)
                    }
                }
            }
        }
    }
}
