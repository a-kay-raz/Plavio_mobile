package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

const val PREFERENCES_NAME: String = "rebuild_preference"

/**
 * Minimal persistent storage ABI used by extensions.
 *
 * Plavio rewrite of the TV port's Jackson-backed DataStore on org.json /
 * SharedPreferences (no Jackson in this file): values are stored as JSON
 * strings in the "rebuild_preference" prefs file, exactly like before, so
 * the filename and the swallow-on-error contract are unchanged.
 *
 * Codec coverage (documented limit, not a silent gap): primitives
 * (String/Boolean/Int/Long/Double/Float), arrays and lists of those,
 * maps, and data classes — written via field reflection, read back for
 * the shapes the host itself uses (notably Array<RepositoryData>) plus
 * any no-arg-constructible bean via field injection. A type the codec
 * cannot rebuild reads back as null, matching the old
 * runCatching { ... }.getOrNull() behaviour.
 */
object DataStore {
    fun getFolderName(folder: String, path: String): String =
        "${folder.trimEnd('/')}/${path.trimStart('/')}"

    fun Context.getSharedPrefs(): SharedPreferences =
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun <T : Any> Context.getKey(path: String, valueType: Class<T>): T? {
        return runCatching {
            val json = getSharedPrefs().getString(path, null) ?: return@runCatching null
            decodeValue(json, valueType)
        }.getOrNull()
    }

    inline fun <reified T : Any> Context.getKey(path: String): T? {
        return runCatching {
            val json = getSharedPrefs().getString(path, null) ?: return@runCatching null
            decodeValue(json, T::class.java)
        }.getOrNull()
    }

    fun <T> Context.setKey(path: String, value: T) {
        runCatching {
            getSharedPrefs().edit().apply {
                if (value == null) remove(path) else putString(path, encodeValue(value))
            }.apply()
        }
    }

    fun <T> Context.setKey(folder: String, path: String, value: T) {
        setKey(getFolderName(folder, path), value)
    }

    fun Context.removeKey(path: String) {
        runCatching { getSharedPrefs().edit().remove(path).apply() }
    }

    fun Context.removeKey(folder: String, path: String) {
        removeKey(getFolderName(folder, path))
    }

    fun Context.removeKeys(folder: String): Int {
        val keys = getKeys(folder)
        getSharedPrefs().edit().apply {
            keys.forEach(::remove)
        }.apply()
        return keys.size
    }

    fun Context.getKeys(folder: String): List<String> {
        val prefix = folder.trimEnd('/') + "/"
        return getSharedPrefs().all.keys.filter { it.startsWith(prefix) }
    }

    // ---- org.json codec (private) ----

    private fun encodeValue(value: Any?): String = encodeAny(value).toString()

    private fun encodeAny(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is String, is Boolean, is Number -> value
        is Array<*> -> JSONArray().apply { value.forEach { put(encodeAny(it)) } }
        is Collection<*> -> JSONArray().apply { value.forEach { put(encodeAny(it)) } }
        is Map<*, *> -> JSONObject().apply { value.forEach { (k, v) -> put(k.toString(), encodeAny(v)) } }
        else -> JSONObject().apply {
            value.javaClass.declaredFields.forEach { field ->
                if (field.name.startsWith("$")) return@forEach
                if (java.lang.reflect.Modifier.isStatic(field.modifiers)) return@forEach
                runCatching {
                    field.isAccessible = true
                    put(field.name, encodeAny(field.get(value)))
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> decodeValue(json: String, valueType: Class<T>): T? {
        val parsed = runCatching { JSONTokener(json).nextValue() }.getOrNull() ?: return null
        return decodeAny(parsed, valueType) as? T
    }

    private fun decodeAny(parsed: Any?, target: Class<*>): Any? {
        if (parsed == null || parsed === JSONObject.NULL) return null
        return when {
            target == String::class.java -> parsed.toString()
            target == Boolean::class.java || target == java.lang.Boolean::class.java ->
                when (parsed) {
                    is Boolean -> parsed
                    is String -> parsed.toBooleanStrictOrNull()
                    else -> null
                }
            target == Int::class.java || target == java.lang.Integer::class.java ->
                (parsed as? Number)?.toInt() ?: (parsed as? String)?.toIntOrNull()
            target == Long::class.java || target == java.lang.Long::class.java ->
                (parsed as? Number)?.toLong() ?: (parsed as? String)?.toLongOrNull()
            target == Double::class.java || target == java.lang.Double::class.java ->
                (parsed as? Number)?.toDouble() ?: (parsed as? String)?.toDoubleOrNull()
            target == Float::class.java || target == java.lang.Float::class.java ->
                (parsed as? Number)?.toFloat() ?: (parsed as? String)?.toFloatOrNull()
            target == JSONObject::class.java -> parsed as? JSONObject
            target == JSONArray::class.java -> parsed as? JSONArray
            target.isArray -> {
                val jsonArray = parsed as? JSONArray ?: return null
                val component = target.componentType ?: return null
                val array = java.lang.reflect.Array.newInstance(component, jsonArray.length())
                for (i in 0 until jsonArray.length()) {
                    val element = decodeAny(jsonArray.opt(i), component) ?: continue
                    java.lang.reflect.Array.set(array, i, element)
                }
                array
            }
            target == List::class.java || target == Collection::class.java || target == MutableList::class.java -> {
                val jsonArray = parsed as? JSONArray ?: return null
                ArrayList<Any?>().apply {
                    for (i in 0 until jsonArray.length()) {
                        add(decodeAny(jsonArray.opt(i), Any::class.java))
                    }
                }
            }
            target == Map::class.java || target == MutableMap::class.java -> {
                val jsonObject = parsed as? JSONObject ?: return null
                LinkedHashMap<String, Any?>().apply {
                    jsonObject.keys().forEach { key ->
                        put(key, decodeAny(jsonObject.opt(key), Any::class.java))
                    }
                }
            }
            target.name == "com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData" -> {
                val jsonObject = parsed as? JSONObject ?: return null
                val name = jsonObject.optString("name").takeIf { it.isNotEmpty() } ?: return null
                val url = jsonObject.optString("url").takeIf { it.isNotEmpty() } ?: return null
                val iconUrl = jsonObject.optString("iconUrl").takeIf { it.isNotEmpty() }
                com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData(iconUrl, name, url)
            }
            target == Any::class.java -> when (parsed) {
                is JSONObject -> decodeAny(parsed, Map::class.java)
                is JSONArray -> decodeAny(parsed, List::class.java)
                else -> parsed
            }
            parsed is JSONObject -> {
                // Generic bean: no-arg constructor + field injection by JSON key.
                val instance = runCatching { target.getDeclaredConstructor().newInstance() }.getOrNull()
                    ?: return null
                target.declaredFields.forEach { field ->
                    if (field.name.startsWith("$")) return@forEach
                    if (java.lang.reflect.Modifier.isStatic(field.modifiers)) return@forEach
                    if (!parsed.has(field.name)) return@forEach
                    runCatching {
                        field.isAccessible = true
                        decodeAny(parsed.opt(field.name), field.type)?.let { field.set(instance, it) }
                    }
                }
                instance
            }
            else -> null
        }
    }
}
