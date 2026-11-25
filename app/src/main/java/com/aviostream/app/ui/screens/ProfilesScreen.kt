package com.aviostream.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aviostream.app.data.ProfileStore
import com.aviostream.app.data.ProfilesRepository
import com.aviostream.app.data.SettingsStore
import com.aviostream.app.data.db.Profile
import com.aviostream.app.ui.components.GlassCard
import com.aviostream.app.ui.components.GlowBackground
import com.aviostream.app.ui.components.PRESET_AVATARS
import com.aviostream.app.ui.components.ProfileAvatarImage
import com.aviostream.app.ui.theme.LocalPalette
import com.aviostream.app.ui.theme.TextPrimary
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Nuvio parseHexColor (profiles/ProfileModels.kt, verbatim logic). */
private fun parseHexColor(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    return runCatching {
        when (cleaned.length) {
            6 -> Color(("FF$cleaned").toLong(16))
            8 -> Color(cleaned.toLong(16))
            else -> Color(0xFF1E88E5)
        }
    }.getOrDefault(Color(0xFF1E88E5))
}

/**
 * Step 56: profiles manager (Nuvio ProfileSelectionScreen +
 * ProfileSwitcherTab, local half). Lists profiles, switches between
 * them (PIN first when the target is locked — switching restarts the
 * app on the new profile's database + settings), and creates/edits/
 * deletes them. No server sync: profiles live on this device only.
 */
