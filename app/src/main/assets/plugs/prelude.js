// Plugs host prelude (Plugs v1, v0.14.60 — QuickJS host).
// Injected into every JS plug's runtime before the plug file runs.
// Provides the browser-ish globals Nuvio-format plugs expect:
// fetch (backed by the host's OkHttp bridge __plugFetch), Headers,
// URL/URLSearchParams, AbortController, setTimeout/clearTimeout
// (over the host's async __plugDelay), btoa/atob, a WebCrypto-lite
// getRandomValues, TextEncoder/TextDecoder, and require() for the
// host-supplied modules (cheerio, crypto-js, axios).
//
// Every global is installed ONLY when the runtime does not already
// provide it, and plug code itself runs inside a function scope
// (see JsPlugHost), so a plug's own top-level declarations shadow
// these instead of colliding with them.
(function () {
  var g = globalThis;
  if (typeof g.window === 'undefined') g.window = g;
  if (typeof g.self === 'undefined') g.self = g;
  // Node-flavoured plugs reference the bare name `global` (the
  // reference hosts install this exact alias — JsBindings line 8);
  // without it they die with "ReferenceError: 'global' is not
  // defined" before their first fetch (DVDPlay, MalluMV —
  // diagnosis 2026-11-20). Guarded like every alias here.
  if (typeof g.global === 'undefined') g.global = g;

  // --- timers -------------------------------------------------------
  // Backed by the host's async __plugDelay(ms): its promise resolves
  // after the delay and the callback runs as a normal promise job —
  // the same shape a browser event loop gives, so a plug's own
  // timeout races behave the way the plug was written to expect.
  var timerSeq = 0;
  var timerLive = {};
  if (typeof g.setTimeout === 'undefined') {
    g.setTimeout = function (fn, ms) {
      var id = ++timerSeq;
      timerLive[id] = true;
      if (typeof __plugDelay === 'function') {
        __plugDelay(Math.max(0, ms || 0)).then(function () {
          if (timerLive[id]) {
            delete timerLive[id];
            try { fn(); } catch (e) {
              if (g.console) console.error('[Plugs] timer error: ' + ((e && e.message) || e));
            }
          }
        }, function () { /* a failed delay simply never fires */ });
      }
      return id;
    };
  }
  if (typeof g.clearTimeout === 'undefined') {
    g.clearTimeout = function (id) { delete timerLive[id]; };
  }

  // --- AbortController / AbortSignal --------------------------------
  // Third-party plugs construct `new AbortController()` for fetch
  // timeouts; without it they die with a ReferenceError (diagnosis
  // 2026-11-20). Minimal spec-shaped pair: an aborted flag plus
  // 'abort' listeners. fetch() below honours a pre-aborted signal.
  if (typeof g.AbortSignal === 'undefined') {
    g.AbortSignal = function () {
      this.aborted = false;
      this.onabort = null;
      this._listeners = [];
    };
    g.AbortSignal.prototype.addEventListener = function (type, fn) {
      if (type === 'abort' && typeof fn === 'function') this._listeners.push(fn);
    };
    g.AbortSignal.prototype.removeEventListener = function (type, fn) {
      if (type !== 'abort') return;
      this._listeners = this._listeners.filter(function (f) { return f !== fn; });
    };
    g.AbortSignal.prototype._fireAbort = function () {
      var ls = this._listeners.slice();
      for (var i = 0; i < ls.length; i++) {
        try { ls[i].call(this, { type: 'abort', target: this }); } catch (e) { /* listener errors are swallowed */ }
      }
      if (typeof this.onabort === 'function') {
        try { this.onabort({ type: 'abort', target: this }); } catch (e) { /* as above */ }
      }
    };
  }
  if (typeof g.AbortController === 'undefined') {
    g.AbortController = function () { this.signal = new g.AbortSignal(); };
    g.AbortController.prototype.abort = function () {
      if (this.signal.aborted) return;
      this.signal.aborted = true;
      this.signal._fireAbort();
    };
  }
  function abortError() {
    var e = new Error('The operation was aborted');
    e.name = 'AbortError';
    return e;
  }

  // --- URLSearchParams ------------------------------------------------
  if (typeof g.URLSearchParams === 'undefined') {
    g.URLSearchParams = function (init) {
      this._pairs = [];
      if (init == null) return;
      if (typeof init === 'string') {
        var s = init.charAt(0) === '?' ? init.substring(1) : init;
        if (!s) return;
        var self = this;
        s.split('&').forEach(function (part) {
          if (!part) return;
          var eq = part.indexOf('=');
          var k = eq < 0 ? part : part.substring(0, eq);
          var v = eq < 0 ? '' : part.substring(eq + 1);
          try {
            k = decodeURIComponent(k.replace(/\+/g, ' '));
            v = decodeURIComponent(v.replace(/\+/g, ' '));
          } catch (e) { /* keep raw */ }
          self._pairs.push([k, v]);
        });
      } else if (Array.isArray(init)) {
        for (var i = 0; i < init.length; i++) {
          this._pairs.push([String(init[i][0]), String(init[i][1])]);
        }
      } else if (typeof init === 'object') {
        for (var k2 in init) this._pairs.push([k2, String(init[k2])]);
      }
    };
    g.URLSearchParams.prototype.get = function (n) {
      for (var i = 0; i < this._pairs.length; i++) {
        if (this._pairs[i][0] === n) return this._pairs[i][1];
      }
      return null;
    };
    g.URLSearchParams.prototype.getAll = function (n) {
      var out = [];
      for (var i = 0; i < this._pairs.length; i++) {
        if (this._pairs[i][0] === n) out.push(this._pairs[i][1]);
      }
      return out;
    };
    g.URLSearchParams.prototype.has = function (n) { return this.get(n) !== null; };
    g.URLSearchParams.prototype.set = function (n, v) {
      var done = false;
      var out = [];
      for (var i = 0; i < this._pairs.length; i++) {
        if (this._pairs[i][0] === n) {
          if (!done) { out.push([n, String(v)]); done = true; }
        } else out.push(this._pairs[i]);
      }
      if (!done) out.push([n, String(v)]);
      this._pairs = out;
    };
    g.URLSearchParams.prototype.append = function (n, v) {
      this._pairs.push([n, String(v)]);
    };
    g.URLSearchParams.prototype.delete = function (n) {
      this._pairs = this._pairs.filter(function (p) { return p[0] !== n; });
    };
    g.URLSearchParams.prototype.forEach = function (fn) {
      for (var i = 0; i < this._pairs.length; i++) fn(this._pairs[i][1], this._pairs[i][0], this);
    };
    g.URLSearchParams.prototype.toString = function () {
      return this._pairs.map(function (p) {
        return encodeURIComponent(p[0]).replace(/%20/g, '+') + '=' +
          encodeURIComponent(p[1]).replace(/%20/g, '+');
      }).join('&');
    };
  }

  // --- fetch --------------------------------------------------------
  function headersToObject(h) {
    if (!h) return {};
    if (typeof h === 'object') {
      if (typeof h.forEach === 'function' && typeof h.get === 'function') {
        var o = {};
        h.forEach(function (v, k) { o[k] = v; });
        return o;
      }
      return h;
    }
    return {};
  }
  function PlugResponse(r) {
    this.status = r.status;
    this.statusText = r.statusText || '';
    this.ok = r.status >= 200 && r.status < 300;
    this.url = r.url || '';
    this._body = r.body || '';
    this._headers = r.headers || {};
    var self = this;
    this.headers = {
      // A header the server sent more than once arrives as an
      // ARRAY of its separate values (the host no longer comma-
      // joins them — the join mangled Set-Cookie, whose values
      // contain commas of their own). get() follows the fetch
      // spec and joins repeats with ", "; getSetCookie() is the
      // faithful route: the separate cookie strings, unmangled.
      get: function (name) {
        var want = String(name).toLowerCase();
        for (var k in self._headers) {
          if (k.toLowerCase() === want) {
            var v = self._headers[k];
            return Array.isArray(v) ? v.join(', ') : v;
          }
        }
        return null;
      },
      getSetCookie: function () {
        for (var k in self._headers) {
          if (k.toLowerCase() === 'set-cookie') {
            var v = self._headers[k];
            return Array.isArray(v) ? v.slice() : [v];
          }
        }
        return [];
      },
      has: function (name) { return this.get(name) !== null; }
    };
  }
  PlugResponse.prototype.text = function () { return Promise.resolve(this._body); };
  PlugResponse.prototype.json = function () {
    var b = this._body;
    return Promise.resolve().then(function () { return JSON.parse(b); });
  };
  PlugResponse.prototype.clone = function () { return this; };
  if (typeof g.fetch === 'undefined') {
    g.fetch = function (url, options) {
      options = options || {};
      if (options.signal && options.signal.aborted) {
        return Promise.reject(abortError());
      }
      return new Promise(function (resolve, reject) {
        var raw;
        try {
          raw = __plugFetch(
            String(url),
            String(options.method || 'GET'),
            JSON.stringify(headersToObject(options.headers)),
            options.body == null ? null : String(options.body),
            // fetch redirect mode (v0.14.58): 'manual' tells the
            // host bridge not to follow, so the plug sees the raw
            // 3xx status + Location header. Anything else follows,
            // exactly as before this option was honoured.
            options.redirect === 'manual' ? 'manual' : 'follow'
          );
        } catch (e) {
          reject(new Error('fetch failed: ' + ((e && e.message) || e)));
          return;
        }
        var parsed;
        try { parsed = JSON.parse(raw); }
        catch (e2) { reject(new Error('fetch failed: bad host response')); return; }
        // Host network failures arrive as an error envelope (the
        // bridge never throws host exceptions into JS): surface
        // them as an ordinary fetch rejection, like a real network
        // error.
        if (parsed && parsed.error) {
          reject(new Error('fetch failed: ' + parsed.error));
          return;
        }
        resolve(new PlugResponse(parsed));
      });
    };
  }
  if (typeof g.Headers === 'undefined') {
    g.Headers = function (init) {
      this._m = {};
      if (init) {
        var o = headersToObject(init);
        for (var k in o) this._m[k.toLowerCase()] = String(o[k]);
      }
    };
    g.Headers.prototype.get = function (n) {
      var v = this._m[String(n).toLowerCase()];
      return v === undefined ? null : v;
    };
    g.Headers.prototype.set = function (n, v) { this._m[String(n).toLowerCase()] = String(v); };
    g.Headers.prototype.append = function (n, v) { this.set(n, v); };
    g.Headers.prototype.has = function (n) { return this.get(n) !== null; };
    g.Headers.prototype.forEach = function (fn) { for (var k in this._m) fn(this._m[k], k); };
  }

  // --- URL (minimal: absolute parse + relative resolution) ----------
  function parseUrl(u) {
    var m = /^((https?:)\/\/([^/?#]*))?([^?#]*)(\?[^#]*)?(#.*)?$/.exec(String(u));
    if (!m) return null;
    return {
      origin: m[1] || '', protocol: m[2] || '', host: m[3] || '',
      pathname: m[4] || '/', search: m[5] || '', hash: m[6] || ''
    };
  }
  if (typeof g.URL === 'undefined') {
    g.URL = function (url, base) {
      var u = String(url);
      if (base && !/^[a-z]+:\/\//i.test(u)) {
        var b = parseUrl(base);
        if (b && b.origin) {
          if (u.charAt(0) === '/') u = b.origin + u;
          else u = b.origin + b.pathname.replace(/[^/]*$/, '') + u;
        }
      }
      var p = parseUrl(u) || { origin: '', protocol: '', host: '', pathname: u, search: '', hash: '' };
      this.href = u;
      this.origin = p.origin;
      this.protocol = p.protocol;
      this.host = p.host;
      this.hostname = p.host.split(':')[0];
      this.pathname = p.pathname;
      this.search = p.search;
      this.hash = p.hash;
      this.searchParams = new g.URLSearchParams(p.search);
    };
    g.URL.prototype.toString = function () { return this.href; };
  }

  // --- TextEncoder / TextDecoder (UTF-8) ------------------------------
  if (typeof g.TextEncoder === 'undefined') {
    g.TextEncoder = function () {};
    g.TextEncoder.prototype.encode = function (s) {
      var out = [];
      s = String(s);
      for (var i = 0; i < s.length; i++) {
        var c = s.charCodeAt(i);
        if (c < 0x80) out.push(c);
        else if (c < 0x800) out.push(0xC0 | (c >> 6), 0x80 | (c & 0x3F));
        else if (c >= 0xD800 && c <= 0xDBFF && i + 1 < s.length) {
          var cp = 0x10000 + ((c - 0xD800) << 10) + (s.charCodeAt(++i) - 0xDC00);
          out.push(0xF0 | (cp >> 18), 0x80 | ((cp >> 12) & 0x3F), 0x80 | ((cp >> 6) & 0x3F), 0x80 | (cp & 0x3F));
        } else out.push(0xE0 | (c >> 12), 0x80 | ((c >> 6) & 0x3F), 0x80 | (c & 0x3F));
      }
      return new Uint8Array(out);
    };
  }
  if (typeof g.TextDecoder === 'undefined') {
    g.TextDecoder = function () {};
    g.TextDecoder.prototype.decode = function (bytes) {
      if (!bytes) return '';
      var b = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
      var out = '';
      var i = 0;
      while (i < b.length) {
        var c = b[i++];
        if (c < 0x80) out += String.fromCharCode(c);
        else if (c < 0xE0) out += String.fromCharCode(((c & 0x1F) << 6) | (b[i++] & 0x3F));
        else if (c < 0xF0) out += String.fromCharCode(((c & 0x0F) << 12) | ((b[i++] & 0x3F) << 6) | (b[i++] & 0x3F));
        else {
          var cp = ((c & 0x07) << 18) | ((b[i++] & 0x3F) << 12) | ((b[i++] & 0x3F) << 6) | (b[i++] & 0x3F);
          cp -= 0x10000;
          out += String.fromCharCode(0xD800 + (cp >> 10), 0xDC00 + (cp & 0x3FF));
        }
      }
      return out;
    };
  }

  // --- WebCrypto-lite -------------------------------------------------
  // crypto.getRandomValues backed by the host's SecureRandom. The
  // crypto-js browser build hard-requires a native random source
  // (WordArray.random throws without one), and plug hosts are
  // expected to offer this global. getRandomValues only — no
  // `subtle` (nothing in the ecosystem's plugs uses it here).
  if (typeof g.crypto === 'undefined' && typeof __plugRandomBytes === 'function') {
    g.crypto = {
      getRandomValues: function (arr) {
        var bytes = __plugRandomBytes(arr.byteLength);
        var view = new Uint8Array(arr.buffer, arr.byteOffset || 0, arr.byteLength);
        for (var i = 0; i < bytes.length; i++) view[i] = bytes[i];
        return arr;
      }
    };
  }

  // --- base64 ---------------------------------------------------------
  if (typeof g.btoa === 'undefined' && typeof __plugB64e === 'function') {
    g.btoa = function (s) { return __plugB64e(String(s)); };
  }
  if (typeof g.atob === 'undefined' && typeof __plugB64d === 'function') {
    g.atob = function (s) { return __plugB64d(String(s)); };
  }

  // --- axios (host shim over fetch) -----------------------------------
  // The slice of the axios API scraper plugs actually use:
  // axios(config), axios.request(config), axios.get/head/delete(
  // url[, config]), axios.post/put/patch(url[, data[, config]]),
  // axios.create(defaults). Resolves { data, status, statusText,
  // headers, config }; `data` is JSON-parsed when the body parses,
  // else the raw text (axios's default response transform); rejects
  // on a non-2xx status with an error carrying .response (axios's
  // default validateStatus contract).
  function axiosRequest(defaults, config) {
    config = config || {};
    var merged = {};
    var k;
    if (defaults) for (k in defaults) merged[k] = defaults[k];
    for (k in config) merged[k] = config[k];
    config = merged;
    var url = String(config.url || '');
    if (config.baseURL && !/^[a-z]+:\/\//i.test(url)) {
      url = String(config.baseURL).replace(/\/+$/, '') + '/' + url.replace(/^\/+/, '');
    }
    if (config.params) {
      var qs = new g.URLSearchParams();
      for (var pk in config.params) {
        if (config.params[pk] != null) qs.append(pk, String(config.params[pk]));
      }
      var q = qs.toString();
      if (q) url += (url.indexOf('?') >= 0 ? '&' : '?') + q;
    }
    var headers = {};
    if (config.headers) for (var hk in config.headers) headers[hk] = String(config.headers[hk]);
    var body = null;
    if (config.data != null) {
      if (typeof config.data === 'string') body = config.data;
      else if (typeof g.URLSearchParams !== 'undefined' && config.data instanceof g.URLSearchParams) {
        body = config.data.toString();
      } else {
        body = JSON.stringify(config.data);
        var hasCt = false;
        for (var ck in headers) if (ck.toLowerCase() === 'content-type') hasCt = true;
        if (!hasCt) headers['Content-Type'] = 'application/json';
      }
    }
    return g.fetch(url, {
      method: String(config.method || 'get').toUpperCase(),
      headers: headers,
      body: body
    }).then(function (resp) {
      return resp.text().then(function (text) {
        var data = text;
        if (config.responseType !== 'text') {
          try { data = JSON.parse(text); } catch (e) { /* keep raw text */ }
        }
        var plainHeaders = {};
        var raw = resp._headers || {};
        for (var rk in raw) {
          var rv = raw[rk];
          // axios convention: set-cookie stays an array of the
          // separate values; any other repeated header joins.
          if (Array.isArray(rv) && rk.toLowerCase() !== 'set-cookie') rv = rv.join(', ');
          plainHeaders[rk.toLowerCase()] = rv;
        }
        var out = {
          data: data, status: resp.status, statusText: resp.statusText || '',
          headers: plainHeaders, config: config
        };
        var validate = config.validateStatus || function (s) { return s >= 200 && s < 300; };
        if (!validate(resp.status)) {
          var err = new Error('Request failed with status code ' + resp.status);
          err.response = out;
          err.config = config;
          throw err;
        }
        return out;
      });
    });
  }
  function makeAxios(defaults) {
    var fn = function (config) { return axiosRequest(defaults, config); };
    fn.request = function (config) { return axiosRequest(defaults, config); };
    fn.get = function (url, config) { return axiosRequest(defaults, withUrl(config, 'get', url)); };
    fn.head = function (url, config) { return axiosRequest(defaults, withUrl(config, 'head', url)); };
    fn.delete = function (url, config) { return axiosRequest(defaults, withUrl(config, 'delete', url)); };
    fn.post = function (url, data, config) { return axiosRequest(defaults, withData(config, 'post', url, data)); };
    fn.put = function (url, data, config) { return axiosRequest(defaults, withData(config, 'put', url, data)); };
    fn.patch = function (url, data, config) { return axiosRequest(defaults, withData(config, 'patch', url, data)); };
    fn.create = function (more) {
      var d = {};
      var k;
      if (defaults) for (k in defaults) d[k] = defaults[k];
      if (more) for (k in more) d[k] = more[k];
      return makeAxios(d);
    };
    return fn;
  }
  function withUrl(config, method, url) {
    var c = {};
    if (config) for (var k in config) c[k] = config[k];
    c.method = method;
    c.url = url;
    return c;
  }
  function withData(config, method, url, data) {
    var c = withUrl(config, method, url);
    if (data !== undefined) c.data = data;
    return c;
  }
  var axiosModule = makeAxios(null);

  // --- host modules: require() ----------------------------------------
  // cheerio / crypto-js ship as host assets. Their sources come
  // from the host on first use (__plugModuleSource) and are
  // evaluated ONCE per run — each is a single expression IIFE
  // returning the module, the shape this host has always consumed.
  // axios is the shim above. Anything else throws the
  // self-contained error plugs have always seen here.
  var hostModules = {};
  function loadHostModule(name) {
    if (name in hostModules) return hostModules[name];
    // v0.14.65: the host may have pre-evaluated this library
    // from precompiled QuickJS bytecode into the __plugModules
    // registry (compiled once per process — no per-run parse of
    // the 1.3 MB cheerio source). Prefer it when present; the
    // source path below is the unchanged fallback.
    var pre = (typeof g.__plugModules !== 'undefined') ? g.__plugModules[name] : undefined;
    if (pre !== undefined) { hostModules[name] = pre; return pre; }
    var src = (typeof __plugModuleSource === 'function') ? __plugModuleSource(name) : null;
    if (src == null) return undefined;
    hostModules[name] = (0, eval)(src);
    return hostModules[name];
  }
  if (typeof g.require === 'undefined') {
    g.require = function (name) {
      if (name === 'cheerio' || name === 'cheerio-without-node-native') {
        var ch = loadHostModule('cheerio');
        if (ch !== undefined) return ch;
      }
      if (name === 'crypto-js') {
        var cj = loadHostModule('crypto-js');
        if (cj !== undefined) return cj;
      }
      if (name === 'axios') return axiosModule;
      throw new Error("require('" + name + "') is not available — plugs must be self-contained files");
    };
  }

  // --- small standard-library polyfills (guarded) ---------------------
  if (!String.prototype.replaceAll) {
    String.prototype.replaceAll = function (a, b) {
      if (a instanceof RegExp) return this.replace(a, b);
      return this.split(String(a)).join(String(b));
    };
  }
  if (!Object.values) {
    Object.values = function (o) { return Object.keys(o).map(function (k) { return o[k]; }); };
  }
  if (!Object.entries) {
    Object.entries = function (o) { return Object.keys(o).map(function (k) { return [k, o[k]]; }); };
  }
  if (!Object.fromEntries) {
    Object.fromEntries = function (entries) {
      var o = {};
      for (var i = 0; i < entries.length; i++) o[entries[i][0]] = entries[i][1];
      return o;
    };
  }
  if (!Array.prototype.flat) {
    Array.prototype.flat = function () {
      var out = [];
      this.forEach(function (v) {
        if (Array.isArray(v)) v.forEach(function (x) { out.push(x); });
        else out.push(v);
      });
      return out;
    };
  }
  if (!Array.prototype.flatMap) {
    Array.prototype.flatMap = function (fn, thisArg) {
      return this.map(fn, thisArg).flat();
    };
  }
  if (typeof Promise !== 'undefined' && !Promise.allSettled) {
    Promise.allSettled = function (items) {
      return Promise.all(Array.prototype.map.call(items, function (p) {
        return Promise.resolve(p).then(
          function (v) { return { status: 'fulfilled', value: v }; },
          function (e) { return { status: 'rejected', reason: e }; }
        );
      }));
    };
  }
})();
