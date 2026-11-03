const { chromium } = require("playwright");
const assert = require("node:assert/strict");
const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve("build/dist/wasmJs/productionExecutable");
const screenshots = path.resolve("build/browser-evidence");
const mime = { ".wasm": "application/wasm", ".js": "application/javascript",
    ".mjs": "application/javascript", ".html": "text/html", ".json": "application/json" };

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
            await page.goto(origin);
            await page.waitForFunction(async () => {
                if (!globalThis.evTimerStorage) return false;
                return JSON.parse(await globalThis.evTimerStorage.read()) !== null;
            }, null, { timeout: 120_000 });
            const initial = await page.evaluate(async () => JSON.parse(await evTimerStorage.read()));
            const payload = JSON.parse(initial.payload);
            assert.equal(payload.backup.formatVersion, 2);
            assert.equal(payload.backup.presets.length, 1);
            assert.equal(payload.activeSession, null);
            await page.waitForSelector("canvas");
            // Compose's first frame and asynchronously loaded text resources must finish.
            await page.waitForTimeout(3000);
            await page.screenshot({ path: path.join(screenshots, `storage-${colorScheme}.png`) });

            const tab = await context.newPage();
            await tab.goto(origin);
            await tab.waitForFunction(() => !!globalThis.evTimerStorage);
            const reopened = await tab.evaluate(async () => JSON.parse(await evTimerStorage.read()));
            assert.equal(reopened.payload, initial.payload);
            // Real IndexedDB, not a localStorage or in-memory substitute.
            const next = await page.evaluate(async ({ revision, payload }) =>
                JSON.parse(await evTimerStorage.write(revision, payload)), initial);
            await assert.rejects(tab.evaluate(async ({ revision, payload }) =>
                evTimerStorage.write(revision, payload), initial), /another tab/);
            await page.reload();
            await page.waitForFunction(() => !!globalThis.evTimerStorage);
            assert.equal(await page.evaluate(async () => JSON.parse(await evTimerStorage.read()).revision), next.revision);
            assert.deepEqual(errors, []);
            await context.close();
        }
        console.log("Real Chromium: Wasm startup, IndexedDB commits/reload/two-tab conflict and light/dark screenshots passed");
    } finally {
        await browser.close();
        await new Promise(resolve => server.close(resolve));
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
