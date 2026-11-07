const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const vm = require("node:vm");
const { webcrypto, createHash } = require("node:crypto");
const { prepare } = require("../pwa/prepare.cjs");

function fixture() {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), "ev-shell-unit-"));
    fs.writeFileSync(path.join(root, "index.html"), "<title>Timer</title>");
    fs.writeFileSync(path.join(root, "app.wasm"), "wasm-fixture");
    fs.writeFileSync(path.join(root, "privacy.html"), "must remain network-only");
    return root;
}

function worker(root, sharedStores = new Map(), clients = [], legacy = false) {
    prepare(root);
    const listeners = {};
    const stores = sharedStores;
    const notices = [];
    const base = "https://example.test/charging-timer-ev/";
    let fail = false;
    let skipped = false;
    const key = url => typeof url === "string" ? url : url.url;
    const network = async url => fail ? new Response("unavailable", { status: 503 }) :
        new Response(fs.readFileSync(path.join(root, new URL(url).pathname.slice("/charging-timer-ev/".length))));
    const caches = {
        keys: async () => [...stores.keys()],
        delete: async name => stores.delete(name),
        open: async name => {
            if (!stores.has(name)) stores.set(name, new Map());
            const store = stores.get(name);
            return {
                match: async url => store.get(key(url))?.clone(),
                put: async (url, response) => store.set(key(url), response.clone()),
                keys: async () => [...store.keys()].map(url => ({ url })),
                delete: async url => store.delete(key(url))
            };
        }
    };
    const context = vm.createContext({
        URL, Response, Uint8Array, crypto: webcrypto, caches, fetch: network,
        self: {
            location: { href: base + "sw.js" },
            clients: { matchAll: async options => options?.includeUncontrolled ?
                [...clients.map(client => ({ postMessage() {}, ...client })),
                    { url: "https://example.test/observer", postMessage: message => notices.push(message) }] : clients },
            skipWaiting: async () => { skipped = true; },
            addEventListener: (name, callback) => { listeners[name] = callback; }
        }
    });
    let source = fs.readFileSync(path.join(root, "sw.js"), "utf8");
    if (legacy) {
        const lines = source.split("\n");
        const template = fs.readFileSync(path.join(__dirname, "../pwa/legacy-worker.js"), "utf8")
            .replace(/\r\n/g, "\n");
        const assets = lines[1].slice("const ASSETS = ".length, -1);
        const error = JSON.parse(lines[2].slice("const ERROR_TEXT = ".length, -1));
        const version = createHash("sha256").update(assets + template + error).digest("hex");
        source = `const VERSION = "${version}";\n` + lines.slice(1, 3).join("\n") + "\n" + template;
    }
    vm.runInContext(source, context);
    return {
        stores, caches, notices, base, skipped: () => skipped, setFail: value => { fail = value; },
        async lifecycle(name) {
            let result;
            listeners[name]({ waitUntil: promise => { result = promise; } });
            await result;
        },
        async activateFromPage() {
            let result;
            listeners.message({
                data: { type: "ev-timer-activate" }, source: { url: base },
                ports: [{ postMessage() {} }], waitUntil: promise => { result = promise; }
            });
            await result;
        },
        async request(relative, mode = "cors", method = "GET", clientId = "", resultingClientId = "") {
            let result;
            listeners.fetch({
                request: { url: new URL(relative, base).href, mode, method },
                clientId, resultingClientId,
                respondWith: promise => { result = promise; }
            });
            return result;
        }
    };
}

test("shell build is deterministic, byte-versioned and excludes privacy", () => {
    const root = fixture();
    try {
        const first = prepare(root);
        assert.equal(prepare(root), first);
        fs.writeFileSync(path.join(root, "privacy.html"), "new privacy policy");
        assert.equal(prepare(root), first);
        fs.writeFileSync(path.join(root, "app.wasm"), "new bytes, same filename");
        assert.notEqual(prepare(root), first);
        fs.unlinkSync(path.join(root, "app.wasm"));
        assert.throws(() => prepare(root), /complete production Wasm/);
    } finally { fs.rmSync(root, { recursive: true, force: true }); }
});

test("failed precache is removed; activation only cleans this path's old shell caches", async () => {
    const root = fixture();
    try {
        const sw = worker(root);
        sw.stores.set("unrelated", new Map());
        sw.stores.set("ev-timer-shell:/another-app/:old", new Map());
        sw.stores.set("ev-timer-shell:/charging-timer-ev/:old", new Map([
            [sw.base + "__ev_manifest__", new Response(JSON.stringify({ inventory: {}, legacy: false }))]
        ]));
        sw.setFail(true);
        await assert.rejects(sw.lifecycle("install"), /Invalid shell asset/);
        assert.equal(sw.notices.length, 1);
        assert.equal([...sw.stores.keys()].length, 3, "Partial new cache must be removed");
        sw.setFail(false);
        await sw.lifecycle("install");
        await sw.lifecycle("activate");
        assert.equal(sw.stores.has("unrelated"), true);
        assert.equal(sw.stores.has("ev-timer-shell:/another-app/:old"), true);
        assert.equal(sw.stores.has("ev-timer-shell:/charging-timer-ev/:old"), false);
    } finally { fs.rmSync(root, { recursive: true, force: true }); }
});

