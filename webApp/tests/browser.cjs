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

async function clickCanvasButton(page, name) {
    const button = page.getByRole("button", { name, exact: true });
    await button.waitFor();
    const deadline = Date.now() + 10_000;
    while (!await button.isEnabled()) {
        assert.ok(Date.now() < deadline, `${name} must become enabled`);
        await new Promise(resolve => setTimeout(resolve, 50));
    }
    // Semantics layout can precede canvas painting, especially after centered
    // content reflows. Let the updated bounds reach the rendered canvas.
    await button.evaluate(() => new Promise(resolve =>
        requestAnimationFrame(() => requestAnimationFrame(resolve))));
    await button.click({ force: true, timeout: 10_000 });
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
            const context = await browser.newContext({
                colorScheme, locale: "en-US", viewport: { width: 1100, height: 800 }
            });
            const page = await context.newPage();
            const errors = [];
            page.on("pageerror", error => errors.push(error.message));
            page.on("console", message => console.log(`[${colorScheme}] ${message.type()}: ${message.text()}`));
            await page.goto(origin);
            console.log("Browser language preferences:", await page.evaluate(() => navigator.languages));
            const initial = await waitForRecord(page);
            await page.screenshot({ path: path.join(screenshots, `startup-${colorScheme}.png`) });
            fs.writeFileSync(path.join(screenshots, `semantics-${colorScheme}.txt`),
                await page.locator("body").ariaSnapshot());
            fs.writeFileSync(path.join(screenshots, `dom-${colorScheme}.html`), await page.content());
            assert.deepEqual(errors, []);
            assert.notEqual(initial, null, "Wasm must durably initialize IndexedDB before the UI is usable");
            const payload = JSON.parse(initial.payload);
            assert.equal(payload.backup.formatVersion, 2);
            assert.equal(payload.backup.presets.length, 1);
            assert.equal(payload.activeSession, null);
            await page.waitForSelector("canvas");
            // Compose's first frame and asynchronously loaded text resources must finish.
            await page.waitForTimeout(3000);
            await page.screenshot({ path: path.join(screenshots, `configuration-${colorScheme}.png`) });
            await clickCanvasButton(page, "Start Timer");
            const started = await waitForRecord(page, record => JSON.parse(record?.payload || "{}").activeSession != null);
            assert.notEqual(started, null, "Start must commit an active snapshot");
            const active = JSON.parse(started.payload).activeSession;
            await page.getByText("Charging in Progress", { exact: true }).waitFor();
            await page.screenshot({ path: path.join(screenshots, `running-${colorScheme}.png`) });
            await page.reload();
            await page.getByText("Charging in Progress", { exact: true }).waitFor();
            const reloaded = await waitForRecord(page);
            assert.deepEqual(JSON.parse(reloaded.payload).activeSession, active,
                "Reload must resume the persisted snapshot rather than restarting the timer");
            // Simulate a closed/suspended page by advancing wall-clock time without
            // running any ticks. pageshow must catch up and finish at the target epoch.
            await page.clock.install();
            await page.clock.setSystemTime(new Date(active.segments[0].startedAt + 30 * 24 * 3_600_000));
            await page.evaluate(() => window.dispatchEvent(new Event("pageshow")));
            const completed = await waitForRecord(page, record => {
                const state = JSON.parse(record?.payload || "{}");
                return state.activeSession === null && state.backup?.sessions.length === 1;
            });
            assert.notEqual(completed, null, "Resume must complete an elapsed timer");
            const history = JSON.parse(completed.payload).backup.sessions[0];
            assert.equal(history.endPercent, active.targetPercent);
            assert.ok(history.endedAt < active.segments[0].startedAt + 30 * 24 * 3_600_000,
                "History must use the calculated target time, not the page resume time");
            await clickCanvasButton(page, "Charging data");
            await page.screenshot({ path: path.join(screenshots, `storage-${colorScheme}.png`) });

            // Transparent semantics proxies share the canvas control's bounds but
            // have pointer-events:none. Send trusted pointer input through to the canvas.
            const [download] = await Promise.all([
                page.waitForEvent("download"),
                clickCanvasButton(page, "Export backup")
            ]);
            assert.equal(await download.failure(), null);
            const exported = JSON.parse(fs.readFileSync(await download.path(), "utf8"));
            assert.equal(exported.formatVersion, 2);
            assert.equal(exported.preferences.selectedPresetId, "custom");
            await page.getByText("Download requested. Check your browser downloads to confirm the file was saved.",
                { exact: true }).waitFor();
            const imported = JSON.stringify({
                ...exported,
                presets: [{ ...exported.presets[0], name: "Imported car" }],
                preferences: { ...exported.preferences, notifyAt80: false, startPercent: 21.5 },
                sessions: []
            }).replace('"sessions":[]', '"sessions":[{"id":9007199254740993,"batteryCapacity":83,' +
                '"startPercent":20,"targetPercent":80,"efficiency":0.9,"endedAt":300,' +
                '"actualPercent":78.5,"calibrationEnabled":true,' +
                '"segments":[{"startedAt":100,"powerKw":11,"efficiency":0.85}]}]');
            const [chooser] = await Promise.all([
                page.waitForEvent("filechooser"),
                clickCanvasButton(page, "Import backup")
            ]);
            await chooser.setFiles({
                name: "backup.json", mimeType: "application/json", buffer: Buffer.from(imported)
            });
            await page.getByRole("button", { name: "Replace data", exact: true }).waitFor();
            // Merely reading the file must not mutate persisted data.
            assert.equal(await page.evaluate(async () => JSON.parse(await evTimerStorage.read()).revision), completed.revision);
            await clickCanvasButton(page, "Replace data");
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
        console.log("Real Chromium: shared timer UI, durable start/reload, epoch catch-up/completion, backup roundtrip, IndexedDB conflicts and light/dark screenshots passed");
    } finally {
        await browser.close();
        await new Promise(resolve => server.close(resolve));
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
