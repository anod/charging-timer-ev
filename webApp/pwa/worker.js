/* VERSION and ASSETS are injected from the complete production distribution. */
const BASE = new URL("./", self.location.href);
const PREFIX = `ev-timer-shell:${BASE.pathname}:`;
const CACHE = PREFIX + VERSION;
const urls = new Map(Object.entries(ASSETS).map(([name, hash]) => [new URL(name, BASE).href, hash]));

async function verified(response, expected) {
    if (!response || !response.ok || response.type === "opaque" || response.redirected) return false;
    const bytes = await response.clone().arrayBuffer();
    const hash = Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", bytes)),
        byte => byte.toString(16).padStart(2, "0")).join("");
    return hash === expected;
}

async function notifyFailure() {
    for (const client of await self.clients.matchAll({ includeUncontrolled: true })) {
        client.postMessage({ type: "ev-timer-cache-error" });
    }
}

self.addEventListener("install", event => {
    event.waitUntil((async () => {
        try {
            const cache = await caches.open(CACHE);
            // Bounded sequential downloads avoid holding the whole Wasm build in memory.
            for (const [url, hash] of urls) {
                const response = await fetch(url, { cache: "no-store" });
                if (!await verified(response, hash)) throw new Error(`Invalid shell asset: ${url}`);
                await cache.put(url, response);
            }
        } catch (error) {
            await caches.delete(CACHE);
            await notifyFailure();
            throw error;
        }
        // Never skipWaiting: an existing page keeps its matching shell until all
        // tabs/installed windows close. No reload, database access or data migration.
    })());
});

self.addEventListener("activate", event => {
    event.waitUntil((async () => {
        for (const name of await caches.keys()) {
            if (name.startsWith(PREFIX) && name !== CACHE) await caches.delete(name);
        }
        // No clients.claim: the first online page finishes with its original build.
    })());
});

self.addEventListener("fetch", event => {
    if (event.request.method !== "GET") return;
    const url = new URL(event.request.url);
    if (url.origin !== BASE.origin) return;
    if (event.request.mode === "navigate" &&
        (url.pathname === BASE.pathname || url.pathname === BASE.pathname + "index.html")) {
        url.pathname = BASE.pathname + "index.html";
        url.search = "";
    }
    // Everything outside the generated asset inventory (notably the existing
    // privacy page) stays network-only, with no navigation fallback.
    const expected = urls.get(url.href);
    if (!expected) return;
    event.respondWith((async () => {
        const cache = await caches.open(CACHE);
        const cached = await cache.match(url.href);
        if (await verified(cached, expected)) return cached;
        try {
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
