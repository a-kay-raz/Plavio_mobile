package com.aviostream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aviostream.app.data.FavoritePeopleRepository
import com.aviostream.app.data.MetaPreview
import com.aviostream.app.data.FollowActorsSupport
import com.aviostream.app.data.PersonDetail
import com.aviostream.app.data.StreamRepository
import com.aviostream.app.data.allPersonCredits
import com.aviostream.app.data.buildCreditSummary
import com.aviostream.app.data.latestCredits
import com.aviostream.app.data.personBirthLine
import com.aviostream.app.data.personInitials
import com.aviostream.app.data.popularCredits
import com.aviostream.app.data.upcomingCredits
import com.aviostream.app.ui.components.ExpandableDescription
import com.aviostream.app.ui.components.GlowBackground
import com.aviostream.app.ui.components.CustomPosterScreen
import com.aviostream.app.ui.components.PosterCard
import com.aviostream.app.ui.components.SafePosterImage
import com.aviostream.app.ui.components.ShimmerPoster
import com.aviostream.app.ui.theme.LocalPalette
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary

/**
 * Actor filmography page (v0.8.31+; Step 58: Nuvio PersonDetailScreen
 * richness). Centered hero — big round photo, name, star + pin,
 * born/age line, place of birth, known for, credits summary, expandable
 * biography — then Popular / Latest / Upcoming rails (Nuvio's three
 * credit sorts). Wide screens (>= 900dp) get Nuvio's sidebar layout:
 * identity on the left, rails on the right. Backed by AvioMeta
 * /person, enriched from TMDB when a key is set; when no addon serves
 * /person the repository falls back to search by actor name.
 */
@Composable
fun ActorScreen(
    repository: StreamRepository,
    personId: Int,
    personName: String? = null,
    favoritePeopleRepository: FavoritePeopleRepository,
    onBack: () -> Unit,
    onOpenDetail: (type: String, metaId: String) -> Unit
) {
    var person by remember(personId, personName) { mutableStateOf<PersonDetail?>(null) }
    var loading by remember(personId, personName) { mutableStateOf(true) }
    var loadAttempt by remember(personId, personName) { mutableStateOf(0) }
    val favState by favoritePeopleRepository.uiState.collectAsState()
    var followedActors by remember { mutableStateOf(FollowActorsSupport.decode(repository.settings.followedActorsJson)) }
    LaunchedEffect(personId, personName, loadAttempt) {
        favoritePeopleRepository.ensureLoaded()
        loading = true
        // Person data comes from whichever installed metadata addon
        // serves /person (AvioMeta is the richest); when none does,
        // the repository falls back to that addon's search by actor
        // name (metadata flexibility, owner's order 2026-10-26).
        person = runCatching { repository.getPerson(personId, personName) }.getOrNull()
        loading = false
    }
    val isFavorite = person?.let { favState.people.any { fav -> fav.tmdbId == it.id } } ?: false
    val isPinned = person?.let { it.id in favState.pinnedPersonIds } ?: false
    val isFollowing = person?.let { FollowActorsSupport.isFollowed(followedActors, it.id) } ?: false
    val onToggleFollow: () -> Unit = {
        person?.let { p ->
            val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val nextList = if (FollowActorsSupport.isFollowed(followedActors, p.id)) {
                FollowActorsSupport.unfollow(followedActors, p.id)
            } else {
                FollowActorsSupport.follow(followedActors, p, year)
            }
            followedActors = nextList
            repository.settings.followedActorsJson = FollowActorsSupport.encode(nextList)
        }
    }

    GlowBackground {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 900.dp
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Actor",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                when {
                    loading -> PersonSkeleton()
                    person == null -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Couldn't load this actor.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(16.dp)
                            )
                            Button(onClick = { loadAttempt++ }) { Text("Retry") }
                        }
                    }
                    wide -> WidePersonContent(
                        person = person!!,
                        isFavorite = isFavorite,
                        isPinned = isPinned,
                        onToggleFavorite = { favoritePeopleRepository.toggle(person!!) },
                        onTogglePinned = { favoritePeopleRepository.togglePinned(person!!.id) },
                        isFollowing = isFollowing,
                        onToggleFollow = onToggleFollow,
                        onOpenDetail = onOpenDetail
                    )
                    else -> NarrowPersonContent(
                        person = person!!,
                        isFavorite = isFavorite,
                        isPinned = isPinned,
                        onToggleFavorite = { favoritePeopleRepository.toggle(person!!) },
                        onTogglePinned = { favoritePeopleRepository.togglePinned(person!!.id) },
                        isFollowing = isFollowing,
                        onToggleFollow = onToggleFollow,
                        onOpenDetail = onOpenDetail
                    )
                }
            }
        }
    }
}

