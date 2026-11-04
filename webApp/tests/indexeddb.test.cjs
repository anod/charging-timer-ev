const { test } = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const { webcrypto } = require("node:crypto");
const { IDBFactory, IDBObjectStore } = require("fake-indexeddb");
const source = fs.readFileSync("src/wasmJsMain/resources/browser-storage.js", "utf8");

function bridge(indexedDB = new IDBFactory(), extra = {}) {
    const root = { indexedDB, crypto: webcrypto, TextDecoder, Blob, ...extra };
    vm.runInNewContext(source, root);
    return root.evTimerStorage;
}

test("commits are readable after reopening; payload stays opaque including 64-bit IDs", async () => {
    const db = new IDBFactory();
    const first = bridge(db);
    assert.equal(await first.read(), "null");
    const payload = '{"id":9007199254740993,"name":"Car 🚗"}';
    const saved = JSON.parse(await first.write(null, payload));
    assert.equal(saved.payload, payload);
    assert.deepEqual(JSON.parse(await bridge(db).read()), saved);
    const replacement = JSON.parse(await bridge(db).write(saved.revision, "replacement"));
    assert.notEqual(saved.revision, replacement.revision);
    assert.equal(JSON.parse(await first.read()).payload, "replacement");
});

function picker(files, event = "change") {
    let removed = false;
    const input = {
        files,
        remove() { removed = true; },
        click() { queueMicrotask(() => event === "cancel" ? input.oncancel() : input.onchange()); }
    };
    const storage = bridge(null, { document: { createElement: () => input, body: { append() {} } } });
    return { storage, removed: () => removed };
}

test("picker cancellation is not a successful import and removes its input", async () => {
    const cancelled = picker([], "cancel");
    assert.equal(await cancelled.storage.pickBackup(), null);
    assert.equal(cancelled.removed(), true);
});

test("picker bounds bytes before reading and rejects invalid UTF-8 rather than replacing characters", async () => {
    let read = false;
    const oversized = picker([{ size: 16 * 1024 * 1024 + 1, arrayBuffer() { read = true; } }]);
    await assert.rejects(oversized.storage.pickBackup(), /16 MiB/);
    assert.equal(read, false);
    assert.equal(oversized.removed(), true);
    const invalid = picker([{ size: 1, arrayBuffer: async () => new Uint8Array([0xff]).buffer }]);
    await assert.rejects(invalid.storage.pickBackup(), /encoded data/);
    assert.equal(invalid.removed(), true);
    const valid = picker([{ size: 2, arrayBuffer: async () => new TextEncoder().encode("{}").buffer }]);
    assert.equal(await valid.storage.pickBackup(), "{}");
});

test("download initiation failures propagate rather than reporting export success", () => {
    const storage = bridge(null, { URL: { createObjectURL() { throw new Error("Download blocked"); } } });
    assert.throws(() => storage.downloadBackup("{}"), /Download blocked/);
});

test("two tabs cannot overwrite each other's revision", async () => {
    const db = new IDBFactory();
    const first = bridge(db);
    const second = bridge(db);
    const saved = JSON.parse(await first.write(null, "original"));
    const outcomes = await Promise.allSettled([
        first.write(saved.revision, "first"),
        second.write(saved.revision, "second")
    ]);
    assert.equal(outcomes.filter(x => x.status === "fulfilled").length, 1);
    assert.equal(outcomes.filter(x => x.status === "rejected").length, 1);
    await assert.rejects(second.write(null, "stale initialization"), /another tab/);
    assert.notEqual(JSON.parse(await first.read()).payload, "stale initialization");
});

test("unavailable IndexedDB and open errors reject, never create a memory fallback", async () => {
    await assert.rejects(bridge(null).read(), /unavailable/);
    await assert.rejects(bridge({ open() { throw new Error("Permission denied"); } }).read(), /Permission denied/);
});

test("an aborted quota write rejects and preserves the previous committed document", async () => {
    const storage = bridge();
    const before = JSON.parse(await storage.write(null, "original"));
    const put = IDBObjectStore.prototype.put;
    IDBObjectStore.prototype.put = function () {
        throw new DOMException("Quota exceeded", "QuotaExceededError");
    };
    try {
        await assert.rejects(storage.write(before.revision, "not committed"), /Quota exceeded/);
        assert.deepEqual(JSON.parse(await storage.read()), before);
    } finally {
        IDBObjectStore.prototype.put = put;
    }
});

test("a corrupt IndexedDB envelope is not mistaken for an empty database", async () => {
    const db = new IDBFactory();
    const storage = bridge(db);
    await storage.write(null, "original");
    await new Promise((resolve, reject) => {
        const request = db.open("ev-charging-timer", 1);
        request.onerror = () => reject(request.error);
        request.onsuccess = () => {
            const connection = request.result;
            const tx = connection.transaction("state", "readwrite");
            tx.objectStore("state").put(null, "app");
            tx.oncomplete = () => { connection.close(); resolve(); };
            tx.onabort = () => { connection.close(); reject(tx.error); };
        };
    });
    await assert.rejects(storage.read(), /corrupt/);
    await assert.rejects(storage.write(null, "defaults"), /corrupt/);
});