test("offline reload, verified repair, explicit failure and privacy/navigation isolation", async () => {
    const root = fixture();
    try {
        const sw = worker(root);
        await sw.lifecycle("install");
        sw.setFail(true);
        assert.equal(await (await sw.request("./", "navigate")).text(), "<title>Timer</title>");
        for (const route of ["./#/running", "./#/settings", "./#/summary/42", "index.html#/presets"]) {
            assert.equal(await (await sw.request(route, "navigate")).text(), "<title>Timer</title>");
        }
        for (const relative of ["privacy", "privacy.html", "privacy/", "docs/privacy.html",
            "unknown", "../another-app/", "https://other.test/index.html"]) {
            assert.equal(await sw.request(relative, "navigate"), undefined);
        }
        assert.equal(await sw.request("index.html", "cors", "POST"), undefined);
        const cache = [...sw.stores.entries()].find(([name]) => name.startsWith("ev-timer-shell"))[1];
        cache.set(sw.base + "app.wasm", new Response("bad bytes"));
        assert.equal((await sw.request("app.wasm")).status, 503);
        assert.equal(sw.notices.length, 1);
        sw.setFail(false);
        assert.equal(await (await sw.request("app.wasm")).text(), "wasm-fixture");
        fs.writeFileSync(path.join(root, "app.wasm"), "different deployment");
        cache.delete(sw.base + "app.wasm");
        assert.equal((await sw.request("app.wasm")).status, 503, "Never mix builds during repair");
    } finally { fs.rmSync(root, { recursive: true, force: true }); }
});

test("activation pins existing documents across worker restarts, refresh selects new verified shell", async () => {
    const root = fixture();
    try {
        const stores = new Map();
        const clients = [{ id: "timer-A", url: "https://example.test/charging-timer-ev/" }];
        const a = worker(root, stores, clients);
        await a.lifecycle("install");
        await a.lifecycle("activate");
        fs.writeFileSync(path.join(root, "app.wasm"), "build B");
        const b = worker(root, stores, clients);
        await b.lifecycle("install");
        assert.equal(b.skipped(), false, "Background installation never reloads or switches windows");
        await b.activateFromPage();
        assert.equal(b.skipped(), true);
        await b.lifecycle("activate");
        b.setFail(true);
        assert.equal(await (await b.request("app.wasm", "cors", "GET", "timer-A")).text(), "wasm-fixture");
        await b.request("./", "navigate", "GET", "timer-A", "refreshed-B");
        assert.equal(await (await b.request("app.wasm", "cors", "GET", "refreshed-B")).text(), "build B");
        const restarted = worker(root, stores, clients);
        restarted.setFail(true);
        assert.equal(await (await restarted.request("app.wasm", "cors", "GET", "timer-A")).text(), "wasm-fixture");
        const old = [...stores.entries()].find(([name, cache]) =>
            name.startsWith("ev-timer-shell") && cache.get(a.base + "app.wasm"));
        old[1].set(a.base + "app.wasm", new Response("corrupt"));
        assert.equal((await restarted.request("app.wasm", "cors", "GET", "timer-A")).status, 503,
            "An old missing/corrupt asset cannot be replaced by another deployment");
        for (let index = 0; index < 30; index++) {
            const id = `refresh-${index}`;
            await restarted.request("./", "navigate", "GET", clients[0].id, id);
            clients[0].id = id;
        }
        const metadata = [...stores.entries()].find(([name]) => name.startsWith("ev-timer-versions:"))[1];
        assert.ok(metadata.size <= 3, "A live installed window must not accumulate closed-document bindings");
        clients.length = 0;
        await restarted.lifecycle("activate");
        assert.equal([...stores.keys()].filter(name => name.startsWith("ev-timer-shell")).length, 1);
    } finally { fs.rmSync(root, { recursive: true, force: true }); }
});

test("legacy inventory is authenticated against its original version, never blessing corrupted cached bytes", async () => {
    for (const corrupt of [false, true]) {
        const root = fixture();
        try {
            const stores = new Map();
            const clients = [{ id: "legacy-timer", url: "https://example.test/charging-timer-ev/" }];
            const a = worker(root, stores, clients, true);
            await a.lifecycle("install");
            await a.lifecycle("activate");
            if (corrupt) [...stores.values()][0].set(a.base + "app.wasm", new Response("corrupt"));
            fs.writeFileSync(path.join(root, "app.wasm"), "new deployment");
            const b = worker(root, stores, clients);
            if (corrupt) {
                await assert.rejects(b.lifecycle("install"), /Legacy shell inventory mismatch/);
                assert.equal(b.skipped(), false);
                assert.equal([...stores.keys()].filter(name => name.startsWith("ev-timer-shell")).length, 1);
            } else {
                await b.lifecycle("install");
                assert.equal(b.skipped(), true, "Legacy adapter needs no activation message");
                await a.lifecycle("activate");
                assert.equal([...stores.keys()].filter(name => name.startsWith("ev-timer-shell-v2:")).length, 1,
                    "Original activation must not delete the newly downloaded shell");
                await b.lifecycle("activate");
                b.setFail(true);
                assert.equal(await (await b.request("app.wasm", "cors", "GET", "legacy-timer")).text(), "wasm-fixture");
                await b.request("./", "navigate", "GET", "legacy-timer", "upgraded");
                assert.equal(await (await b.request("app.wasm", "cors", "GET", "upgraded")).text(), "new deployment");
            }
        } finally { fs.rmSync(root, { recursive: true, force: true }); }
    }
});