@Composable
fun ProfilesScreen(
    profilesRepository: ProfilesRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val profiles by profilesRepository.observeProfiles()
        .collectAsState(initial = emptyList())
    val activeId = remember(tick, profiles) { ProfileStore.activeProfileId(context) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pinTarget by remember { mutableStateOf<Profile?>(null) }
    var deleteTarget by remember { mutableStateOf<Profile?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun doSwitch(profile: Profile) {
        ProfileStore.setActiveProfileId(context, profile.id)
        ProfileStore.restartApp(context)
    }

    fun requestSwitch(profile: Profile) {
        if (profile.id == activeId) return
        if (profile.pinEnabled && !ProfileStore.isUnlocked(profile.id)) {
            pinTarget = profile
        } else {
            doSwitch(profile)
        }
    }

    GlowBackground {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Text(
                    text = "Profiles",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                    color = TextPrimary
                )
            }
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "Each profile keeps its own watchlist, progress, lists and settings. Switching restarts the app on that profile.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = V117Muted,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                if (notice != null) {
                    item {
                        Text(notice!!, color = V117Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                items(profiles, key = { it.id }) { profile ->
                    val isActive = profile.id == activeId
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { requestSwitch(profile) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProfileAvatarImage(
                                avatarSetting = profile.avatar,
                                photoFile = ProfileStore.photoFile(context, profile.id),
                                modifier = Modifier.size(56.dp),
                                placeholderLetter = profile.name.firstOrNull()?.uppercase(),
                                backgroundColor = parseHexColor(profile.avatarColorHex)
                            )
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                    if (profile.pinEnabled) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Filled.Lock, contentDescription = "PIN locked", tint = V117Muted, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(
                                    text = if (isActive) "Active profile — tap to stay" else "Tap to switch",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = V117Muted
                                )
                            }
                            if (isActive) {
                                Icon(Icons.Filled.Check, contentDescription = "Active", tint = LocalPalette.current.accent)
                            }
                            IconButton(onClick = { editing = profile }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = V117Muted)
                            }
                            IconButton(
                                onClick = { deleteTarget = profile },
                                enabled = profiles.size > 1
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = V117Muted)
                            }
                        }
                    }
                }
                item {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (profiles.size >= ProfileStore.MAX_PROFILES) {
                                    notice = "You can have up to ${ProfileStore.MAX_PROFILES} profiles."
                                } else {
                                    creating = true
                                }
                            },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingsIconTile(Icons.Filled.Add, "Add a profile")
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = "Add a profile",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (creating) {
        ProfileEditorDialog(
            profile = null,
            onDismiss = { creating = false },
            onSave = { name, colorHex, avatar, pin ->
                scope.launch {
                    val created = withContext(Dispatchers.IO) {
                        profilesRepository.create(name, colorHex, avatar, pin)
                    }
                    creating = false
                    if (created == null) {
                        notice = "Couldn't add a profile — the limit is ${ProfileStore.MAX_PROFILES}."
                    } else {
                        tick++
                    }
                }
            }
        )
    }

    if (editing != null) {
        val target = editing!!
        ProfileEditorDialog(
            profile = target,
            onDismiss = { editing = null },
            onSave = { name, colorHex, avatar, pin ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val updated = if (pin != null) {
                            val salt = ProfileStore.newSalt()
                            target.copy(
                                name = name,
                                avatarColorHex = colorHex,
                                avatar = avatar,
                                pinHash = ProfileStore.hashPinV2(pin, salt),
                                pinSalt = salt
                            )
                        } else {
                            target.copy(name = name, avatarColorHex = colorHex, avatar = avatar)
                        }
                        profilesRepository.update(updated)
                        // Mirror into the profile's own settings file so
                        // the Drive backup (which reads SettingsStore)
                        // keeps seeing the same name/avatar.
                        val prefs = SettingsStore(context, ProfileStore.profilePrefsName(target.id))
                        prefs.profileUserName = name
                        prefs.profileAvatar = avatar
                    }
                    editing = null
                    tick++
                }
            },
            onRemovePin = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        profilesRepository.update(target.copy(pinHash = null, pinSalt = null))
                    }
                    editing = null
                    tick++
                }
            }
        )
    }

    if (pinTarget != null) {
        PinEntryDialog(
            profile = pinTarget!!,
            onDismiss = { pinTarget = null },
            onUnlocked = {
                ProfileStore.markUnlocked(pinTarget!!.id)
                val target = pinTarget!!
                pinTarget = null
                doSwitch(target)
            },
            onPinMigrated = { upgraded ->
                scope.launch {
                    withContext(Dispatchers.IO) { profilesRepository.update(upgraded) }
                }
            }
        )
    }

    if (deleteTarget != null) {
        val target = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${target.name}?", color = TextPrimary) },
            text = {
                Text(
                    "Their watchlist, progress, lists and settings are deleted too. This can't be undone." +
                        if (target.id == activeId) " You are using this profile, so the app will switch to another profile first." else ""
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val wasActive = target.id == activeId
                        if (wasActive) {
                            val other = profiles.firstOrNull { it.id != target.id }
                            if (other != null) ProfileStore.setActiveProfileId(context, other.id)
                        }
                        withContext(Dispatchers.IO) {
                            profilesRepository.delete(context, target)
                        }
                        deleteTarget = null
                        if (wasActive) ProfileStore.restartApp(context) else tick++
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
            containerColor = LocalPalette.current.background
        )
    }
}

