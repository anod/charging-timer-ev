// Opaque UTF-8 JSON stays in Kotlin: parsing it in JS would round 64-bit session IDs.
(function (root) {
    "use strict";
    const databaseName = "ev-charging-timer";
    const storeName = "state";
    const key = "app";
    const maxBytes = 16 * 1024 * 1024;

    function open() {
        return new Promise((resolve, reject) => {
            let request;
            try {
                if (!root.indexedDB) throw new Error("IndexedDB is unavailable");
                request = root.indexedDB.open(databaseName, 1);
            } catch (error) {
                reject(error);
                return;
            }
            let blocked = false;
            request.onblocked = () => {
                blocked = true;
                reject(new Error("Storage upgrade blocked. Close other tabs and retry."));
            };
            request.onerror = () => reject(request.error);
            request.onupgradeneeded = () => {
                if (!request.result.objectStoreNames.contains(storeName)) {
                    request.result.createObjectStore(storeName);
                }
            };
            request.onsuccess = () => {
                const db = request.result;
                if (blocked) {
                    db.close();
                    return;
                }
                db.onversionchange = () => db.close();
                resolve(db);
            };
        });
    }

    async function transact(write, expectedRevision, payload) {
        const db = await open();
        return new Promise((resolve, reject) => {
            let tx;
            try {
                tx = write
                    ? db.transaction(storeName, "readwrite", { durability: "strict" })
                    : db.transaction(storeName, "readonly");
            } catch (error) {
                db.close();
                reject(error);
                return;
            }
            let result = null;
            let failure;
            tx.oncomplete = () => { db.close(); resolve(JSON.stringify(result)); };
            tx.onabort = () => { db.close(); reject(failure || tx.error || new Error("Storage transaction aborted")); };
            tx.onerror = () => { failure = tx.error || failure; };
            const store = tx.objectStore(storeName);
            const request = store.get(key);
            request.onsuccess = () => {
                const current = request.result;
                if (current !== undefined && (!current || typeof current.revision !== "string" ||
                    current.revision.length === 0 || typeof current.payload !== "string")) {
                    failure = new Error("Browser storage record is corrupt. Existing data was not replaced.");
                    tx.abort();
                    return;
                }
                if (!write) {
                    result = current === undefined ? null : current;
                    return;
                }
                if ((current === undefined ? null : current.revision) !== expectedRevision) {
                    failure = new Error("Storage changed in another tab. Reload and retry.");
                    tx.abort();
                    return;
                }
                try {
                    result = { revision: root.crypto.randomUUID(), payload };
                    store.put(result, key);
                } catch (error) {
                    failure = error;
                    tx.abort();
                }
            };
        });
    }

    function pickBackup() {
        return new Promise((resolve, reject) => {
            const input = root.document.createElement("input");
            input.type = "file";
            input.accept = ".json,application/json";
            const cleanup = () => input.remove();
            input.oncancel = () => { cleanup(); resolve(null); };
            input.onchange = async () => {
                try {
                    const file = input.files[0];
                    if (!file) { resolve(null); return; }
                    if (file.size > maxBytes) throw new Error("Backup exceeds 16 MiB");
                    const bytes = await file.arrayBuffer();
                    if (bytes.byteLength > maxBytes) throw new Error("Backup exceeds 16 MiB");
                    resolve(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
                } catch (error) {
                    reject(error);
                } finally {
                    cleanup();
                }
            };
            root.document.body.append(input);
            try {
                input.click();
            } catch (error) {
                cleanup();
                reject(error);
            }
        });
    }

    function downloadBackup(text) {
        const blob = new Blob([text], { type: "application/json;charset=utf-8" });
        if (blob.size > maxBytes) throw new Error("Backup exceeds 16 MiB");
        const url = root.URL.createObjectURL(blob);
        const anchor = root.document.createElement("a");
        anchor.href = url;
        anchor.download = "ev-charging-timer-backup.json";
        try {
            root.document.body.append(anchor);
            anchor.click();
        } finally {
            anchor.remove();
            root.setTimeout(() => root.URL.revokeObjectURL(url), 60_000);
        }
    }

    root.evTimerStorage = {
        read: () => transact(false),
        write: (revision, payload) => transact(true, revision, payload),
        pickBackup,
        downloadBackup
    };
})(globalThis);
