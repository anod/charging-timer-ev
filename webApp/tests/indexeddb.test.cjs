const { test } = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const { webcrypto } = require("node:crypto");
const { IDBFactory, IDBObjectStore } = require("fake-indexeddb");
const source = fs.readFileSync("src/wasmJsMain/resources/browser-storage.js", "utf8");

function bridge(indexedDB = new IDBFactory()) {
    const root = { indexedDB, crypto: webcrypto };
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
