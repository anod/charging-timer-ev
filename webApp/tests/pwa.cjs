const { chromium } = require("playwright");
const assert = require("node:assert/strict");
const http = require("node:http");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { prepare } = require("../pwa/prepare.cjs");

const distribution = path.resolve("build/dist/wasmJs/productionExecutable");
const evidence = path.resolve("build/browser-evidence");
const mime = { ".wasm": "application/wasm", ".js": "application/javascript",
    ".mjs": "application/javascript", ".html": "text/html", ".png": "image/png",
    ".webmanifest": "application/manifest+json", ".json": "application/json" };
const prefix = "/charging-timer-ev/";

async function state(page) {
    await page.waitForFunction(async () => globalThis.evTimerStorage && await evTimerStorage.read());
    return page.evaluate(async () => JSON.parse(JSON.parse(await evTimerStorage.read()).payload));
}

async function start(page) {
    const button = page.getByRole("button", { name: "Start Timer", exact: true });
    await button.waitFor();
    await page.waitForTimeout(3000);
    const box = await button.boundingBox();
    assert.ok(box);
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    await page.waitForFunction(async () =>
        JSON.parse(JSON.parse(await evTimerStorage.read()).payload).activeSession !== null);
    return (await state(page)).activeSession;
}

async function status(page, expected) {
    await page.waitForFunction(expected => globalThis.evTimerPwa?.status() === expected, expected,
        { timeout: 120_000 });
}

async function labels(page, ...texts) {
    for (const text of texts) {
        await page.getByText(text, { exact: true }).waitFor();
    }
    // Let Compose paint the frame corresponding to the populated semantics.
    await page.evaluate(() => new Promise(resolve =>
        requestAnimationFrame(() => requestAnimationFrame(resolve))));
}

async function controlled(page, url) {
    await page.goto(url);
    await status(page, "ready");
    await page.evaluate(() => navigator.serviceWorker.ready);
    await page.reload();
    await page.waitForFunction(() => !!navigator.serviceWorker.controller);
    await state(page);
}

