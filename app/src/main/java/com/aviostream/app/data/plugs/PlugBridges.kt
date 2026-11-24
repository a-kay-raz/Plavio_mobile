package com.aviostream.app.data.plugs

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Item F (v0.14.65) — the native bridges, built exactly the way the
 * reference host does it (see ~/workspace/nuvio-no-hang-study-2026-11-21.md
 * §1/§5): plugs keep calling the cheerio / crypto-js APIs, but every
 * operation lands HERE, in native code. The DOM lives in Kotlin maps
 * keyed by string element ids — JS never holds a DOM node — and the
 * crypto runs on java.security / javax.crypto with hex as the
 * transport encoding, so no binary ever smears across the boundary.
 *
 * Per run, JsPlugHost creates one [PlugDomBridge], installs both
 * bridges on the run's runtime, and calls [PlugDomBridge.clear] when
 * the run ends (the runtime itself is closed right after, per the
 * standing lifecycle). There is NO second path: in bridge mode the
 * 1.3 MB cheerio.js / crypto-js bundles are never parsed.
 */

/** Native DOM behind the cheerio shim (assets/plugs/bridge.js). */
internal class PlugDomBridge {
    private val documents = HashMap<String, Document>()
    private val elements = HashMap<String, Element>()
    private var counter = 0

    fun clear() {
        documents.clear()
        elements.clear()
    }

    private fun mint(docId: String, el: Element): String {
        val id = "$docId:e${counter++}"
        elements[id] = el
        return id
    }

    private fun idsJson(ids: List<String>): String =
        ids.joinToString(",", "[", "]") { "\"$it\"" }

    /** Ksoup's :contains wants the argument unquoted (reference-host fix). */
    private fun normalizeSelector(selector: String): String =
        selector.replace(CONTAINS_QUOTED) { ":contains(${it.groupValues[1]})" }

