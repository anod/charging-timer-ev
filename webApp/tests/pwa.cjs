const { chromium, firefox } = require("playwright");
const assert = require("node:assert/strict");
const http = require("node:http");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { prepare } = require("../pwa/prepare.cjs");
const { createHash } = require("node:crypto");
const { button: clickCanvasButton, text: clickCanvasText } = require("./canvas.cjs");

const distribution = path.resolve(process.argv[2] || "build/dist/wasmJs/productionExecutable");
const pagesSite = process.argv[2] !== undefined;
const engine = process.env.EV_PWA_BROWSER === "firefox" ? firefox : chromium;
const evidence = path.resolve(process.env.EV_PWA_EVIDENCE || "build/browser-evidence");
const mime = { ".wasm": "application/wasm", ".js": "application/javascript",
    ".mjs": "application/javascript", ".html": "text/html", ".png": "image/png",
    ".webmanifest": "application/manifest+json", ".json": "application/json" };
const prefix = "/charging-timer-ev/";
const observed = new WeakSet();

function observe(page) {
    if (observed.has(page)) return;
    observed.add(page);
    page.on("pageerror", error => console.error("PWA page error:", error.message));
    page.on("console", message => {
        if (message.type() === "error") console.error("PWA console:", message.text());
    });
}

async function state(page, accept = () => true) {
    const deadline = Date.now() + 120_000;
    while (Date.now() < deadline) {
        const value = await page.evaluate(async () => {
            if (!globalThis.evTimerStorage) return null;
            const record = JSON.parse(await evTimerStorage.read());
            return record === null ? null : JSON.parse(record.payload);
        });
        if (value !== null && accept(value)) return value;
        await page.waitForTimeout(100);
    }
    fs.writeFileSync(path.join(evidence, "pwa-failure-dom.html"), await page.content());
    await page.screenshot({ path: path.join(evidence, "pwa-failure.png") });
    assert.fail("Expected persisted browser state was not committed");
}

async function start(page) {
    const button = page.getByRole("button", { name: "Start Timer", exact: true });
    await button.waitFor();
    await page.waitForTimeout(3000);
    const box = await button.boundingBox();
    assert.ok(box);
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    return (await state(page, value => value.activeSession !== null)).activeSession;
}

async function status(page, expected) {
    await page.waitForFunction(expected => globalThis.evTimerPwa?.status() === expected, expected,
        { timeout: 120_000 });
}

async function shell(page, expected) {
    try {
        await page.waitForFunction(expected => document.documentElement.dataset.shell === expected,
            expected, { timeout: 30_000 });
    } catch (error) {
        console.error("Shell mismatch:", await page.evaluate(async () => ({
            shell: document.documentElement.dataset.shell, status: globalThis.evTimerPwa?.status(),
            navigation: performance.getEntriesByType("navigation").map(entry => entry.type),
            legacyNavigation: performance.navigation?.type,
            refreshGuard: sessionStorage.getItem(`ev-timer-refresh:${location.pathname}`),
            html: document.documentElement.outerHTML.slice(0, 1000),
            caches: await caches.keys(), controlled: !!navigator.serviceWorker.controller,
            registration: await navigator.serviceWorker.getRegistration().then(r => ({
                active: r.active?.state, waiting: r.waiting?.state,
                controller: navigator.serviceWorker.controller?.state,
                same: navigator.serviceWorker.controller === r.active
            })),
            inventories: await Promise.all((await caches.keys()).filter(n => n.startsWith("ev-timer-shell"))
                .map(async name => ({ name, html: await (await (await caches.open(name))
                    .match(new URL("index.html", location.href)))?.text().then(t => t.slice(0, 70)) })))
        })));
        await page.screenshot({ path: path.join(evidence, "pwa-shell-failure.png") });
        throw error;
    }
}

