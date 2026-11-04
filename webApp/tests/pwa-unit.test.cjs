const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const vm = require("node:vm");
const { webcrypto } = require("node:crypto");
const { prepare } = require("../pwa/prepare.cjs");

function fixture() {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), "ev-shell-unit-"));
    fs.writeFileSync(path.join(root, "index.html"), "<title>Timer</title>");
    fs.writeFileSync(path.join(root, "app.wasm"), "wasm-fixture");
    fs.writeFileSync(path.join(root, "privacy.html"), "must remain network-only");
    return root;
}

function worker(root) {
    prepare(root);
    const listeners = {};
    const stores = new Map();
    const notices = [];
    const base = "https://example.test/charging-timer-ev/";
    let fail = false;
    const network = async url => fail ? new Response("unavailable", { status: 503 }) :
        new Response(fs.readFileSync(path.join(root, new URL(url).pathname.slice("/charging-timer-ev/".length))));
    const caches = {
        keys: async () => [...stores.keys()],
        delete: async name => stores.delete(name),
        open: async name => {
            if (!stores.has(name)) stores.set(name, new Map());
            const store = stores.get(name);
            return {
                match: async url => store.get(url)?.clone(),
                put: async (url, response) => store.set(url, response.clone())
            };
        }
    };
    const context = vm.createContext({
        URL, Response, Uint8Array, crypto: webcrypto, caches, fetch: network,
        self: {
            location: { href: base + "sw.js" },
            clients: { matchAll: async () => [{ postMessage: message => notices.push(message) }] },
            addEventListener: (name, callback) => { listeners[name] = callback; }
        }
    });
    vm.runInContext(fs.readFileSync(path.join(root, "sw.js"), "utf8"), context);
    return {
        stores, caches, notices, base, setFail: value => { fail = value; },
        async lifecycle(name) {
            let result;
            listeners[name]({ waitUntil: promise => { result = promise; } });
            await result;
        },
        async request(relative, mode = "cors", method = "GET") {
            let result;
            listeners.fetch({
                request: { url: new URL(relative, base).href, mode, method },
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
        sw.stores.set("ev-timer-shell:/charging-timer-ev/:old", new Map());
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
        for (const relative of ["privacy", "privacy.html", "privacy/", "docs/privacy.html",
            "unknown", "../another-app/", "https://other.test/index.html"]) {
            assert.equal(await sw.request(relative, "navigate"), undefined);
        }
        assert.equal(await sw.request("index.html", "cors", "POST"), undefined);
        const cache = [...sw.stores.values()][0];
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
