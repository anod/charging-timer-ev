/* VERSION and ASSETS are injected from the complete production distribution. */
const BASE = new URL("./", self.location.href);
const PREFIX = `ev-timer-shell-v2:${BASE.pathname}:`;
const LEGACY_PREFIX = `ev-timer-shell:${BASE.pathname}:`;
const CACHE = PREFIX + VERSION;
const shellCache = name => name.startsWith(PREFIX) || name.startsWith(LEGACY_PREFIX);
const urls = new Map(Object.entries(ASSETS).map(([name, hash]) => [new URL(name, BASE).href, hash]));
const META = `ev-timer-versions:${BASE.pathname}`;
const manifestUrl = new URL("__ev_manifest__", BASE).href;
const bindingUrl = id => new URL(`__ev_client__/${encodeURIComponent(id)}`, BASE).href;
const activeUrl = new URL("__ev_active__", BASE).href;
let activation;

async function readJson(cache, key) {
    const response = await cache.match(key);
    return response ? response.json() : null;
}

async function saveJson(cache, key, value) {
    await cache.put(key, new Response(JSON.stringify(value)));
}

async function appClients() {
    // A waiting worker controls no clients yet; include the active worker's
    // documents, then scope the result so another app cannot pin our caches.
    return (await self.clients.matchAll({ includeUncontrolled: true }))
        .filter(client => client.url.startsWith(BASE.href));
}

async function pruneBindings(meta, preserve = []) {
    const live = new Set((await appClients()).map(client => bindingUrl(client.id)));
    for (const id of preserve) if (id) live.add(bindingUrl(id));
    for (const request of await meta.keys()) {
        if (request.url !== activeUrl && !live.has(request.url)) await meta.delete(request);
    }
}

async function digest(response) {
    const bytes = await response.clone().arrayBuffer();
    return Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", bytes)),
        byte => byte.toString(16).padStart(2, "0")).join("");
}

async function verified(response, expected) {
    if (!response || !response.ok || response.type === "opaque" || response.redirected) return false;
    return await digest(response) === expected;
}

// Persist before skipWaiting, not in activate: activation can be interrupted.
// Client IDs survive worker termination but change on document navigation.
async function pinClients() {
    const meta = await caches.open(META);
    await pruneBindings(meta);
    const names = (await caches.keys()).filter(name => shellCache(name) && name !== CACHE);
    const recorded = await readJson(meta, activeUrl);
    // The original activate handler deletes every predecessor, so its active
    // cache precedes any subsequently installed/waiting legacy caches.
    const previous = names.includes(recorded) ? recorded : names[0];
    for (const name of names) {
        const cache = await caches.open(name);
        if (!await readJson(cache, manifestUrl)) {
            // Reconstruct the original inventory, then authenticate the entire
            // inventory against its byte-derived cache version. Corruption or
            // missing files cannot be blessed with newly computed checksums.
            const inventory = {};
            const requests = (await cache.keys()).sort((a, b) => {
                const left = decodeURIComponent(a.url.slice(BASE.href.length));
                const right = decodeURIComponent(b.url.slice(BASE.href.length));
                return left < right ? -1 : left > right ? 1 : 0;
            });
            const assets = {};
            for (const request of requests) {
                inventory[request.url] = await digest(await cache.match(request));
                assets[request.url.slice(BASE.href.length)] = inventory[request.url];
            }
            const version = await digest(new Response(JSON.stringify(assets) + LEGACY_TEMPLATE + ERROR_TEXT));
            if (name !== LEGACY_PREFIX + version) throw new Error("Legacy shell inventory mismatch");
            await saveJson(cache, manifestUrl, { inventory, legacy: true });
        }
    }
    for (const client of await appClients()) {
        if (previous && !await readJson(meta, bindingUrl(client.id))) {
            await saveJson(meta, bindingUrl(client.id), previous);
        }
    }
}

async function notifyFailure() {
    for (const client of await self.clients.matchAll({ includeUncontrolled: true })) {
        client.postMessage({ type: "ev-timer-cache-error" });
    }
}