async function labels(page, ...texts) {
    const deadline = Date.now() + 30_000;
    for (const text of texts) {
        // Compose merges some row labels and values into one semantics node.
        while (!(await page.locator("body").ariaSnapshot()).includes(text)) {
            if (Date.now() >= deadline) {
                fs.writeFileSync(path.join(evidence, "pwa-label-failure.txt"),
                    await page.locator("body").ariaSnapshot());
                await page.screenshot({ path: path.join(evidence, "pwa-label-failure.png") });
                assert.fail(`Visible semantics must contain: ${text}`);
            }

            await page.waitForTimeout(100);
        }
    }
    // Let Compose paint the frame corresponding to the populated semantics.
    await page.evaluate(() => new Promise(resolve =>
        requestAnimationFrame(() => requestAnimationFrame(resolve))));
}

async function noticeControls(page, action) {
    await labels(page, action, "Stop Timer");
    await page.waitForTimeout(500);
    const notice = await page.getByRole("button", { name: action, exact: true }).boundingBox();
    const stop = await page.getByRole("button", { name: "Stop Timer", exact: true }).boundingBox();
    assert.ok(notice && stop);
    assert.ok(notice.width >= 48 && notice.height >= 48, "Action has a 48px target");
    const centerY = notice.y + notice.height / 2;
    assert.ok(stop.y + stop.height <= centerY - 24, "48px target must not overlap Stop Timer");
    assert.ok(centerY + 24 <= page.viewportSize().height, "48px target stays inside viewport");
    return { x: notice.x + notice.width / 2, y: centerY + 23 };
}

async function controlled(page, url) {
    observe(page);
    await page.goto(url);
    await status(page, "ready");
    await page.evaluate(() => navigator.serviceWorker.ready);
    await refresh(page);
    await page.waitForFunction(() => !!navigator.serviceWorker.controller);
    await state(page);
}

async function refresh(page) {
    // Exercise the browser reload algorithm used by the adapter too, rather
    // than relying on Firefox automation's navigation/reload flags.
    await Promise.all([
        page.waitForEvent("load"),
        page.evaluate(() => location.reload())
    ]);
}