    fun install(qjs: QuickJs) {
        qjs.function("__cheerio_load") { args: Array<Any?> ->
            val html = args.getOrNull(0) as? String ?: ""
            val docId = "doc${counter++}"
            documents[docId] = Ksoup.parse(html)
            docId
        }
        qjs.function("__cheerio_select") { args: Array<Any?> ->
            val docId = args.getOrNull(0) as? String ?: ""
            val selector = args.getOrNull(1) as? String ?: ""
            val doc = documents[docId] ?: return@function "[]"
            try {
                if (selector.isBlank()) "[]"
                else idsJson(doc.select(normalizeSelector(selector)).map { mint(docId, it) })
            } catch (e: Exception) {
                "[]"
            }
        }
        qjs.function("__cheerio_find") { args: Array<Any?> ->
            val docId = args.getOrNull(0) as? String ?: ""
            val el = elements[args.getOrNull(1) as? String] ?: return@function "[]"
            val selector = args.getOrNull(2) as? String ?: ""
            try {
                idsJson(el.select(normalizeSelector(selector)).map { mint(docId, it) })
            } catch (e: Exception) {
                "[]"
            }
        }
        qjs.function("__cheerio_children") { args: Array<Any?> ->
            val docId = args.getOrNull(0) as? String ?: ""
            val el = elements[args.getOrNull(1) as? String] ?: return@function "[]"
            val selector = args.getOrNull(2) as? String
            try {
                val kids = el.children()
                val picked = if (selector.isNullOrBlank()) kids
                    else kids.filter { matches(it, normalizeSelector(selector)) }
                idsJson(picked.map { mint(docId, it) })
            } catch (e: Exception) {
                "[]"
            }
        }
        qjs.function("__cheerio_parent") { args: Array<Any?> ->
            val docId = args.getOrNull(0) as? String ?: ""
            val el = elements[args.getOrNull(1) as? String] ?: return@function NONE
            val parent = el.parent()
            if (parent == null) NONE else mint(docId, parent)
        }
        qjs.function("__cheerio_closest") { args: Array<Any?> ->
            val docId = args.getOrNull(0) as? String ?: ""
            val el = elements[args.getOrNull(1) as? String] ?: return@function NONE
            val selector = args.getOrNull(2) as? String ?: ""
            try {
                var node: Element? = el
                var found: Element? = null
                while (node != null) {
                    if (matches(node, normalizeSelector(selector))) {
                        found = node
                        break
                    }
                    node = node.parent()
                }
                if (found == null) NONE else mint(docId, found)
            } catch (e: Exception) {
                NONE
            }
        }
        qjs.function("__cheerio_next") { args: Array<Any?> ->
            val docId = args.getOrNull(0) as? String ?: ""
            val el = elements[args.getOrNull(1) as? String] ?: return@function NONE
            val next = el.nextElementSibling()
            if (next == null) NONE else mint(docId, next)
        }
        qjs.function("__cheerio_prev") { args: Array<Any?> ->
            val docId = args.getOrNull(0) as? String ?: ""
            val el = elements[args.getOrNull(1) as? String] ?: return@function NONE
            val prev = el.previousElementSibling()
            if (prev == null) NONE else mint(docId, prev)
        }
        qjs.function("__cheerio_next_all") { args: Array<Any?> ->
            val docId = args.getOrNull(0) as? String ?: ""
            val el = elements[args.getOrNull(1) as? String] ?: return@function "[]"
            idsJson(el.nextElementSiblings().map { mint(docId, it) })
        }
        qjs.function("__cheerio_prev_all") { args: Array<Any?> ->
            val docId = args.getOrNull(0) as? String ?: ""
            val el = elements[args.getOrNull(1) as? String] ?: return@function "[]"
            idsJson(el.previousElementSiblings().map { mint(docId, it) })
        }
        qjs.function("__cheerio_text") { args: Array<Any?> ->
            val csv = args.getOrNull(1) as? String ?: ""
            csv.split(",")
                .filter { it.isNotEmpty() }
                .mapNotNull { elements[it]?.text() }
                .joinToString(" ")
        }
        qjs.function("__cheerio_html") { args: Array<Any?> ->
            val docId = args.getOrNull(0) as? String ?: ""
            val elementId = args.getOrNull(1) as? String ?: ""
            if (elementId.isEmpty()) documents[docId]?.html() ?: ""
            else elements[elementId]?.html() ?: ""
        }
        qjs.function("__cheerio_inner_html") { args: Array<Any?> ->
            val elementId = args.getOrNull(1) as? String ?: ""
            elements[elementId]?.html() ?: ""
        }
        qjs.function("__cheerio_attr") { args: Array<Any?> ->
            val elementId = args.getOrNull(1) as? String ?: ""
            val name = args.getOrNull(2) as? String ?: ""
            val value = elements[elementId]?.attr(name)
            if (value.isNullOrEmpty()) UNDEFINED else value
        }
        qjs.function("__cheerio_set_attr") { args: Array<Any?> ->
            val el = elements[args.getOrNull(1) as? String]
            val name = args.getOrNull(2) as? String ?: ""
            val value = args.getOrNull(3) as? String ?: ""
            if (el != null && name.isNotEmpty()) el.attr(name, value)
            ""
        }
        qjs.function("__cheerio_remove_attr") { args: Array<Any?> ->
            val el = elements[args.getOrNull(1) as? String]
            val name = args.getOrNull(2) as? String ?: ""
            if (el != null && name.isNotEmpty()) el.removeAttr(name)
            ""
        }
        qjs.function("__cheerio_is") { args: Array<Any?> ->
            val el = elements[args.getOrNull(1) as? String] ?: return@function false
            val selector = args.getOrNull(2) as? String ?: ""
            try {
                matches(el, normalizeSelector(selector))
            } catch (e: Exception) {
                false
            }
        }
    }

    /** Selector match test for one element (Ksoup has no Element.is). */
    private fun matches(el: Element, selector: String): Boolean {
        if (selector.isBlank()) return false
        val doc = el.ownerDocument() ?: return false
        return doc.select(selector).any { it === el }
    }

    private companion object {
        const val NONE = "__NONE__"
        const val UNDEFINED = "__UNDEFINED__"
        val CONTAINS_QUOTED = Regex(""":contains\(["']([^"']+)["']\)""")
    }
}

/** Native crypto behind the crypto-js shim (assets/plugs/bridge.js). */
internal object PlugCryptoBridge {