self.addEventListener("install", event => {
    event.waitUntil((async () => {
        try {
            // Snapshot before creating this cache. Cleanup must never delete a
            // later worker's installing/waiting shell, even with no open clients.
            const predecessors = (await caches.keys()).filter(name => shellCache(name) && name !== CACHE);
            const cache = await caches.open(CACHE);
            // Bounded sequential downloads avoid holding the whole Wasm build in memory.
            for (const [url, hash] of urls) {
                const response = await fetch(url, { cache: "no-store" });
                if (!await verified(response, hash)) throw new Error(`Invalid shell asset: ${url}`);
                await cache.put(url, response);
            }
            await saveJson(cache, manifestUrl, { inventory: Object.fromEntries(urls), legacy: false, predecessors });
            // The original browser adapter cannot send our activation protocol.
            // Safely retire that worker as soon as the first upgraded shell is
            // complete; existing legacy documents remain pinned and never reload.
            const names = (await caches.keys()).filter(name => shellCache(name) && name !== CACHE);
            let legacy = false;
            for (const name of names) {
                if (!await readJson(await caches.open(name), manifestUrl)) legacy = true;
            }
            if (legacy) {
                await pinClients();
                await saveJson(await caches.open(META), activeUrl, CACHE);
                await self.skipWaiting();
            }
        } catch (error) {
            await caches.delete(CACHE);
            await notifyFailure();
            throw error;
        }
        // Installation alone never interrupts an existing timer window.
    })());
});

self.addEventListener("activate", event => {
    event.waitUntil((async () => {
        const meta = await caches.open(META);
        await saveJson(meta, activeUrl, CACHE);
        // A live document can still request lazy-loaded assets from an old build.
        // Retain shells while any controlled document exists, including races
        // with navigation. A later client-free activation reclaims old shells.
        if ((await self.clients.matchAll()).length === 0) {
            const manifest = await readJson(await caches.open(CACHE), manifestUrl);
            if (!Array.isArray(manifest?.predecessors)) {
                await notifyFailure();
                throw new Error("Shell cleanup inventory unavailable");
            }
            for (const name of manifest.predecessors) {
                if (shellCache(name) && name !== CACHE) await caches.delete(name);
            }
            for (const request of await meta.keys()) {
                if (request.url !== activeUrl) await meta.delete(request);
            }
        }
        // No clients.claim and no broadcast reload.
    })());
});

self.addEventListener("message", event => {
    if (event.data?.type !== "ev-timer-activate" || !event.source ||
        !event.source.url.startsWith(BASE.href)) return;
    event.waitUntil((async () => {
        try {
            activation ||= (async () => {
                const meta = await caches.open(META);
                if (await readJson(meta, activeUrl) !== CACHE) {
                    await pinClients();
                    await saveJson(meta, activeUrl, CACHE);
                }
            })();
            await activation;
            event.ports[0]?.postMessage({ version: VERSION });
            await self.skipWaiting();
        } catch (error) {
            activation = null;
            await notifyFailure();
            throw error;
        }
    })());
});

self.addEventListener("fetch", event => {
    if (event.request.method !== "GET") return;
    const url = new URL(event.request.url);
    if (url.origin !== BASE.origin) return;
    // Fragment routes identify application screens, not different shell assets.
    url.hash = "";
    if (event.request.mode === "navigate" &&
        (url.pathname === BASE.pathname || url.pathname === BASE.pathname + "index.html")) {
        url.pathname = BASE.pathname + "index.html";
        url.search = "";
    }
    if (event.request.mode === "navigate" && url.pathname !== BASE.pathname + "index.html") return;
    // Everything outside the generated asset inventory (notably the existing
    // privacy page) stays network-only, with no navigation fallback.
    // Old builds can contain files absent from the current inventory.
    if (!url.href.startsWith(BASE.href) ||
        /(^|\/)privacy(?:[./]|$)/i.test(url.pathname.slice(BASE.pathname.length))) return;
    event.respondWith((async () => {
        try {
            const meta = await caches.open(META);
            const name = event.request.mode === "navigate" ? CACHE :
                await readJson(meta, bindingUrl(event.clientId)) || CACHE;
            const cache = await caches.open(name);
            const manifest = name === CACHE ? { inventory: Object.fromEntries(urls), legacy: false } :
                await readJson(cache, manifestUrl);
            const expected = manifest?.inventory[url.href];
            if (!expected) {
                if (name !== CACHE && urls.has(url.href)) throw new Error("Asset absent from pinned shell");
                return fetch(event.request);
            }
            if (event.request.mode === "navigate" && event.resultingClientId) {
                await saveJson(meta, bindingUrl(event.resultingClientId), CACHE);
                await pruneBindings(meta, [event.clientId, event.resultingClientId]);
            }
            const cached = await cache.match(url.href);
            if (await verified(cached, expected)) return cached;
            if (manifest.legacy) throw new Error("Legacy shell asset unavailable");
            const response = await fetch(url.href, { cache: "no-store" });
            if (!await verified(response, expected)) throw new Error("Shell version mismatch");
            await cache.put(url.href, response.clone());
            return response;
        } catch (error) {
            await notifyFailure();
            return new Response(ERROR_TEXT,
                { status: 503, headers: { "Content-Type": "text/plain; charset=utf-8" } });
        }
    })());
});