(async () => {
    const temporary = fs.mkdtempSync(path.join(os.tmpdir(), "ev-pwa-"));
    fs.mkdirSync(evidence, { recursive: true });
    const a = path.join(temporary, "a");
    const b = path.join(temporary, "b");
    const c = path.join(temporary, "c");
    fs.cpSync(distribution, a, { recursive: true });
    fs.cpSync(distribution, b, { recursive: true });
    // Same marketing version and filenames, different content => different worker.
    fs.writeFileSync(path.join(b, "index.html"),
        fs.readFileSync(path.join(b, "index.html"), "utf8").replace("<html ", '<html data-shell="B" '));
    fs.appendFileSync(path.join(b, "browser-storage.js"), "\n// Shell B integrity probe.\n");
    const versionA = prepare(a);
    assert.deepEqual(fs.readFileSync(path.join(a, "sw.js")),
        fs.readFileSync(path.join(distribution, "sw.js")),
        "The tested worker must be byte-identical to the uploaded worker");
    const versionB = prepare(b);
    fs.cpSync(b, c, { recursive: true });
    fs.writeFileSync(path.join(c, "index.html"),
        fs.readFileSync(path.join(c, "index.html"), "utf8").replace('data-shell="B"', 'data-shell="C"'));
    prepare(c);
    const legacyA = path.join(temporary, "legacy-a");
    const legacyB = path.join(temporary, "legacy-b");
    for (const [root, label] of [[legacyA, "legacy-A"], [legacyB, "legacy-B"]]) {
        fs.cpSync(process.env.EV_PWA_LEGACY_DIST || a, root, { recursive: true });
        if (!process.env.EV_PWA_LEGACY_DIST) {
            fs.copyFileSync(path.join(__dirname, "fixtures/legacy-browser-pwa.js"),
                path.join(root, "browser-pwa.js"));
        }
        fs.writeFileSync(path.join(root, "index.html"),
            fs.readFileSync(path.join(root, "index.html"), "utf8").replace("<html ", `<html data-shell="${label}" `));
        prepare(root);
        const lines = fs.readFileSync(path.join(root, "sw.js"), "utf8").split("\n");
        const template = fs.readFileSync(path.join(__dirname, "../pwa/legacy-worker.js"), "utf8")
            .replace(/\r\n/g, "\n");
        const assets = lines[1].slice("const ASSETS = ".length, -1);
        const error = JSON.parse(lines[2].slice("const ERROR_TEXT = ".length, -1));
        const version = createHash("sha256").update(assets + template + error).digest("hex");
        fs.writeFileSync(path.join(root, "sw.js"), `const VERSION = "${version}";\n` +
            lines.slice(1, 3).join("\n") + "\n" + template);
    }
    assert.notEqual(versionA, versionB);
    assert.equal(prepare(b), versionB, "Build version must be deterministic");
    let serving = a;
    let fail = null;
    let networkOffline = false;
    let privacyRevision = 1;
    const server = http.createServer((request, response) => {
        if (networkOffline) { request.socket.destroy(); return; }
        const url = new URL(request.url, "http://localhost");
        const relative = url.pathname.startsWith(prefix) ? url.pathname.slice(prefix.length) :
            url.pathname.slice(1);
        response.setHeader("Cache-Control", "no-store");
        if (["privacy", "privacy.html", "privacy/", "docs/privacy.html"].includes(relative)) {
            response.setHeader("Content-Type", "text/html");
            const policy = pagesSite && relative === "privacy.html" ?
                fs.readFileSync(path.join(serving, "privacy.html"), "utf8") : "";
            response.end(policy && privacyRevision === 1 ? policy :
                `${policy}<title>Privacy ${privacyRevision}</title>Privacy ${privacyRevision}`);
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
    const browser = await engine.launch();
    const profiles = new Set();
    const setOffline = async (context, value) => {
        // Also sever the server connection: browser offline emulation alone can
        // leave worker-originated requests online in some Chromium versions.
        networkOffline = value;
        await context.setOffline(value);
    };
    try {
        // Reproduce the owner's original installed/waiting worker with the old
        // adapter, which cannot send the new activation message.
        serving = legacyA;
        const legacyContext = await browser.newContext({ locale: "en-US", viewport: { width: 1100, height: 900 } });
        const legacyPage = await legacyContext.newPage();
        await controlled(legacyPage, url);
        const legacyActive = await start(legacyPage);
        const legacyBackup = (await state(legacyPage)).backup;
        const legacyOther = await legacyContext.newPage();
        await legacyOther.goto(url);
        await state(legacyOther);
        serving = legacyB;
        await legacyPage.evaluate(() => navigator.serviceWorker.getRegistration().then(r => r.update()));
        await status(legacyPage, "update");
        assert.equal(await legacyPage.evaluate(() =>
            navigator.serviceWorker.getRegistration().then(r => r.waiting?.state)), "installed");
        await legacyPage.screenshot({ path: path.join(evidence, "pwa-legacy-waiting-before.png") });
        for (const colorScheme of ["light", "dark"]) {
            await legacyPage.emulateMedia({ colorScheme });
            await legacyPage.waitForTimeout(500);
            await legacyPage.screenshot({ path: path.join(evidence, `pwa-legacy-waiting-before-${colorScheme}.png`) });
        }
        serving = b;
        await legacyPage.evaluate(async () => {
            const r = await navigator.serviceWorker.getRegistration();
            r.addEventListener("updatefound", () => { globalThis.legacyCandidate = r.installing; });
        });
        await legacyPage.evaluate(() => navigator.serviceWorker.getRegistration().then(r => r.update()));
        await legacyPage.waitForFunction(() => globalThis.legacyCandidate?.state === "activated");
        await legacyPage.waitForFunction(async () =>
            (await caches.keys()).some(name => name.startsWith("ev-timer-versions:")));
        await refresh(legacyPage);
        await shell(legacyPage, "B");
        assert.deepEqual((await state(legacyPage)).activeSession, legacyActive);
        assert.deepEqual((await state(legacyPage)).backup, legacyBackup);
        assert.equal(await legacyOther.locator("html").getAttribute("data-shell"), "legacy-A");
        assert.equal(await legacyOther.evaluate(async () => (await fetch("browser-pwa.js")).text()),
            fs.readFileSync(path.join(legacyA, "browser-pwa.js"), "utf8"));
        await labels(legacyPage, "Charging in Progress");
        await legacyPage.screenshot({ path: path.join(evidence, "pwa-legacy-refresh-after.png") });
        for (const colorScheme of ["light", "dark"]) {
            await legacyPage.emulateMedia({ colorScheme });
            await legacyPage.waitForTimeout(500);
            await legacyPage.screenshot({ path: path.join(evidence, `pwa-legacy-refresh-after-${colorScheme}.png`) });
        }
        await legacyContext.close();
        console.log("PWA: original waiting-worker reproduction and legacy refresh transition verified");
        serving = a;
        const context = await browser.newContext({ locale: "en-US" });
        const page = await context.newPage();
        await controlled(page, url);
        assert.equal(await page.evaluate(() => navigator.serviceWorker.getRegistration().then(r => r.scope)), url);
        if (pagesSite) {
            const policy = await page.evaluate(async () => (await fetch("privacy.html")).text());
            assert.ok(policy.includes("<!doctype html>"));
            assert.ok(policy.includes("EV Charging Timer privacy policy"));
            assert.equal(policy, fs.readFileSync(path.join(distribution, "privacy.html"), "utf8"),
                "The server must serve the exact packaged privacy page");
            assert.equal(await page.evaluate(async () => {
                const names = (await caches.keys()).filter(n => n.startsWith("ev-timer-shell"));
                for (const name of names) {
                    const keys = await (await caches.open(name)).keys();
                    if (keys.some(r => /(^|\/)privacy(?:[./]|$)/i.test(new URL(r.url).pathname))) return true;
                }
                return false;
            }), false, "The deployed privacy page must never be precached");
            const policyPage = await context.newPage();
            await policyPage.goto(url + "privacy.html");
            await policyPage.getByRole("heading", { name: "EV Charging Timer privacy policy", exact: true }).waitFor();
            assert.equal(await policyPage.getByRole("link", { name: "project's GitHub issues" })
                .getAttribute("href"), "https://github.com/anod/charging-timer-ev/issues");
            await policyPage.close();
            fs.writeFileSync(path.join(evidence, "pages-site-inventory.json"),
                JSON.stringify(fs.readdirSync(distribution, { recursive: true }).filter(name =>
                    fs.statSync(path.join(distribution, name)).isFile()).sort(), null, 2));
            console.log("Pages: exact packaged worker, project scope and rendered network-only privacy.html verified");
        }
        console.log("PWA: initial controlled shell");
        if (engine === chromium) {
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
        }
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
        await setOffline(context, true);
        await refresh(page);
        await page.getByText("Charging in Progress", { exact: true }).waitFor();
        const offline = await state(page);
        assert.deepEqual(offline.activeSession, aged);
        assert.deepEqual(offline.backup, before.backup);
        assert.equal(await page.evaluate(() => navigator.onLine), false);
        await setOffline(context, false);
        console.log("PWA: offline active snapshot retained");
        // A failed B install must leave A and IndexedDB usable, with no partial B cache.
        serving = b;
        fail = "icons/icon-192.png";
        await page.evaluate(() => navigator.serviceWorker.getRegistration().then(r => r.update()));
        await status(page, "error");
        await page.waitForFunction(async version => {
            const r = await navigator.serviceWorker.getRegistration();
            return !r.installing && !(await caches.keys()).some(name => name.endsWith(version));
        }, versionB);
        assert.equal((await page.evaluate(() => caches.keys())).filter(n => n.endsWith(versionB)).length, 0);
        assert.deepEqual((await state(page)).activeSession, aged);
        await refresh(page);
        await state(page);
        await labels(page, "Charging in Progress");
        await status(page, "error");
        await page.waitForFunction(async version => {
            const r = await navigator.serviceWorker.getRegistration();
            return !r.installing && !r.waiting && !(await caches.keys()).some(name => name.endsWith(version));
        }, versionB);
        assert.equal(await page.locator("html").getAttribute("data-shell"), null,
            "A failed refresh update must keep the verified old shell");
        fail = null;
        await page.evaluate(() => navigator.serviceWorker.getRegistration().then(r => r.update()));
        await status(page, "update");
        const other = await context.newPage();
        observe(other);
        await other.goto(url);
        await state(other);
        // Resource warm-up competes with the animated first tab; slow CI CPUs can exceed 30s.
        await other.waitForFunction(() => document.body.getAttribute("aria-busy") === "false",
            null, { timeout: 120_000 });
        await labels(other, "Charging in Progress");
        let otherNavigations = 0;
        other.on("domcontentloaded", () => { otherNavigations++; });
        assert.deepEqual((await state(page)).activeSession, aged);
        assert.equal(await page.evaluate(() => navigator.serviceWorker.controller.scriptURL), url + "sw.js");
        await labels(page, "Update available", "Refresh",
            "Charging in Progress");
        await page.screenshot({ path: path.join(evidence, "pwa-refresh-before.png") });
        let refreshNavigations = 0;
        page.on("domcontentloaded", () => { refreshNavigations++; });
        await setOffline(context, true);
        await Promise.all([
            page.waitForEvent("load"),
            clickCanvasButton(page, "Refresh")
        ]);
        await shell(page, "B");
        await status(page, "ready");
        assert.equal(refreshNavigations, 2, "One manual refresh plus exactly one internal reload");
        assert.deepEqual((await state(page)).activeSession, aged);
        assert.deepEqual((await state(page)).backup, before.backup);
        await labels(page, "Charging in Progress");
        await page.screenshot({ path: path.join(evidence, "pwa-refresh-after.png") });
        assert.equal(await other.locator("html").getAttribute("data-shell"), null,
            "The other running window must keep shell A");
        assert.equal(otherNavigations, 0, "Never reload another timer window");
        assert.deepEqual((await state(other)).activeSession, aged);
        const oldStorage = fs.readFileSync(path.join(a, "browser-storage.js"), "utf8");
        assert.equal(await other.evaluate(async () => (await fetch("browser-storage.js")).text()), oldStorage);
        assert.equal((await page.evaluate(() => caches.keys())).filter(n => n.endsWith(versionA)).length, 1);
        // An update first discovered during refresh also completes that flow.
        await setOffline(context, false);
        serving = c;
        refreshNavigations = 0;
        await refresh(page);
        await shell(page, "C");
        await status(page, "ready");
        assert.equal(refreshNavigations, 2);
        assert.deepEqual((await state(page)).activeSession, aged);
        assert.deepEqual((await state(page)).backup, before.backup);
        await page.waitForTimeout(1500);
        assert.equal(refreshNavigations, 2, "No delayed controllerchange reload loop");
        assert.equal(otherNavigations, 0);
        await other.close();
        const next = page;
        console.log("PWA: waiting/refresh-discovered update, bounded reload and two-window isolation verified");

        // Missing and corrupted entries recover only from matching, verified network bytes.
        await next.evaluate(async () => {
            await caches.open("unrelated-app").then(c => c.put("/unrelated", new Response("keep")));
            const names = (await caches.keys()).filter(n => n.startsWith("ev-timer-shell"));
            const name = names.at(-1);
            const cache = await caches.open(name);
            await cache.put(new URL("browser-storage.js", location.href), new Response("corrupt"));
            await cache.delete(new URL("icons/icon-192.png", location.href));
        });
        await refresh(next);
        assert.deepEqual((await state(next)).activeSession, aged);
        assert.equal(await next.evaluate(async () => {
            const response = await fetch("icons/icon-192.png");
            return response.ok;
        }), true);
        await next.evaluate(async () => {
            const name = (await caches.keys()).filter(n => n.startsWith("ev-timer-shell")).at(-1);
            await caches.open(name).then(c => c.put(new URL("icons/icon-192.png", location.href),
                new Response("corrupt")));
        });
        await setOffline(context, true);
        assert.equal(await next.evaluate(async () => (await fetch("icons/icon-192.png")).status), 503);
        await status(next, "error");
        await setOffline(context, false);
        assert.equal(await next.evaluate(async () => (await fetch("icons/icon-192.png")).status), 200);
        assert.equal(await next.evaluate(() => caches.has("unrelated-app")), true);

        for (const privacy of ["privacy", "privacy.html", "privacy/", "docs/privacy.html"]) {
            privacyRevision++;
            assert.ok(await next.evaluate(async p => (await fetch(p)).text(), privacy)
                .then(text => text.includes(`Privacy ${privacyRevision}`)));
            await setOffline(context, true);
            assert.equal(await next.evaluate(async p => {
                try { await fetch(p); return "cached"; } catch { return "network-only"; }
            }, privacy), "network-only");
            await setOffline(context, false);
        }
        await next.goto(url + "privacy.html");
        assert.ok((await next.content()).includes(`Privacy ${privacyRevision}`));
        await context.close();
        console.log("PWA: cache repair and privacy isolation verified");

        // Persisted browser profile survives an actual context/browser-profile reopen.
        const profile = path.join(temporary, "profile");
        let persistent = await engine.launchPersistentContext(profile, { headless: true, locale: "en-US" });
        profiles.add(persistent);
        let tab = persistent.pages()[0];
        await controlled(tab, url);
        const persistedActive = await start(tab);
        const persistedBackup = (await state(tab)).backup;
        await persistent.close();
        profiles.delete(persistent);
        networkOffline = true;
        persistent = await engine.launchPersistentContext(profile, { headless: true, offline: true, locale: "en-US" });
        profiles.add(persistent);
        tab = persistent.pages()[0];
        observe(tab);
        await tab.goto(url);
        await tab.getByText("Charging in Progress", { exact: true }).waitFor();
        assert.deepEqual((await state(tab)).activeSession, persistedActive);
        assert.deepEqual((await state(tab)).backup, persistedBackup);
        await persistent.close();
        profiles.delete(persistent);
        networkOffline = false;
        console.log("PWA: browser-profile restart verified offline");

        for (const colorScheme of ["light", "dark"]) {
            for (const [device, viewport] of [
                ["phone", { width: 390, height: 844 }], ["desktop", { width: 1100, height: 900 }]
            ]) {
                const shotContext = await browser.newContext({ colorScheme, viewport, locale: "en-US" });
                const shot = await shotContext.newPage();
                await controlled(shot, url);
                await labels(shot, "Start Timer", "Charging Timer for EV",
                    "Default", "Collapse Start.", "Collapse Charging Power.");
                await shot.screenshot({ path: path.join(evidence, `pwa-ready-${device}-${colorScheme}.png`) });
                await clickCanvasButton(shot, "Settings");
                await labels(shot, "Ready for offline use.", "Charging data",
                    "Charging history", "Enable foreground notifications", "About", "Open-source licenses");
                await shot.screenshot({ path: path.join(evidence, `pwa-settings-${device}-${colorScheme}.png`) });
                await clickCanvasText(shot, "Presets");
                await labels(shot, "Presets", "Use the edit button to rename. Drag the handle to reorder.",
                    "Rename Default", "Keep at least one preset.");
                await clickCanvasButton(shot, "Back");
                await clickCanvasButton(shot, "Back");
                await start(shot);
                await setOffline(shotContext, true);
                await refresh(shot);
                await labels(shot, "Charging in Progress", "Estimated charging time",
                    "Charging Power", "Current %", "Target %", "Time Remaining", "Time to 80%", "Elapsed Time");
                await shot.screenshot({ path: path.join(evidence, `pwa-running-offline-${device}-${colorScheme}.png`) });
                await setOffline(shotContext, false);
                serving = a;
                fail = "icons/icon-192.png";
                await shot.evaluate(() => navigator.serviceWorker.getRegistration().then(r => r.update()));
                await status(shot, "error");
                await labels(shot, "Offline setup or update failed. Reconnect to retry.", "Details",
                    "Charging in Progress", "Estimated charging time",
                    "Charging Power", "Current %", "Target %", "Time Remaining", "Time to 80%", "Elapsed Time");
                await shot.screenshot({ path: path.join(evidence, `pwa-error-${device}-${colorScheme}.png`) });
                const detailsTarget = await noticeControls(shot, "Details");
                if (device === "desktop") {
                    await shot.locator("canvas").first().focus();
                    for (let tab = 0; tab < 3; tab++) await shot.keyboard.press("Tab");
                    await shot.keyboard.press("Enter");
                } else await shot.mouse.click(detailsTarget.x, detailsTarget.y);
                await labels(shot, "Offline setup or update failed. Reconnect and reopen to retry. Keep a backup; do not clear site data.",
                    "Charging data");
                await clickCanvasButton(shot, "Back");
                fail = null;
                await shot.evaluate(() => navigator.serviceWorker.getRegistration().then(r => r.update()));
                await status(shot, "update");
                await labels(shot, "Update available", "Refresh",
                    "Charging in Progress", "Estimated charging time",
                    "Charging Power", "Current %", "Target %", "Time Remaining", "Time to 80%", "Elapsed Time");
                await shot.screenshot({ path: path.join(evidence, `pwa-update-${device}-${colorScheme}.png`) });
                const refreshTarget = await noticeControls(shot, "Refresh");
                const dismissTarget = await noticeControls(shot, "Dismiss");
                const savedBeforeRefresh = await state(shot);
                if (colorScheme === "dark") {
                    await shot.mouse.click(dismissTarget.x, dismissTarget.y);
                    await shot.getByRole("button", { name: "Dismiss", exact: true }).waitFor({ state: "hidden" });
                    await shot.evaluate(() => {
                        history.pushState({ evTimerDepth: 2 }, "", "#/settings");
                        dispatchEvent(new PopStateEvent("popstate"));
                    });
                    await labels(shot, "Update ready. Refresh this window to apply it. Other timer windows and saved data stay intact.",
                        "Charging data");
                } else {
                    await Promise.all([
                        shot.waitForEvent("load"),
                        shot.mouse.click(refreshTarget.x, refreshTarget.y)
                    ]);
                    await status(shot, "ready");
                }
                assert.deepEqual((await state(shot)).activeSession, savedBeforeRefresh.activeSession);
                assert.deepEqual((await state(shot)).backup, savedBeforeRefresh.backup);
                if (colorScheme === "dark") await clickCanvasButton(shot, "Back");
                await clickCanvasButton(shot, "Stop Timer");
                await labels(shot, "Charge summary", "Car reading", "Not recorded", "Add car reading");
                await shotContext.close();
                console.log(`PWA: ${device} ${colorScheme} ready/offline/error/update evidence captured`);
                serving = b;
            }
        }
        const localContext = await browser.newContext({ locale: "en-US" });
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
        await labels(failed, "Offline setup or update failed. Reconnect to retry.", "Details");
        const stillUsable = await start(failed);
        assert.deepEqual((await state(failed)).activeSession, stillUsable,
            "Registration failure must not stop the online workflow or clear data");
        await failedContext.close();
        fail = null;
        const insecureContext = await browser.newContext({ locale: "en-US" });
        await insecureContext.addInitScript(() => {
            Object.defineProperty(globalThis, "isSecureContext", { value: false });
        });
        const insecure = await insecureContext.newPage();
        await insecure.goto(url);
        await status(insecure, "unsupported");
        assert.equal(await insecure.evaluate(() => navigator.serviceWorker.getRegistrations().then(r => r.length)), 0);
        await insecureContext.close();
        console.log(`Production ${engine.name()} PWA: root/project scopes, offline active timer, ` +
            "profile restart, bounded refresh updates, legacy transition, multi-window shell isolation, " +
            "failed precache, corruption recovery and privacy isolation passed");
    } finally {
        await Promise.all([...profiles].map(context => context.close()));
        await browser.close();
        server.closeAllConnections();
        await new Promise(resolve => server.close(resolve));
        fs.rmSync(temporary, { recursive: true, force: true });
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