    fun install(qjs: QuickJs) {
        qjs.function("__crypto_get_random_values_hex") { args: Array<Any?> ->
            val n = (args.getOrNull(0) as? Number)?.toInt() ?: 0
            if (n <= 0) ""
            else ByteArray(n).also { SecureRandom().nextBytes(it) }.toHex()
        }
        qjs.function("__crypto_digest_hex_raw") { args: Array<Any?> ->
            val algo = args.getOrNull(0) as? String ?: "SHA256"
            val data = (args.getOrNull(1) as? String ?: "").hexToBytes()
            digest(algo, data).toHex()
        }
        qjs.function("__crypto_hmac_hex_raw") { args: Array<Any?> ->
            val algo = args.getOrNull(0) as? String ?: "SHA256"
            val key = (args.getOrNull(1) as? String ?: "").hexToBytes()
            val data = (args.getOrNull(2) as? String ?: "").hexToBytes()
            hmac(algo, key, data).toHex()
        }
        qjs.function("__crypto_pbkdf2_hex") { args: Array<Any?> ->
            val password = (args.getOrNull(0) as? String ?: "").hexToBytes()
            val salt = (args.getOrNull(1) as? String ?: "").hexToBytes()
            val iterations = (args.getOrNull(2) as? Number)?.toInt() ?: 1000
            val keyBits = (args.getOrNull(3) as? Number)?.toInt() ?: 256
            val algo = args.getOrNull(4) as? String ?: "SHA256"
            pbkdf2(password, salt, iterations, keyBits / 8, algo).toHex()
        }
        qjs.function("__crypto_aes_encrypt_hex") { args: Array<Any?> ->
            aes(args, encrypt = true)
        }
        qjs.function("__crypto_aes_decrypt_hex") { args: Array<Any?> ->
            aes(args, encrypt = false)
        }
    }

    private fun aes(args: Array<out Any?>, encrypt: Boolean): String {
        val mode = args.getOrNull(0) as? String ?: "AES-CBC"
        val key = (args.getOrNull(1) as? String ?: "").hexToBytes()
        val iv = (args.getOrNull(2) as? String ?: "").hexToBytes()
        val data = (args.getOrNull(3) as? String ?: "").hexToBytes()
        val noPadding = mode.endsWith("-NoPadding")
        val base = mode.removeSuffix("-NoPadding")
        val transformation = when (base) {
            "AES-ECB" -> if (noPadding) "AES/ECB/NoPadding" else "AES/ECB/PKCS5Padding"
            "AES-GCM" -> "AES/GCM/NoPadding"
            else -> if (noPadding) "AES/CBC/NoPadding" else "AES/CBC/PKCS5Padding"
        }
        val cipher = Cipher.getInstance(transformation)
        val keySpec = SecretKeySpec(key, "AES")
        if (base == "AES-ECB") {
            cipher.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, keySpec)
        } else if (base == "AES-GCM") {
            cipher.init(
                if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
                keySpec,
                javax.crypto.spec.GCMParameterSpec(128, iv)
            )
        } else {
            cipher.init(
                if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
                keySpec,
                IvParameterSpec(iv)
            )
        }
        return cipher.doFinal(data).toHex()
    }

    private fun jcaDigest(algo: String): String = when (algo.uppercase()) {
        "MD5" -> "MD5"
        "SHA1", "SHA-1" -> "SHA-1"
        "SHA384", "SHA-384" -> "SHA-384"
        "SHA512", "SHA-512" -> "SHA-512"
        else -> "SHA-256"
    }

    private fun jcaMac(algo: String): String = when (algo.uppercase()) {
        "MD5" -> "HmacMD5"
        "SHA1", "SHA-1" -> "HmacSHA1"
        "SHA384", "SHA-384" -> "HmacSHA384"
        "SHA512", "SHA-512" -> "HmacSHA512"
        else -> "HmacSHA256"
    }

    private fun digest(algo: String, data: ByteArray): ByteArray =
        MessageDigest.getInstance(jcaDigest(algo)).digest(data)

    private fun hmac(algo: String, key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(jcaMac(algo))
        mac.init(SecretKeySpec(key, jcaMac(algo)))
        return mac.doFinal(data)
    }

    /**
     * PBKDF2 over the raw password BYTES (CryptoJS semantics — the
     * JCA PBEKeySpec char conversion would mangle non-ASCII input),
     * hand-rolled over HMAC so every Android API level behaves the
     * same (PBKDF2WithHmacSHA256 is API 26+ only).
     */
    private fun pbkdf2(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        keyBytes: Int,
        algo: String
    ): ByteArray {
        val macName = jcaMac(algo)
        val mac = Mac.getInstance(macName)
        mac.init(SecretKeySpec(password, macName))
        val hLen = mac.macLength
        val blocks = (keyBytes + hLen - 1) / hLen
        val out = ByteArray(blocks * hLen)
        for (block in 1..blocks) {
            var u = mac.doFinal(
                salt + byteArrayOf(
                    (block ushr 24).toByte(), (block ushr 16).toByte(),
                    (block ushr 8).toByte(), block.toByte()
                )
            )
            val t = u.copyOf()
            for (i in 1 until iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            System.arraycopy(t, 0, out, (block - 1) * hLen, hLen)
        }
        return out.copyOf(keyBytes)
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        val clean = replace(Regex("[^0-9a-fA-F]"), "")
        val padded = if (clean.length % 2 == 1) "0$clean" else clean
        return ByteArray(padded.length / 2) {
            padded.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }
}