(async () => {
    const temporary = fs.mkdtempSync(path.join(os.tmpdir(), "ev-pwa-"));
    fs.mkdirSync(evidence, { recursive: true });
    const a = path.join(temporary, "a");
    const b = path.join(temporary, "b");
    fs.cpSync(distribution, a, { recursive: true });
    fs.cpSync(distribution, b, { recursive: true });
    // Same marketing version and filenames, different content => different worker.
    fs.appendFileSync(path.join(b, "index.html"), "\n<!-- distribution B -->\n");
    const versionA = prepare(a);
    const versionB = prepare(b);
    assert.notEqual(versionA, versionB);
    assert.equal(prepare(b), versionB, "Build version must be deterministic");
    let serving = a;
    let fail = null;
    let privacyRevision = 1;
    const server = http.createServer((request, response) => {
        const url = new URL(request.url, "http://localhost");
        const relative = url.pathname.startsWith(prefix) ? url.pathname.slice(prefix.length) :
            url.pathname.slice(1);
        response.setHeader("Cache-Control", "no-store");
        if (["privacy", "privacy.html", "privacy/", "docs/privacy.html"].includes(relative)) {
            response.setHeader("Content-Type", "text/html");
            response.end(`<title>Privacy ${privacyRevision}</title>Privacy ${privacyRevision}`);
            return;
        }
        if (relative === fail) { response.writeHead(503).end("Simulated partial deployment"); return; }
        const file = path.resolve(serving, decodeURIComponent(relative || "index.html"));
        if (!file.startsWith(serving + path.sep)) { response.writeHead(403).end(); return; }
        fs.readFile(file, (error, bytes) => {
            if (error) { response.writeHead(404).end(); return; }
            response.setHeader("Content-Type", mime[path.extname(file)] || "application/octet-stream");
            response.end(bytes);
        });
    });
    await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
    const origin = `http://127.0.0.1:${server.address().port}`;
    const url = origin + prefix;
    const browser = await chromium.launch();
    try {
        const context = await browser.newContext({ locale: "en-US" });
        const page = await context.newPage();
        await controlled(page, url);
        const cdp = await context.newCDPSession(page);
        const appManifest = await cdp.send("Page.getAppManifest");
        assert.deepEqual(appManifest.errors, []);
        const manifest = JSON.parse(appManifest.data);
        assert.equal(manifest.name, "EV Charging Timer");
        assert.equal(manifest.short_name, "EV Timer");
        assert.equal(manifest.display, "standalone");
        assert.equal(new URL(manifest.start_url, appManifest.url).href, url);
        assert.equal(new URL(manifest.scope, appManifest.url).href, url);
        assert.deepEqual((await cdp.send("Page.getInstallabilityErrors")).installabilityErrors, []);
        for (const icon of manifest.icons) {
            const dimensions = await page.evaluate(async src => {
                const image = new Image();
                image.src = new URL(src, document.baseURI);
                await image.decode();
                return `${image.naturalWidth}x${image.naturalHeight}`;
            }, icon.src);
            assert.equal(dimensions, icon.sizes);
        }
        assert.equal(await page.locator('meta[name="apple-mobile-web-app-capable"]').getAttribute("content"), "yes");
        const apple = await page.locator('link[rel="apple-touch-icon"]').getAttribute("href");
        assert.equal(await page.evaluate(async src => {
            const image = new Image(); image.src = src; await image.decode(); return image.naturalWidth;
        }, apple), 180);
        fs.writeFileSync(path.join(evidence, "pwa-manifest.json"),
            JSON.stringify({ manifest, installability: "no Chromium errors", versionA, versionB }, null, 2));
        const active = await start(page);
        const before = await state(page);
        // Emulate suspension by ageing the real persisted epoch, not render clocks.
        await page.evaluate(async () => {
            const record = JSON.parse(await evTimerStorage.read());
            const value = JSON.parse(record.payload);
            value.activeSession.segments[0].startedAt -= 60_000;
            await evTimerStorage.write(record.revision, JSON.stringify(value));
        });
        const aged = (await state(page)).activeSession;
        assert.equal(aged.segments[0].startedAt, active.segments[0].startedAt - 60_000);
        await context.setOffline(true);
        await page.reload();
        await page.getByText("Charging in Progress", { exact: true }).waitFor();
        const offline = await state(page);
        assert.deepEqual(offline.activeSession, aged);
        assert.deepEqual(offline.backup, before.backup);
        assert.equal(await page.evaluate(() => navigator.onLine), false);
        await context.setOffline(false);

        // A failed B install must leave A and IndexedDB usable, with no partial B cache.
        serving = b;
        fail = "icons/icon-192.png";
        await page.evaluate(() => navigator.serviceWorker.getRegistration().then(r => r.update()));
        await status(page, "error");
        assert.equal((await page.evaluate(() => caches.keys())).filter(n => n.endsWith(versionB)).length, 0);
        assert.deepEqual((await state(page)).activeSession, aged);
        await page.reload();
        await state(page);
        fail = null;
        await page.evaluate(() => navigator.serviceWorker.getRegistration().then(r => r.update()));
        await status(page, "update");
        assert.deepEqual((await state(page)).activeSession, aged);
        assert.equal(await page.evaluate(() => navigator.serviceWorker.controller.scriptURL), url + "sw.js");
        // A remains in control through reload even with B fully installed.
        await page.reload();
        await status(page, "update");
        assert.ok(!(await page.content()).includes("distribution B"));
        assert.deepEqual((await state(page)).activeSession, aged);
        const other = await context.newPage();
        await other.goto(url);
        await state(other);
        await page.close();
        assert.ok(!(await other.content()).includes("distribution B"), "A second open window also delays B");
        let bWorker;
        for (const candidate of context.serviceWorkers()) {
            if (await candidate.evaluate(() => VERSION) === versionB) bWorker = candidate;
        }
        assert.ok(bWorker);
        const activation = bWorker.evaluate(() => new Promise(resolve => {
            const waiting = self.registration.waiting;
            if (!waiting) { resolve(); return; }
            waiting.addEventListener("statechange", () => {
                if (waiting.state === "activated") resolve();
            });
        }));
        await other.close();
        await activation;
        const next = await context.newPage();
        await next.goto(url);
        await status(next, "ready");
        assert.ok((await next.content()).includes("distribution B"));
        assert.deepEqual((await state(next)).activeSession, aged);
        assert.deepEqual((await state(next)).backup, before.backup);
        assert.equal((await next.evaluate(() => caches.keys())).filter(n => n.endsWith(versionA)).length, 0);

        // Missing and corrupted entries recover only from matching, verified network bytes.
        await next.evaluate(async () => {
            await caches.open("unrelated-app").then(c => c.put("/unrelated", new Response("keep")));
            const name = (await caches.keys()).find(n => n.startsWith("ev-timer-shell:"));
            const cache = await caches.open(name);
            await cache.put(new URL("browser-storage.js", location.href), new Response("corrupt"));
            await cache.delete(new URL("icons/icon-192.png", location.href));
        });
        await next.reload();
        assert.deepEqual((await state(next)).activeSession, aged);
        assert.equal(await next.evaluate(async () => {
            const response = await fetch("icons/icon-192.png");
            return response.ok;
        }), true);
        await next.evaluate(async () => {
            const name = (await caches.keys()).find(n => n.startsWith("ev-timer-shell:"));
            await caches.open(name).then(c => c.put(new URL("icons/icon-192.png", location.href),
                new Response("corrupt")));
        });
        await context.setOffline(true);
        assert.equal(await next.evaluate(async () => (await fetch("icons/icon-192.png")).status), 503);
        await status(next, "error");
        await context.setOffline(false);
        assert.equal(await next.evaluate(async () => (await fetch("icons/icon-192.png")).status), 200);
        assert.equal(await next.evaluate(() => caches.has("unrelated-app")), true);

        for (const privacy of ["privacy", "privacy.html", "privacy/", "docs/privacy.html"]) {
            privacyRevision++;
            assert.ok(await next.evaluate(async p => (await fetch(p)).text(), privacy)
                .then(text => text.includes(`Privacy ${privacyRevision}`)));
            await context.setOffline(true);
            assert.equal(await next.evaluate(async p => {
                try { await fetch(p); return "cached"; } catch { return "network-only"; }
            }, privacy), "network-only");
            await context.setOffline(false);
        }
        await next.goto(url + "privacy.html");
        assert.ok((await next.content()).includes(`Privacy ${privacyRevision}`));
        await context.close();

        // Persisted browser profile survives an actual context/browser-profile reopen.
        const profile = path.join(temporary, "profile");
        let persistent = await chromium.launchPersistentContext(profile, { headless: true });
        let tab = persistent.pages()[0];
        await controlled(tab, url);
        const persistedActive = await start(tab);
        const persistedBackup = (await state(tab)).backup;
        await persistent.close();
        persistent = await chromium.launchPersistentContext(profile, { headless: true, offline: true });
        tab = persistent.pages()[0];
        await tab.goto(url);
        await tab.getByText("Charging in Progress", { exact: true }).waitFor();
        assert.deepEqual((await state(tab)).activeSession, persistedActive);
        assert.deepEqual((await state(tab)).backup, persistedBackup);
        await persistent.close();

        for (const colorScheme of ["light", "dark"]) {
            for (const [device, viewport] of [
                ["phone", { width: 390, height: 844 }], ["desktop", { width: 1100, height: 900 }]
            ]) {
                const shotContext = await browser.newContext({ colorScheme, viewport, locale: "en-US" });
                const shot = await shotContext.newPage();
                await controlled(shot, url);
                await labels(shot, "Ready for offline use.", "Start Timer", "Charging data",
                    "Charging history", "Enable foreground notifications");
                await shot.screenshot({ path: path.join(evidence, `pwa-ready-${device}-${colorScheme}.png`) });
                await start(shot);
                await shotContext.setOffline(true);
                await shot.reload();
                await labels(shot, "Ready for offline use.", "Charging in Progress", "Charging data",
                    "Charging history", "Enable foreground notifications");
                await shot.screenshot({ path: path.join(evidence, `pwa-running-offline-${device}-${colorScheme}.png`) });
                await shotContext.setOffline(false);
                serving = a;
                await shot.evaluate(() => navigator.serviceWorker.getRegistration().then(r => r.update()));
                await status(shot, "update");
                await labels(shot, "Update ready. Close all timer tabs and installed windows, then reopen. Saved timers and data stay intact.",
                    "Charging in Progress", "Charging data", "Charging history");
                await shot.screenshot({ path: path.join(evidence, `pwa-update-${device}-${colorScheme}.png`) });
                await shotContext.close();
                serving = b;
            }
        }
        const localContext = await browser.newContext();
        const local = await localContext.newPage();
        await controlled(local, origin + "/");
        assert.equal(await local.evaluate(() => navigator.serviceWorker.getRegistration().then(r => r.scope)),
            origin + "/");
        await localContext.close();
        const failedContext = await browser.newContext({ locale: "en-US" });
        fail = "sw.js";
        const failed = await failedContext.newPage();
        await failed.goto(url);
        await status(failed, "error");
        await labels(failed, "Offline setup or update failed. Reconnect and reopen to retry. Keep a backup; do not clear site data.");
        const stillUsable = await start(failed);
        assert.deepEqual((await state(failed)).activeSession, stillUsable,
            "Registration failure must not stop the online workflow or clear data");
        await failedContext.close();
        fail = null;
        const insecureContext = await browser.newContext();
        await insecureContext.addInitScript(() => {
            Object.defineProperty(globalThis, "isSecureContext", { value: false });
        });
        const insecure = await insecureContext.newPage();
        await insecure.goto(url);
        await status(insecure, "unsupported");
        assert.equal(await insecure.evaluate(() => navigator.serviceWorker.getRegistrations().then(r => r.length)), 0);
        await insecureContext.close();
        console.log("Production Chromium PWA: installability, root/project scopes, offline active timer, " +
            "profile restart, safe waiting update, failed precache, corruption recovery and privacy isolation passed");
    } finally {
        await browser.close();
        await new Promise(resolve => server.close(resolve));
        fs.rmSync(temporary, { recursive: true, force: true });
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
