const { chromium } = require("playwright");
const assert = require("node:assert/strict");
const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve("build/dist/wasmJs/productionExecutable");
const screenshots = path.resolve("build/browser-evidence");
const mime = { ".wasm": "application/wasm", ".js": "application/javascript",
    ".mjs": "application/javascript", ".html": "text/html", ".json": "application/json" };

async function waitForRecord(page, accept = record => record !== null) {
    const deadline = Date.now() + 120_000;
    while (Date.now() < deadline) {
        const record = await page.evaluate(async () =>
            globalThis.evTimerStorage ? JSON.parse(await globalThis.evTimerStorage.read()) : null);
        if (accept(record)) return record;
        await new Promise(resolve => setTimeout(resolve, 250));
    }
    return null;
}

(async () => {
    fs.mkdirSync(screenshots, { recursive: true });
    const server = http.createServer((request, response) => {
        const url = new URL(request.url, "http://localhost");
        const file = path.resolve(root, "." + decodeURIComponent(url.pathname === "/" ? "/index.html" : url.pathname));
        if (!file.startsWith(root + path.sep)) {
            response.writeHead(403).end();
            return;
        }
        fs.readFile(file, (error, bytes) => {
            if (error) { response.writeHead(404).end(); return; }
            response.setHeader("Content-Type", mime[path.extname(file)] || "application/octet-stream");
            response.end(bytes);
        });
    });
    await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
    const browser = await chromium.launch({ headless: true });
    try {
        const origin = `http://127.0.0.1:${server.address().port}`;
        for (const colorScheme of ["light", "dark"]) {
            const context = await browser.newContext({ colorScheme, viewport: { width: 1100, height: 800 } });
            const page = await context.newPage();
            const errors = [];
            page.on("pageerror", error => errors.push(error.message));
            page.on("console", message => console.log(`[${colorScheme}] ${message.type()}: ${message.text()}`));
            await page.goto(origin);
            const initial = await waitForRecord(page);
            await page.screenshot({ path: path.join(screenshots, `startup-${colorScheme}.png`) });
            fs.writeFileSync(path.join(screenshots, `semantics-${colorScheme}.txt`),
                await page.locator("body").ariaSnapshot());
            assert.deepEqual(errors, []);
            assert.notEqual(initial, null, "Wasm must durably initialize IndexedDB before the UI is usable");
            const payload = JSON.parse(initial.payload);
            assert.equal(payload.backup.formatVersion, 2);
            assert.equal(payload.backup.presets.length, 1);
            assert.equal(payload.activeSession, null);
            await page.waitForSelector("canvas");
            // Compose's first frame and asynchronously loaded text resources must finish.
            await page.waitForTimeout(3000);
            await page.screenshot({ path: path.join(screenshots, `storage-${colorScheme}.png`) });

            const downloadEvent = page.waitForEvent("download");
            await page.getByRole("button", { name: "Export backup", exact: true }).click();
            const download = await downloadEvent;
            assert.equal(await download.failure(), null);
            const exported = JSON.parse(fs.readFileSync(await download.path(), "utf8"));
            assert.equal(exported.formatVersion, 2);
            assert.equal(exported.preferences.selectedPresetId, "custom");
            const imported = JSON.stringify({
                ...exported,
                presets: [{ ...exported.presets[0], name: "Imported car" }],
                preferences: { ...exported.preferences, notifyAt80: false, startPercent: 21.5 },
                sessions: []
            }).replace('"sessions":[]', '"sessions":[{"id":9007199254740993,"batteryCapacity":83,' +
                '"startPercent":20,"targetPercent":80,"efficiency":0.9,"endedAt":300,' +
                '"actualPercent":78.5,"calibrationEnabled":true,' +
                '"segments":[{"startedAt":100,"powerKw":11,"efficiency":0.85}]}]');
            const chooserEvent = page.waitForEvent("filechooser");
            await page.getByRole("button", { name: "Import backup", exact: true }).click();
            await (await chooserEvent).setFiles({
                name: "backup.json", mimeType: "application/json", buffer: Buffer.from(imported)
            });
            await page.getByRole("button", { name: "Replace data", exact: true }).waitFor();
            // Merely reading the file must not mutate persisted data.
            assert.equal(await page.evaluate(async () => JSON.parse(await evTimerStorage.read()).revision), initial.revision);
            await page.getByRole("button", { name: "Replace data", exact: true }).click();
            const committed = await waitForRecord(page, record => record?.payload.includes("Imported car"));
            assert.notEqual(committed, null, "Confirmed import must commit the selected backup");
            assert.ok(committed.payload.includes('"id":9007199254740993'));
            const restored = JSON.parse(committed.payload);
            assert.equal(restored.backup.preferences.notifyAt80, false);
            assert.equal(restored.backup.preferences.startPercent, 21.5);
            assert.equal(restored.backup.sessions[0].calibrationEnabled, true);
            assert.equal(restored.backup.sessions[0].reviewed, true);
            assert.equal(restored.backup.sessions[0].segments[0].efficiency, 0.85);

            const tab = await context.newPage();
            await tab.goto(origin);
            await tab.waitForFunction(() => !!globalThis.evTimerStorage);
            const reopened = await tab.evaluate(async () => JSON.parse(await evTimerStorage.read()));
            assert.equal(reopened.payload, committed.payload);
            // Real IndexedDB, not a localStorage or in-memory substitute.
            const next = await page.evaluate(async ({ revision, payload }) =>
                JSON.parse(await evTimerStorage.write(revision, payload)), committed);
            await assert.rejects(tab.evaluate(async ({ revision, payload }) =>
                evTimerStorage.write(revision, payload), committed), /another tab/);
            await page.reload();
            await page.waitForFunction(() => !!globalThis.evTimerStorage);
            assert.equal(await page.evaluate(async () => JSON.parse(await evTimerStorage.read()).revision), next.revision);
            assert.deepEqual(errors, []);
            await context.close();
        }
        console.log("Real Chromium: Wasm startup, actual backup download/picker/confirmation/v2 restore, IndexedDB reload/two-tab conflict and light/dark screenshots passed");
    } finally {
        await browser.close();
        await new Promise(resolve => server.close(resolve));
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