/** Create/edit dialog: name, color, avatar (preset/photo/none), optional 4-digit PIN. */
@Composable
private fun ProfileEditorDialog(
    profile: Profile?,
    onDismiss: () -> Unit,
    onSave: (name: String, colorHex: String, avatar: String, pin: String?) -> Unit,
    onRemovePin: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var colorHex by remember { mutableStateOf(profile?.avatarColorHex ?: "#1E88E5") }
    var avatar by remember { mutableStateOf(profile?.avatar ?: "") }
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && profile != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    saveCroppedPhoto(context, uri, ProfileStore.photoFile(context, profile.id))
                }
                if (saved) avatar = "photo"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) "New profile" else "Edit ${profile.name}", color = TextPrimary) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Color", style = MaterialTheme.typography.bodyMedium, color = V117Muted)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileStore.PROFILE_COLORS.take(8).forEach { hex ->
                        Box(
                            Modifier.size(28.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(hex))
                                .border(
                                    width = if (hex == colorHex) 2.dp else 0.dp,
                                    color = if (hex == colorHex) TextPrimary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { colorHex = hex }
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileStore.PROFILE_COLORS.drop(8).forEach { hex ->
                        Box(
                            Modifier.size(28.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(hex))
                                .border(
                                    width = if (hex == colorHex) 2.dp else 0.dp,
                                    color = if (hex == colorHex) TextPrimary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { colorHex = hex }
                        )
                    }
                }
                Text("Avatar", style = MaterialTheme.typography.bodyMedium, color = V117Muted)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().height(196.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PRESET_AVATARS, key = { it.id }) { preset ->
                        ProfileAvatarImage(
                            avatarSetting = "preset:${preset.id}",
                            photoFile = null,
                            modifier = Modifier.size(56.dp)
                                .border(
                                    width = if (avatar == "preset:${preset.id}") 2.dp else 0.dp,
                                    color = if (avatar == "preset:${preset.id}") LocalPalette.current.accent else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { avatar = "preset:${preset.id}" }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (profile != null) photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            ) else {
                                error = "Save the profile first, then add a photo from Edit."
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Photo", textAlign = TextAlign.Center) }
                    OutlinedButton(
                        onClick = { avatar = "" },
                        modifier = Modifier.weight(1f)
                    ) { Text("No avatar", textAlign = TextAlign.Center) }
                }
                if (avatar == "photo") {
                    Text("Photo set for this profile.", style = MaterialTheme.typography.bodySmall, color = V117Muted)
                }
                Text(
                    if (profile?.pinEnabled == true) "PIN — set a new one, or remove it below"
                    else "PIN (optional) — 4 digits, asked when switching to this profile",
                    style = MaterialTheme.typography.bodyMedium,
                    color = V117Muted
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                    label = { Text("New PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pinConfirm,
                    onValueChange = { pinConfirm = it.filter(Char::isDigit).take(4) },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                if (profile?.pinEnabled == true && onRemovePin != null) {
                    TextButton(onClick = onRemovePin) {
                        Text("Remove PIN", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = "Give the profile a name."
                    return@TextButton
                }
                if (pin.isNotEmpty()) {
                    if (pin.length != 4) {
                        error = "The PIN must be 4 digits."
                        return@TextButton
                    }
                    if (pin != pinConfirm) {
                        error = "The two PINs don't match."
                        return@TextButton
                    }
                }
                onSave(name.trim(), colorHex, avatar, pin.ifEmpty { null })
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = V117Muted) }
        },
        containerColor = LocalPalette.current.background
    )
}

/** PIN gate (Nuvio ProfilePinEntry, scoped): 4 digits, checked locally. */
@Composable
fun PinEntryDialog(
    profile: Profile,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit,
    // S10: a legacy single-SHA-256 PIN hash is upgraded to the stretched
    // format on a successful unlock; the caller persists the returned
    // profile (the old hash keeps working until then, so a missed write
    // never locks anyone out).
    onPinMigrated: ((Profile) -> Unit)? = null
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter PIN for ${profile.name}", color = TextPrimary) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter(Char::isDigit).take(4)
                        error = null
                    },
                    label = { Text("4-digit PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length == 4,
                onClick = {
                    if (ProfileStore.verifyPin(profile, pin)) {
                        ProfileStore.upgradedProfile(profile, pin)?.let { onPinMigrated?.invoke(it) }
                        onUnlocked()
                    } else error = "Wrong PIN. Try again."
                }
            ) { Text("Unlock") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = V117Muted) }
        },
        containerColor = LocalPalette.current.background
    )
}

/** Crop a picked photo square (512px max) and save it as JPEG — same crop as AccountScreen's saveProfilePhoto, to a chosen file. */
private fun saveCroppedPhoto(context: android.content.Context, uri: Uri, target: java.io.File): Boolean =
    runCatching {
        val source = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return false
        val side = minOf(source.width, source.height)
        val cropped = Bitmap.createBitmap(
            source, (source.width - side) / 2, (source.height - side) / 2, side, side
        )
        val scaled = if (side > 512) Bitmap.createScaledBitmap(cropped, 512, 512, true) else cropped
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        target.writeBytes(out.toByteArray())
        true
    }.getOrDefault(false)