/** Loading skeleton (Nuvio PersonDetailSkeleton): shimmer circle + lines. */
@Composable
private fun PersonSkeleton() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier.size(140.dp).clip(CircleShape)
        ) {
            ShimmerPoster(modifier = Modifier.matchParentSize())
        }
        Spacer(Modifier.height(16.dp))
        ShimmerPoster(Modifier.width(180.dp).height(24.dp))
        Spacer(Modifier.height(10.dp))
        ShimmerPoster(Modifier.width(240.dp).height(16.dp))
        Spacer(Modifier.height(24.dp))
        repeat(3) {
            ShimmerPoster(
                modifier = Modifier.fillMaxWidth().height(18.dp).padding(vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        ShimmerPoster(Modifier.fillMaxWidth().height(180.dp))
    }
}

/** Narrow (phone) layout: one scrolling column, hero then rails. */
@Composable
private fun NarrowPersonContent(
    person: PersonDetail,
    isFavorite: Boolean,
    isPinned: Boolean,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onOpenDetail: (type: String, metaId: String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item(key = "hero") {
            PersonHero(
                person = person,
                isFavorite = isFavorite,
                isPinned = isPinned,
                onToggleFavorite = onToggleFavorite,
                onTogglePinned = onTogglePinned,
                isFollowing = isFollowing,
                onToggleFollow = onToggleFollow,
                centered = true
            )
        }
        personRails(person, onOpenDetail)
    }
}

/** Wide layout (Nuvio, >= 900dp): identity sidebar + rails column. */
@Composable
private fun WidePersonContent(
    person: PersonDetail,
    isFavorite: Boolean,
    isPinned: Boolean,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onOpenDetail: (type: String, metaId: String) -> Unit
) {
    Row(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(392.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = 40.dp, end = 36.dp, top = 24.dp, bottom = 42.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PersonHero(
                person = person,
                isFavorite = isFavorite,
                isPinned = isPinned,
                onToggleFavorite = onToggleFavorite,
                onTogglePinned = onTogglePinned,
                isFollowing = isFollowing,
                onToggleFollow = onToggleFollow,
                centered = false,
                bioMaxLines = 12
            )
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(LocalPalette.current.accent.copy(alpha = 0.30f))
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(start = 40.dp, bottom = 40.dp, top = 24.dp)
        ) {
            personRails(person, onOpenDetail)
        }
    }
}

/**
 * The identity block: photo, name, star + pin, born/age, place,
 * known for, credits summary, expandable biography. Shared by both
 * layouts; the narrow one centers it, the wide one left-aligns it.
 */
@Composable
private fun PersonHero(
    person: PersonDetail,
    isFavorite: Boolean,
    isPinned: Boolean,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    centered: Boolean,
    bioMaxLines: Int = 8
) {
    val align = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = align
    ) {
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(LocalPalette.current.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            if (!person.photo.isNullOrBlank()) {
                SafePosterImage(
                    model = person.photo,
                    contentDescription = person.name,
                    modifier = Modifier.matchParentSize().clip(CircleShape)
                )
            } else {
                Text(
                    text = personInitials(person.name),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = person.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = textAlign
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) LocalPalette.current.accent else TextSecondary
                )
            }
            if (isFavorite) {
                IconButton(onClick = onTogglePinned) {
                    Icon(
                        if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (isPinned) "Unpin" else "Pin to top of favorites",
                        tint = if (isPinned) LocalPalette.current.accent else TextSecondary
                    )
                }
            }
        }
        // Follow / Following pill (v0.14.30 port): following captures
        // the filmography baseline (FollowActorsSupport); the person's
        // NEW titles then surface on Home in "New from your people".
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (isFollowing) {
                        LocalPalette.current.accent.copy(alpha = 0.25f)
                    } else {
                        Color.White.copy(alpha = 0.08f)
                    }
                )
                .clickable(onClick = onToggleFollow)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isFollowing) Icons.Filled.Check else Icons.Filled.Add,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (isFollowing) "Following" else "Follow",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
        val infoItems = buildList {
            person.birthday?.let { add(personBirthLine(it, person.deathday)) }
            person.placeOfBirth?.takeIf { it.isNotBlank() }?.let { add(it) }
            person.knownFor?.takeIf { it.isNotBlank() }?.let { add("Known for $it") }
            val summary = buildCreditSummary(allPersonCredits(person))
            if (summary.isNotBlank()) add(summary)
        }
        infoItems.forEach { info ->
            Text(
                text = info,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(2.dp))
        }
        person.biography?.takeIf { it.isNotBlank() }?.let { bio ->
            Spacer(Modifier.height(12.dp))
            ExpandableDescription(
                text = bio,
                collapsedMaxLines = bioMaxLines,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * The credit rails, as a LazyListScope extension so both layouts can
 * emit them: Popular (by popularity), Latest (released, newest first),
 * Upcoming (unreleased, soonest first). When no credit carries a
 * popularity or a release date the rails would all be empty, so the
 * old Movies / Series grids are the fallback.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.personRails(
    person: PersonDetail,
    onOpenDetail: (type: String, metaId: String) -> Unit
) {
    val credits = allPersonCredits(person)
    val popular = popularCredits(credits).filter { it.popularity != null }
    val latest = latestCredits(credits)
    val upcoming = upcomingCredits(credits)
    if (popular.isEmpty() && latest.isEmpty() && upcoming.isEmpty()) {
        if (person.movies.isNotEmpty()) {
            item(key = "movies_header") {
                Text(
                    "Movies",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item(key = "movies_grid") {
                FilmographyGrid(
                    metas = person.movies,
                    onOpenDetail = onOpenDetail
                )
            }
        }
        if (person.series.isNotEmpty()) {
            item(key = "series_header") {
                Text(
                    "Series",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item(key = "series_grid") {
                FilmographyGrid(
                    metas = person.series,
                    onOpenDetail = onOpenDetail
                )
            }
        }
        return
    }
    if (popular.isNotEmpty()) {
        item(key = "popular_header") {
            Text(
                "Popular",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item(key = "popular_rail") {
            PersonCreditRail(metas = popular, onOpenDetail = onOpenDetail)
        }
    }
    if (latest.isNotEmpty()) {
        item(key = "latest_header") {
            Text(
                "Latest",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item(key = "latest_rail") {
            PersonCreditRail(metas = latest, onOpenDetail = onOpenDetail)
        }
    }
    if (upcoming.isNotEmpty()) {
        item(key = "upcoming_header") {
            Text(
                "Upcoming",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item(key = "upcoming_rail") {
            PersonCreditRail(metas = upcoming, onOpenDetail = onOpenDetail)
        }
    }
}

@Composable
private fun PersonCreditRail(
    metas: List<MetaPreview>,
    onOpenDetail: (type: String, metaId: String) -> Unit
) {
    val unique = metas.distinctBy { it.type + "|" + it.id }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(unique, key = { it.type + "|" + it.id }) { meta ->
            PosterCard(
                meta = meta,
                onClick = { onOpenDetail(meta.type, meta.id) },
                surface = CustomPosterScreen.DETAILS,
                modifier = Modifier.width(120.dp)
            )
        }
    }
}

@Composable
private fun FilmographyGrid(
    metas: List<MetaPreview>,
    onOpenDetail: (type: String, metaId: String) -> Unit
) {
    // Fixed-height grid inside the outer LazyColumn.
    val columns = adaptiveColumnCount(LocalConfiguration.current.screenWidthDp - 48)
    val uniqueMetas = metas.distinctBy { it.type + "|" + it.id }
    val rows = (uniqueMetas.size + columns - 1) / columns
    val gridHeight = (rows * 244).dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(gridHeight),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false
    ) {
        items(uniqueMetas, key = { it.type + "|" + it.id }) { meta ->
            PosterCard(
                meta = meta,
                onClick = { onOpenDetail(meta.type, meta.id) },
                surface = CustomPosterScreen.DETAILS
            )
        }
    }
}
