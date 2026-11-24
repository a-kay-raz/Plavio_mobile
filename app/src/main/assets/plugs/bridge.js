// Plugs native bridge shims (v0.14.65, item F).
//
// The shape the reference host uses (see the 2026-11-21 study):
// require('cheerio') and require('crypto-js') return THIN JS shims
// whose every real operation delegates to native Kotlin functions
// (__cheerio_* on a Ksoup DOM that lives in Kotlin, __crypto_* on
// java.security / javax.crypto). JS only ever holds string element
// ids and hex strings — there is no JS DOM and no JS crypto, so a
// plug run parses THIS file (~30 KB) instead of the 1.38 MB of
// library sources the legacy path evaluates. The host evaluates
// this file right after the prelude, only in bridge mode, and the
// shims register themselves in the __plugModules registry the
// prelude's require() consults first.
(function (g) {
  'use strict';
  if (typeof g.__plugModules === 'undefined') g.__plugModules = {};

  // ------------------------------------------------------------------
  // cheerio shim
  // ------------------------------------------------------------------
  function parseIds(json) {
    try { return JSON.parse(json || '[]'); } catch (e) { return []; }
  }

  function createCheerioWrapperFromIds(docId, ids) {
    var wrapper = {
      _docId: docId,
      _elementIds: ids,
      length: ids.length,
      each: function (callback) {
        for (var i = 0; i < ids.length; i++) {
          var elWrapper = createCheerioWrapperFromIds(docId, [ids[i]]);
          if (callback.call(elWrapper, i, elWrapper) === false) break;
        }
        return wrapper;
      },
      find: function (sel) {
        var allIds = [];
        for (var i = 0; i < ids.length; i++) {
          allIds = allIds.concat(parseIds(__cheerio_find(docId, ids[i], sel)));
        }
        return createCheerioWrapperFromIds(docId, allIds);
      },
      children: function (sel) {
        var allIds = [];
        for (var i = 0; i < ids.length; i++) {
          allIds = allIds.concat(parseIds(__cheerio_children(docId, ids[i], sel || '')));
        }
        return createCheerioWrapperFromIds(docId, allIds);
      },
      parent: function () {
        var out = [];
        for (var i = 0; i < ids.length; i++) {
          var p = __cheerio_parent(docId, ids[i]);
          if (p && p !== '__NONE__' && out.indexOf(p) < 0) out.push(p);
        }
        return createCheerioWrapperFromIds(docId, out);
      },
      closest: function (sel) {
        var out = [];
        for (var i = 0; i < ids.length; i++) {
          var c = __cheerio_closest(docId, ids[i], sel);
          if (c && c !== '__NONE__' && out.indexOf(c) < 0) out.push(c);
        }
        return createCheerioWrapperFromIds(docId, out);
      },
      next: function () {
        var out = [];
        for (var i = 0; i < ids.length; i++) {
          var n = __cheerio_next(docId, ids[i]);
          if (n && n !== '__NONE__') out.push(n);
        }
        return createCheerioWrapperFromIds(docId, out);
      },
      prev: function () {
        var out = [];
        for (var i = 0; i < ids.length; i++) {
          var p = __cheerio_prev(docId, ids[i]);
          if (p && p !== '__NONE__') out.push(p);
        }
        return createCheerioWrapperFromIds(docId, out);
      },
      nextAll: function () {
        var allIds = [];
        for (var i = 0; i < ids.length; i++) {
          allIds = allIds.concat(parseIds(__cheerio_next_all(docId, ids[i])));
        }
        return createCheerioWrapperFromIds(docId, allIds);
      },
      prevAll: function () {
        var allIds = [];
        for (var i = 0; i < ids.length; i++) {
          allIds = allIds.concat(parseIds(__cheerio_prev_all(docId, ids[i])));
        }
        return createCheerioWrapperFromIds(docId, allIds);
      },
      text: function () {
        if (ids.length === 0) return '';
        return __cheerio_text(docId, ids.join(','));
      },
      html: function () {
        if (ids.length === 0) return null;
        return __cheerio_inner_html(docId, ids[0]);
      },
      attr: function (name, value) {
        if (ids.length === 0) return undefined;
        if (value === undefined) {
          var v = __cheerio_attr(docId, ids[0], name);
          return v === '__UNDEFINED__' ? undefined : v;
        }
        for (var i = 0; i < ids.length; i++) __cheerio_set_attr(docId, ids[i], name, String(value));
        return wrapper;
      },
      removeAttr: function (name) {
        for (var i = 0; i < ids.length; i++) __cheerio_remove_attr(docId, ids[i], name);
        return wrapper;
      },
      prop: function (name) {
        return wrapper.attr(name);
      },
      val: function () {
        if (ids.length === 0) return undefined;
        var v = wrapper.attr('value');
        return v === undefined ? '' : v;
      },
      is: function (sel) {
        if (ids.length === 0) return false;
        if (typeof sel === 'function') return !!sel.call(wrapper, 0, wrapper);
        return !!__cheerio_is(docId, ids[0], sel);
      },
      has: function (sel) {
        var out = [];
        for (var i = 0; i < ids.length; i++) {
          if (parseIds(__cheerio_find(docId, ids[i], sel)).length > 0) out.push(ids[i]);
        }
        return createCheerioWrapperFromIds(docId, out);
      },
      first: function () {
        return createCheerioWrapperFromIds(docId, ids.length > 0 ? [ids[0]] : []);
      },
      last: function () {
        return createCheerioWrapperFromIds(docId, ids.length > 0 ? [ids[ids.length - 1]] : []);
      },
      eq: function (index) {
        var i = index < 0 ? ids.length + index : index;
        if (i >= 0 && i < ids.length) return createCheerioWrapperFromIds(docId, [ids[i]]);
        return createCheerioWrapperFromIds(docId, []);
      },
      get: function (index) {
        if (typeof index === 'number') {
          var i = index < 0 ? ids.length + index : index;
          if (i >= 0 && i < ids.length) return createCheerioWrapperFromIds(docId, [ids[i]]);
          return undefined;
        }
        return ids.map(function (id) { return createCheerioWrapperFromIds(docId, [id]); });
      },
      map: function (callback) {
        var results = [];
        for (var i = 0; i < ids.length; i++) {
          var elWrapper = createCheerioWrapperFromIds(docId, [ids[i]]);
          var result = callback.call(elWrapper, i, elWrapper);
          if (result !== undefined && result !== null) results.push(result);
        }
        return {
          length: results.length,
          get: function (index) { return typeof index === 'number' ? results[index] : results; },
          toArray: function () { return results; }
        };
      },
      filter: function (selectorOrCallback) {
        if (typeof selectorOrCallback === 'function') {
          var filteredIds = [];
          for (var i = 0; i < ids.length; i++) {
            var elWrapper = createCheerioWrapperFromIds(docId, [ids[i]]);
            if (selectorOrCallback.call(elWrapper, i, elWrapper)) filteredIds.push(ids[i]);
          }
          return createCheerioWrapperFromIds(docId, filteredIds);
        }
        if (typeof selectorOrCallback === 'string') {
          var bySel = [];
          for (var j = 0; j < ids.length; j++) {
            if (__cheerio_is(docId, ids[j], selectorOrCallback)) bySel.push(ids[j]);
          }
          return createCheerioWrapperFromIds(docId, bySel);
        }
        return wrapper;
      },
      toArray: function () {
        return ids.map(function (id) { return createCheerioWrapperFromIds(docId, [id]); });
      }
    };
    return wrapper;
  }

  function createCheerioWrapper(docId, selector) {
    if (typeof selector === 'string') {
      return createCheerioWrapperFromIds(docId, parseIds(__cheerio_select(docId, selector)));
    }
    return createCheerioWrapperFromIds(docId, []);
  }

  var cheerioModule = {
    load: function (html) {
      var docId = __cheerio_load(String(html == null ? '' : html));
      var $ = function (selector, context) {
        if (selector && selector._elementIds) return selector;
        if (typeof selector === 'string' && selector.charAt(0) === '<') {
          // Fragment parse: load it as its own document and select the root.
          var fragDoc = __cheerio_load(selector);
          return createCheerioWrapper(fragDoc, 'body > *');
        }
        if (context && context._elementIds && context._elementIds.length > 0) {
          var allIds = [];
          for (var i = 0; i < context._elementIds.length; i++) {
            allIds = allIds.concat(parseIds(__cheerio_find(docId, context._elementIds[i], selector)));
          }
          return createCheerioWrapperFromIds(docId, allIds);
        }
        return createCheerioWrapper(docId, selector);
      };
      $.html = function (el) {
        if (el && el._elementIds && el._elementIds.length > 0) {
          return __cheerio_html(docId, el._elementIds[0]);
        }
        return __cheerio_html(docId, '');
      };
      $.text = function (el) {
        if (el && el._elementIds) return el.text();
        return '';
      };
      return $;
    }
  };
  g.__plugModules['cheerio'] = cheerioModule;

  // ------------------------------------------------------------------
  // crypto-js shim — WordArray/encoders in JS (cheap byte shuffling),
  // every primitive delegated to the native bridge (hex transport).
  // ------------------------------------------------------------------
  var WordArray = {
    init: function (words, sigBytes) {
      this.words = words || [];
      this.sigBytes = sigBytes != undefined ? sigBytes : this.words.length * 4;
    },
    toString: function (encoder) {
      return (encoder || CryptoJS.enc.Hex).stringify(this);
    },
    concat: function (wordArray) {
      var thisWords = this.words;
      var thatWords = wordArray.words;
      var thisSigBytes = this.sigBytes;
      var thatSigBytes = wordArray.sigBytes;
      this.clamp();
      for (var i = 0; i < thatSigBytes; i++) {
        var thatByte = (thatWords[i >>> 2] >>> (24 - (i % 4) * 8)) & 0xff;
        thisWords[(thisSigBytes + i) >>> 2] |= thatByte << (24 - ((thisSigBytes + i) % 4) * 8);
      }
      this.sigBytes += thatSigBytes;
      return this;
    },
    clamp: function () {
      var words = this.words;
      var sigBytes = this.sigBytes;
      if (sigBytes % 4) {
        words[sigBytes >>> 2] &= 0xffffffff << (32 - (sigBytes % 4) * 8);
      }
      words.length = Math.ceil(sigBytes / 4);
      return this;
    },
    clone: function () {
      return __wordArrayCreate(this.words.slice(0), this.sigBytes);
    }
  };

  function __wordArrayCreate(words, sigBytes) {
    var wa = Object.create(WordArray);
    wa.init(words, sigBytes);
    return wa;
  }

  function __isWordArray(value) {
    return value && typeof value === 'object' && Array.isArray(value.words) && typeof value.sigBytes === 'number';
  }

  function __toUint8Array(data) {
    if (!data) return new Uint8Array(0);
    if (data instanceof Uint8Array) return data;
    if (data instanceof ArrayBuffer) return new Uint8Array(data);
    if (typeof ArrayBuffer !== 'undefined' && ArrayBuffer.isView && ArrayBuffer.isView(data)) {
      return new Uint8Array(data.buffer, data.byteOffset || 0, data.byteLength);
    }
    if (Array.isArray(data)) return new Uint8Array(data);
    if (typeof data.length === 'number') return new Uint8Array(Array.prototype.slice.call(data));
    return new Uint8Array(0);
  }

  function __wordArrayToBytes(wordArray) {
    if (!__isWordArray(wordArray)) {
      return typeof wordArray === 'string' ? new TextEncoder().encode(wordArray) : __toUint8Array(wordArray);
    }
    var bytes = new Uint8Array(wordArray.sigBytes);
    for (var i = 0; i < wordArray.sigBytes; i++) {
      bytes[i] = (wordArray.words[i >>> 2] >>> (24 - (i % 4) * 8)) & 0xff;
    }
    return bytes;
  }

  function __bytesToWordArray(bytes) {
    bytes = __toUint8Array(bytes);
    var words = [];
    for (var i = 0; i < bytes.length; i++) {
      words[i >>> 2] |= (bytes[i] & 0xff) << (24 - (i % 4) * 8);
    }
    return __wordArrayCreate(words, bytes.length);
  }

  function __normalizeWordArrayInput(value) {
    if (__isWordArray(value)) return __wordArrayToBytes(value);
    if (typeof value === 'string') return new TextEncoder().encode(value);
    return __toUint8Array(value);
  }

  function __bytesToHex(bytes) {
    bytes = __toUint8Array(bytes);
    var out = [];
    for (var i = 0; i < bytes.length; i++) {
      var hex = bytes[i].toString(16);
      out.push(hex.length < 2 ? '0' + hex : hex);
    }
    return out.join('');
  }

  function __hexToBytes(hex) {
    hex = String(hex || '').replace(/[^0-9a-fA-F]/g, '');
    if (hex.length % 2) hex = '0' + hex;
    var bytes = new Uint8Array(hex.length / 2);
    for (var i = 0; i < hex.length; i += 2) {
      bytes[i / 2] = parseInt(hex.substr(i, 2), 16) & 0xff;
    }
    return bytes;
  }

  function __concatBytes() {
    var total = 0;
    var parts = [];
    for (var i = 0; i < arguments.length; i++) {
      var part = __toUint8Array(arguments[i]);
      parts.push(part);
      total += part.length;
    }
    var out = new Uint8Array(total);
    var offset = 0;
    for (var j = 0; j < parts.length; j++) {
      out.set(parts[j], offset);
      offset += parts[j].length;
    }
    return out;
  }

  function __normalizeHashName(hash) {
    var name = hash && hash.name ? hash.name : hash;
    name = String(name || 'SHA-256').toUpperCase().replace(/[^A-Z0-9]/g, '');
    if (name === 'SHA1' || name === 'SHA256' || name === 'SHA384' || name === 'SHA512' || name === 'MD5') return name;
    throw new Error('Unsupported hash algorithm: ' + name);
  }

  function __aesModeName(mode, padding) {
    var normalized = String(mode || 'AES-CBC').toUpperCase();
    if (normalized.indexOf('AES-GCM') >= 0) normalized = 'AES-GCM';
    else if (normalized.indexOf('ECB') >= 0) normalized = 'AES-ECB';
    else normalized = 'AES-CBC';
    if (padding === CryptoJS.pad.NoPadding || padding === 'NoPadding') normalized += '-NoPadding';
    return normalized;
  }

  function __nativeDigestBytes(hash, dataBytes) {
    if (typeof __crypto_digest_hex_raw === 'undefined') throw new Error('Native digest bridge is unavailable');
    return __hexToBytes(__crypto_digest_hex_raw(__normalizeHashName(hash), __bytesToHex(dataBytes)));
  }

  function __nativeHmacBytes(hash, keyBytes, dataBytes) {
    if (typeof __crypto_hmac_hex_raw === 'undefined') throw new Error('Native HMAC bridge is unavailable');
    return __hexToBytes(__crypto_hmac_hex_raw(__normalizeHashName(hash), __bytesToHex(keyBytes), __bytesToHex(dataBytes)));
  }

  function __nativePbkdf2Bytes(passwordBytes, saltBytes, iterations, keySizeBits, hash) {
    if (typeof __crypto_pbkdf2_hex === 'undefined') throw new Error('Native PBKDF2 bridge is unavailable');
    return __hexToBytes(__crypto_pbkdf2_hex(__bytesToHex(passwordBytes), __bytesToHex(saltBytes), iterations, keySizeBits, __normalizeHashName(hash)));
  }

  function __nativeAesBytes(encrypt, mode, keyBytes, ivBytes, dataBytes) {
    var fn = encrypt ? __crypto_aes_encrypt_hex : __crypto_aes_decrypt_hex;
    if (typeof fn === 'undefined') throw new Error('Native AES bridge is unavailable');
    return __hexToBytes(fn(mode, __bytesToHex(keyBytes), __bytesToHex(ivBytes), __bytesToHex(dataBytes)));
  }

  // OpenSSL EVP_BytesToKey (MD5, one iteration per block — the
  // CryptoJS default) over the native MD5.
  function __evpKdf(passwordBytes, saltBytes, keySizeBytes, ivSizeBytes) {
    var targetSize = keySizeBytes + ivSizeBytes;
    var derived = new Uint8Array(targetSize);
    var block = new Uint8Array(0);
    var offset = 0;
    while (offset < targetSize) {
      block = __nativeDigestBytes('MD5', __concatBytes(block, passwordBytes, saltBytes || new Uint8Array(0)));
      var take = Math.min(block.length, targetSize - offset);
      derived.set(block.subarray(0, take), offset);
      offset += take;
    }
    return {
      key: derived.subarray(0, keySizeBytes),
      iv: derived.subarray(keySizeBytes, keySizeBytes + ivSizeBytes)
    };
  }

  function __opensslSaltHeader() {
    return new Uint8Array([83, 97, 108, 116, 101, 100, 95, 95]); // "Salted__"
  }

  function __hasOpenSslSaltHeader(bytes) {
    var header = __opensslSaltHeader();
    if (!bytes || bytes.length < 16) return false;
    for (var i = 0; i < header.length; i++) {
      if (bytes[i] !== header[i]) return false;
    }
    return true;
  }

  function __makeCipherParams(ciphertext, key, iv, salt, mode) {
    return {
      ciphertext: __bytesToWordArray(ciphertext),
      key: key ? __bytesToWordArray(key) : undefined,
      iv: iv ? __bytesToWordArray(iv) : undefined,
      salt: salt ? __bytesToWordArray(salt) : undefined,
      mode: mode,
      toString: function (formatter) {
        return (formatter || CryptoJS.format.OpenSSL).stringify(this);
      }
    };
  }

  var CryptoJS = {
    enc: {
      Hex: {
        stringify: function (wordArray) { return __bytesToHex(__wordArrayToBytes(wordArray)); },
        parse: function (hexStr) { return __bytesToWordArray(__hexToBytes(hexStr)); }
      },
      Utf8: {
        stringify: function (wordArray) { return new TextDecoder('utf-8').decode(__wordArrayToBytes(wordArray)); },
        parse: function (utf8Str) { return __bytesToWordArray(new TextEncoder().encode(String(utf8Str))); }
      },
      Latin1: {
        stringify: function (wordArray) {
          var bytes = __wordArrayToBytes(wordArray);
          var out = '';
          for (var i = 0; i < bytes.length; i++) out += String.fromCharCode(bytes[i]);
          return out;
        },
        parse: function (str) {
          str = String(str || '');
          var bytes = new Uint8Array(str.length);
          for (var i = 0; i < str.length; i++) bytes[i] = str.charCodeAt(i) & 0xff;
          return __bytesToWordArray(bytes);
        }
      },
      Base64: {
        // Pure-JS codec: the host's atob/btoa pass through a
        // String, which mangles bytes >= 0x80 (they come back as
        // U+FFFD) — unusable for binary payloads. crypto-js's own
        // decoder is table-driven JS for the same reason.
        stringify: function (wordArray) {
          var bytes = __wordArrayToBytes(wordArray);
          var out = '';
          var alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
          for (var i = 0; i < bytes.length; i += 3) {
            var b0 = bytes[i];
            var b1 = i + 1 < bytes.length ? bytes[i + 1] : 0;
            var b2 = i + 2 < bytes.length ? bytes[i + 2] : 0;
            out += alphabet.charAt(b0 >> 2);
            out += alphabet.charAt(((b0 & 3) << 4) | (b1 >> 4));
            out += i + 1 < bytes.length ? alphabet.charAt(((b1 & 15) << 2) | (b2 >> 6)) : '=';
            out += i + 2 < bytes.length ? alphabet.charAt(b2 & 63) : '=';
          }
          return out;
        },
        parse: function (base64Str) {
          var str = String(base64Str || '').replace(/[^A-Za-z0-9+/=]/g, '');
          var bytes = [];
          var alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
          for (var i = 0; i < str.length; i += 4) {
            var e0 = alphabet.indexOf(str.charAt(i));
            var e1 = alphabet.indexOf(str.charAt(i + 1));
            var e2 = str.charAt(i + 2) === '=' ? -1 : alphabet.indexOf(str.charAt(i + 2));
            var e3 = str.charAt(i + 3) === '=' ? -1 : alphabet.indexOf(str.charAt(i + 3));
            if (e0 < 0 || e1 < 0) break;
            bytes.push(((e0 << 2) | (e1 >> 4)) & 0xff);
            if (e2 >= 0) bytes.push((((e1 & 15) << 4) | (e2 >> 2)) & 0xff);
            if (e3 >= 0) bytes.push((((e2 & 3) << 6) | e3) & 0xff);
          }
          return __bytesToWordArray(new Uint8Array(bytes));
        }
      },
      Base64url: {
        stringify: function (wordArray) {
          return CryptoJS.enc.Base64.stringify(wordArray).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
        },
        parse: function (str) {
          str = String(str || '').replace(/-/g, '+').replace(/_/g, '/');
          while (str.length % 4) str += '=';
          return CryptoJS.enc.Base64.parse(str);
        }
      }
    },
    lib: {
      WordArray: {
        create: function (words, sigBytes) {
          if (words == null) return __wordArrayCreate([], sigBytes || 0);
          if (__isWordArray(words)) return words.clone();
          if (typeof words === 'string') return CryptoJS.enc.Utf8.parse(words);
          if (words instanceof ArrayBuffer || (typeof ArrayBuffer !== 'undefined' && ArrayBuffer.isView && ArrayBuffer.isView(words))) {
            var bytes = __toUint8Array(words);
            return __bytesToWordArray(sigBytes != undefined ? bytes.subarray(0, sigBytes) : bytes);
          }
          return __wordArrayCreate(words, sigBytes);
        },
        random: function (nBytes) {
          var bytes = new Uint8Array(nBytes || 0);
          if (typeof __crypto_get_random_values_hex !== 'undefined') {
            var rnd = __hexToBytes(__crypto_get_random_values_hex(bytes.length));
            bytes.set(rnd.subarray(0, bytes.length));
          } else {
            g.crypto.getRandomValues(bytes);
          }
          return __bytesToWordArray(bytes);
        }
      },
      CipherParams: {
        create: function (params) {
          params = params || {};
          params.toString = params.toString || function (formatter) {
            return (formatter || CryptoJS.format.OpenSSL).stringify(this);
          };
          return params;
        }
      }
    },
    format: {
      OpenSSL: {
        stringify: function (cipherParams) {
          var cipherBytes = __wordArrayToBytes(cipherParams.ciphertext);
          var out = cipherParams.salt
            ? __concatBytes(__opensslSaltHeader(), __wordArrayToBytes(cipherParams.salt), cipherBytes)
            : cipherBytes;
          return CryptoJS.enc.Base64.stringify(__bytesToWordArray(out));
        },
        parse: function (str) {
          var bytes = __wordArrayToBytes(CryptoJS.enc.Base64.parse(str));
          if (__hasOpenSslSaltHeader(bytes)) {
            return CryptoJS.lib.CipherParams.create({
              salt: __bytesToWordArray(bytes.subarray(8, 16)),
              ciphertext: __bytesToWordArray(bytes.subarray(16))
            });
          }
          return CryptoJS.lib.CipherParams.create({ ciphertext: __bytesToWordArray(bytes) });
        }
      }
    },
    mode: { CBC: 'AES-CBC', GCM: 'AES-GCM', ECB: 'AES-ECB' },
    pad: { Pkcs7: 'Pkcs7', NoPadding: 'NoPadding' },
    algo: { MD5: 'MD5', SHA1: 'SHA1', SHA256: 'SHA256', SHA384: 'SHA384', SHA512: 'SHA512', AES: 'AES' },
    MD5: function (m) { return __bytesToWordArray(__nativeDigestBytes('MD5', __normalizeWordArrayInput(m))); },
    SHA1: function (m) { return __bytesToWordArray(__nativeDigestBytes('SHA1', __normalizeWordArrayInput(m))); },
    SHA256: function (m) { return __bytesToWordArray(__nativeDigestBytes('SHA256', __normalizeWordArrayInput(m))); },
    SHA384: function (m) { return __bytesToWordArray(__nativeDigestBytes('SHA384', __normalizeWordArrayInput(m))); },
    SHA512: function (m) { return __bytesToWordArray(__nativeDigestBytes('SHA512', __normalizeWordArrayInput(m))); },
    HmacMD5: function (m, k) { return __bytesToWordArray(__nativeHmacBytes('MD5', __normalizeWordArrayInput(k), __normalizeWordArrayInput(m))); },
    HmacSHA1: function (m, k) { return __bytesToWordArray(__nativeHmacBytes('SHA1', __normalizeWordArrayInput(k), __normalizeWordArrayInput(m))); },
    HmacSHA256: function (m, k) { return __bytesToWordArray(__nativeHmacBytes('SHA256', __normalizeWordArrayInput(k), __normalizeWordArrayInput(m))); },
    HmacSHA384: function (m, k) { return __bytesToWordArray(__nativeHmacBytes('SHA384', __normalizeWordArrayInput(k), __normalizeWordArrayInput(m))); },
    HmacSHA512: function (m, k) { return __bytesToWordArray(__nativeHmacBytes('SHA512', __normalizeWordArrayInput(k), __normalizeWordArrayInput(m))); },
    PBKDF2: function (pass, salt, options) {
      options = options || {};
      var pBytes = __normalizeWordArrayInput(pass);
      var sBytes = __normalizeWordArrayInput(salt);
      var iter = options.iterations || 1000;
      var kSize = options.keySize || 8;
      // crypto-js 4.x's default PBKDF2 hasher is SHA256 (the
      // library this shim replaces) — not the 3.x SHA1 default.
      var algo = options.hasher || 'SHA256';
      return __bytesToWordArray(__nativePbkdf2Bytes(pBytes, sBytes, iter, kSize * 32, algo));
    },
    AES: {
      encrypt: function (message, key, options) {
        options = options || {};
        var data = __normalizeWordArrayInput(message);
        var kBytes, ivBytes, saltBytes;
        if (typeof key === 'string') {
          saltBytes = options.salt ? __wordArrayToBytes(options.salt) : __wordArrayToBytes(CryptoJS.lib.WordArray.random(8));
          var derived = __evpKdf(new TextEncoder().encode(key), saltBytes, 32, 16);
          kBytes = derived.key;
          ivBytes = options.iv ? __wordArrayToBytes(options.iv) : derived.iv;
        } else {
          kBytes = __wordArrayToBytes(key);
          ivBytes = options.iv ? __wordArrayToBytes(options.iv) : new Uint8Array(0);
        }
        var mode = __aesModeName(options.mode || 'AES-CBC', options.padding);
        var resBytes = __nativeAesBytes(true, mode, kBytes, ivBytes, data);
        return __makeCipherParams(resBytes, kBytes, ivBytes, saltBytes, mode);
      },
      decrypt: function (cipher, key, options) {
        options = options || {};
        var cipherParams = typeof cipher === 'string' ? CryptoJS.format.OpenSSL.parse(cipher) : cipher;
        var data = cipherParams.ciphertext ? __wordArrayToBytes(cipherParams.ciphertext) : __toUint8Array(cipherParams);
        var kBytes, ivBytes;
        if (typeof key === 'string') {
          var saltBytes = options.salt ? __wordArrayToBytes(options.salt) : (cipherParams.salt ? __wordArrayToBytes(cipherParams.salt) : new Uint8Array(0));
          var derived = __evpKdf(new TextEncoder().encode(key), saltBytes, 32, 16);
          kBytes = derived.key;
          ivBytes = options.iv ? __wordArrayToBytes(options.iv) : derived.iv;
        } else {
          kBytes = __wordArrayToBytes(key);
          ivBytes = options.iv ? __wordArrayToBytes(options.iv) : new Uint8Array(0);
        }
        var mode = __aesModeName(options.mode || 'AES-CBC', options.padding);
        return __bytesToWordArray(__nativeAesBytes(false, mode, kBytes, ivBytes, data));
      }
    }
  };
  g.__plugModules['crypto-js'] = CryptoJS;
  g.CryptoJS = CryptoJS;
})(globalThis);