test("client-free activation never deletes a later worker's installing/waiting shell", async () => {
    const root = fixture();
    try {
        const stores = new Map();
        const a = worker(root, stores);
        await a.lifecycle("install");
        await a.lifecycle("activate");
        fs.writeFileSync(path.join(root, "app.wasm"), "build B");
        const b = worker(root, stores);
        await b.lifecycle("install");
        fs.writeFileSync(path.join(root, "app.wasm"), "build C");
        const c = worker(root, stores);
        await c.lifecycle("install");
        await b.lifecycle("activate");
        c.setFail(true);
        assert.equal(await (await c.request("app.wasm")).text(), "build C");
        await c.lifecycle("activate");
        assert.equal([...stores.keys()].filter(name => name.startsWith("ev-timer-shell")).length, 1);
    } finally { fs.rmSync(root, { recursive: true, force: true }); }
});

async function refreshAdapter({ refresh = true, guarded = false, discover = false,
    late = false, offline = false, storageFails = false, matchingEntry = false, departingEntry = false,
    retryAfterFailure = false } = {}) {
    const container = new EventTarget();
    const registration = new EventTarget();
    const candidate = new EventTarget();
    candidate.state = "installed";
    let reloads = 0;
    let activations = 0;
    let now = 0;
    let guard = guarded ? "1" : null;
    candidate.postMessage = () => {
        activations++;
        candidate.state = "activated";
        candidate.dispatchEvent(new Event("statechange"));
    };
    registration.active = {};
    registration.waiting = discover ? null : candidate;
    registration.update = async () => {
        if (retryAfterFailure) {
            container.dispatchEvent(new MessageEvent("message", { data: { type: "ev-timer-cache-error" } }));
            return;
        }
        if (discover) {
            if (late) now = 15_001;
            registration.installing = candidate;
            registration.dispatchEvent(new Event("updatefound"));
            candidate.dispatchEvent(new Event("statechange"));
        }
    };
    container.register = async () => registration;
    let load;
    const context = vm.createContext({
        URL, Event, EventTarget, MessageChannel, setTimeout, clearTimeout,
        console: { error() {} }, isSecureContext: true,
        document: { currentScript: { src: "https://example.test/app/browser-pwa.js" } },
        navigator: { serviceWorker: container, onLine: !offline },
        performance: { now: () => now, getEntriesByType: () => [{ type: refresh ? "reload" : "navigate" }] },
        history: { state: matchingEntry || departingEntry ?
            { "ev-timer-entry:/app/": matchingEntry ? "departing" : "older-entry" } : null },
        sessionStorage: {
            getItem: key => {
                if (storageFails) throw new Error("denied");
                return key.startsWith("ev-timer-entry:") ?
                    matchingEntry || departingEntry ? "departing" : null : guard;
            },
            removeItem: key => { if (key.startsWith("ev-timer-refresh:")) guard = null; },
            setItem: (key, value) => { guard = value; }
        },
        location: { reload: () => { reloads++; } },
        window: { dispatchEvent() {}, addEventListener: (type, handler) => { if (type === "load") load = handler; } }
    });
    vm.runInContext(fs.readFileSync(path.join(__dirname,
        "../src/wasmJsMain/resources/browser-pwa.js"), "utf8"), context);
    await load();
    if (retryAfterFailure) {
        registration.installing = candidate;
        registration.dispatchEvent(new Event("updatefound"));
        candidate.dispatchEvent(new Event("statechange"));
    }
    await new Promise(resolve => setImmediate(resolve));
    return { reloads, activations, guard, status: context.evTimerPwa.status() };
}

test("refresh adapter applies waiting and refresh-discovered workers once, including offline waiting update", async () => {
    for (const options of [{}, { discover: true }, { offline: true }, { refresh: false, matchingEntry: true }]) {
        const result = await refreshAdapter(options);
        assert.equal(result.reloads, 1);
        assert.equal(result.activations, 1);
        assert.equal(result.guard, "1");
    }
    for (const options of [{ guarded: true }, { refresh: false }, { refresh: false, departingEntry: true },
        { discover: true, late: true },
        { discover: true, retryAfterFailure: true },
        { storageFails: true }]) {
        const result = await refreshAdapter(options);
        assert.equal(result.reloads, 0);
        assert.equal(result.activations, 0);
        assert.equal(result.status, "update");
    }
});
