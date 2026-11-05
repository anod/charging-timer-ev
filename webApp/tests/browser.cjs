const { chromium } = require("playwright");
const assert = require("node:assert/strict");
const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");
const { button: clickCanvasButton, text: clickCanvasText, clickCanvas } = require("./canvas.cjs");
const { verifyDialogFocus } = require("./dialog-focus.cjs");

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

async function waitForAccessibleText(page, text) {
    const deadline = Date.now() + 10_000;
    while (!(await page.locator("body").ariaSnapshot()).includes(text)) {
        assert.ok(Date.now() < deadline, `Accessible UI must contain: ${text}`);
        await page.waitForTimeout(100);
    }
}

const configurationLabels = ["Charging Timer for EV", "Battery: 83 kWh", "Max: 100%",
    "Start", "Charging Power", "3.6 kW", "7.0 kW", "11.0 kW", "22.0 kW", "Start Timer"];
const runningLabels = ["Charging in Progress", "Charging Speed", "Current %", "Target %",
    "Time Remaining", "Time to 80%", "Elapsed Time", "Stop Timer"];
const summaryLabels = ["Charge summary", "Estimated at timer stop", "Default", "Elapsed Time",
    "Energy added", "Average power", "Charging speeds", "Done"];
const storageLabels = ["Charging data", "Charging timers, presets and history are saved in this browser.",
    "Export backup", "Import backup"];

async function capture(page, name, labels) {
    for (const label of labels) await waitForAccessibleText(page, label);
    // Let recomposed text reach the canvas, then clear the pointer/ripple from navigation.
    await page.mouse.move(0, 0);
    await page.waitForTimeout(300);
    await page.evaluate(() => new Promise(resolve =>
        requestAnimationFrame(() => requestAnimationFrame(resolve))));
    for (const label of labels) await waitForAccessibleText(page, label);
    await page.screenshot({ path: path.join(screenshots, `${name}.png`) });
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
        let dialogFailure;
        try {
            await verifyDialogFocus(browser, origin, screenshots);
        } catch (error) {
            dialogFailure = error;
            console.error("Strict dialog regressions failed; continuing independent parity checks:", error.stack);
        }
        // A cold page must not compose any labelled controls while .cvr string
        // resources are withheld. This tests the real network boundary, not a timer.
        const loadingContext = await browser.newContext({ locale: "en-US" });
        const loadingPage = await loadingContext.newPage();
        let releaseResources;
        const resourcesReady = new Promise(resolve => { releaseResources = resolve; });
        let resourceRequests = 0;
        const loadingErrors = [];
        loadingPage.on("pageerror", error => loadingErrors.push(error.message));
        await loadingPage.route(/\.cvr(?:\?|$)/, async route => {
            resourceRequests++;
            await resourcesReady;
            await route.continue();
        });
        await loadingPage.goto(origin);
        // Loading exposes only the unlabeled progress indicator, never controls
        // that depend on the withheld string resources.
        await loadingPage.locator("body[aria-busy=true]").waitFor();
        await loadingPage.waitForSelector("canvas");
        const requestDeadline = Date.now() + 10_000;
        while (!resourceRequests && Date.now() < requestDeadline) await loadingPage.waitForTimeout(50);
        fs.writeFileSync(path.join(screenshots, "loading-semantics.txt"), await loadingPage.locator("body").ariaSnapshot());
        fs.writeFileSync(path.join(screenshots, "loading-dom.html"), await loadingPage.content());
        assert.ok(resourceRequests > 0, "Test must hold an actual resource request");
        for (let frame = 0; frame < 5; frame++) {
            assert.equal(await loadingPage.locator("body").getAttribute("aria-busy"), "true");
            const snapshot = await loadingPage.locator("body").ariaSnapshot();
            assert.equal(snapshot.trim(), "- progressbar",
                "Before resources load, only an unlabeled progress indicator may be exposed");
            await loadingPage.evaluate(() => new Promise(requestAnimationFrame));
        }
        await capture(loadingPage, "startup-loading", []);
        releaseResources();
        await loadingPage.locator("body[aria-busy=false]").waitFor();
        for (const label of configurationLabels) await waitForAccessibleText(loadingPage, label);
        assert.deepEqual(loadingErrors, [], "Resource loading must finish without unhandled errors");
        await loadingContext.close();
        for (const colorScheme of ["light", "dark"]) {
            const context = await browser.newContext({
                colorScheme, locale: "en-US", viewport: { width: 1100, height: 900 }
            });
            const page = await context.newPage();
            const errors = [];
            page.on("pageerror", error => {
                errors.push(error.message);
                console.error("Browser page error:", error.stack);
            });
            page.on("console", message => console.log(`[${colorScheme}] ${message.type()}: ${message.text()}`));
            await page.goto(origin);
            console.log("Browser language preferences:", await page.evaluate(() => navigator.languages));
            const initial = await waitForRecord(page);
            await capture(page, `startup-${colorScheme}`, configurationLabels);
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
            await capture(page, `configuration-${colorScheme}`, configurationLabels);
            await clickCanvasButton(page, "Start Timer");
            const started = await waitForRecord(page, record => JSON.parse(record?.payload || "{}").activeSession != null);
            assert.notEqual(started, null, "Start must commit an active snapshot");
            const active = JSON.parse(started.payload).activeSession;
            try {
                await page.getByText("Charging in Progress", { exact: true }).waitFor();
            } catch (error) {
                console.error("Running route:", page.url(), await page.locator("body").ariaSnapshot(), errors);
                await page.screenshot({ path: path.join(screenshots, "running-failure.png") });
                throw error;
            }
            await capture(page, `running-${colorScheme}`, runningLabels);
            await page.reload();
            await page.getByText("Charging in Progress", { exact: true }).waitFor();
            const reloaded = await waitForRecord(page);
            assert.deepEqual(JSON.parse(reloaded.payload).activeSession, active,
                "Reload must resume the persisted snapshot rather than restarting the timer");
            await page.goBack();
            await clickCanvasButton(page, "Settings");
            const importButton = page.getByRole("button", { name: "Import backup", exact: true });
            await importButton.waitFor();
            await page.waitForTimeout(300);
            let blockedChooser = 0;
            const unexpectedPicker = chooser => { blockedChooser++; chooser.setFiles([]); };
            page.on("filechooser", unexpectedPicker);
            await importButton.click({ force: true });
            await page.waitForTimeout(300);
            page.off("filechooser", unexpectedPicker);
            assert.equal(blockedChooser, 0, "A running timer must not open the restore picker");
            // Age the durable fixture as if the browser had been closed for a month.
            // Keep the rendering/event clock real: faking it also changes Compose
            // pointer/animation scheduling, which is not the timer's source of truth.
            await page.evaluate(async () => {
                const record = JSON.parse(await evTimerStorage.read());
                const state = JSON.parse(record.payload);
                for (const segment of state.activeSession.segments) {
                    segment.startedAt -= 30 * 24 * 3_600_000;
                }
                await evTimerStorage.write(record.revision, JSON.stringify(state));
            });
            await page.evaluate(() => window.dispatchEvent(new Event("pageshow")));
            const completed = await waitForRecord(page, record => {
                const state = JSON.parse(record?.payload || "{}");
                return state.activeSession === null && state.backup?.sessions.length === 1;
            });
            assert.notEqual(completed, null, "Resume must complete an elapsed timer");
            const history = JSON.parse(completed.payload).backup.sessions[0];
            assert.equal(history.endPercent, active.targetPercent);
            assert.ok(history.endedAt < active.segments[0].startedAt,
                "History must use the calculated target time, not the page resume time");
            await waitForAccessibleText(page, "Charge summary");
            await waitForAccessibleText(page, "60% → 100%");
            await clickCanvasButton(page, "Done");
            await clickCanvasButton(page, "Settings");
            await page.getByRole("button", { name: "Back", exact: true }).waitFor();
            await page.waitForTimeout(300);
            await capture(page, `storage-${colorScheme}`, storageLabels);

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
            await waitForAccessibleText(page,
                "Download requested. Check your browser downloads to confirm the file was saved.");
            const imported = JSON.stringify({
                ...exported,
                presets: [{ ...exported.presets[0], name: "Imported car" }],
                preferences: { ...exported.preferences, notifyAt80: false, startPercent: 21.5 },
                sessions: []
            }).replace('"sessions":[]', '"sessions":[{"id":9007199254740993,"batteryCapacity":83,' +
                '"startPercent":20,"targetPercent":80,"efficiency":0.9,"endedAt":300,' +
                '"actualPercent":78.5,"calibrationEnabled":true,' +
                '"segments":[{"startedAt":100,"powerKw":11,"efficiency":0.85}]}]');
            const beforeImport = await waitForRecord(page);
            const [chooser] = await Promise.all([
                page.waitForEvent("filechooser"),
                clickCanvasButton(page, "Import backup")
            ]);
            await chooser.setFiles({
                name: "backup.json", mimeType: "application/json", buffer: Buffer.from(imported)
            });
            await page.getByRole("button", { name: "Replace data", exact: true }).waitFor();
            // Merely reading the file must not mutate persisted data.
            assert.equal(await page.evaluate(async () => JSON.parse(await evTimerStorage.read()).revision), beforeImport.revision);
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
            console.log(`Backup/recovery ${colorScheme} passed`);
            await context.close();
        }
        for (const [device, viewport] of [
            ["phone", { width: 390, height: 844 }],
            ["desktop", { width: 1100, height: 900 }]
        ]) {
          for (const colorScheme of ["light", "dark"]) {
            const evidence = `${device}-${colorScheme}`;
            const context = await browser.newContext({ locale: "en-US", viewport, colorScheme });
            const page = await context.newPage();
            const errors = [];
            page.on("pageerror", error => errors.push(error.message));
            await page.goto(origin);
            assert.notEqual(await waitForRecord(page), null);
            await page.waitForTimeout(3000);
            await capture(page, `startup-${evidence}`, configurationLabels);
            await capture(page, `configuration-${evidence}`, configurationLabels);
            await clickCanvasButton(page, "Settings");
            await capture(page, `storage-${evidence}`, storageLabels);
            await clickCanvasButton(page, "Back");
            await clickCanvasButton(page, "Start Timer");
            assert.notEqual(await waitForRecord(page, record =>
                JSON.parse(record?.payload || "{}").activeSession != null), null);
            // End a persisted one-hour charge, using real event/rendering time.
            await page.evaluate(async () => {
                const record = JSON.parse(await evTimerStorage.read());
                const state = JSON.parse(record.payload);
                for (const segment of state.activeSession.segments) segment.startedAt -= 3_600_000;
                await evTimerStorage.write(record.revision, JSON.stringify(state));
                window.dispatchEvent(new Event("pageshow"));
            });
            await waitForAccessibleText(page, "Charging in Progress");
            await capture(page, `running-${evidence}`, runningLabels);
            // Stop is below the fold on a phone; scroll the actual Compose canvas.
            await page.mouse.move(viewport.width / 2, viewport.height - 100);
            await page.mouse.wheel(0, 900);
            await page.waitForTimeout(500);
            if (device === "phone") await capture(page, `running-controls-${evidence}`, runningLabels);
            await clickCanvasButton(page, "Stop Timer");
            const ended = await waitForRecord(page, record => {
                const state = JSON.parse(record?.payload || "{}");
                return state.activeSession === null && state.backup?.sessions.length === 1;
            });
            assert.notEqual(ended, null);
            const saved = JSON.parse(ended.payload).backup.sessions[0];
            assert.ok(saved.endedAt - saved.segments[0].startedAt >= 3_600_000);
            assert.ok(saved.endPercent > 60 && saved.endPercent < 100);
            const endText = new Intl.NumberFormat("en-US", { maximumFractionDigits: 1 }).format(saved.endPercent);
            const energy = new Intl.NumberFormat("en-US", { maximumFractionDigits: 1 })
                .format(saved.batteryCapacity * (saved.endPercent - saved.startPercent) / 100);
            const verifySummary = async () => {
                await waitForAccessibleText(page, "Charge summary");
                await waitForAccessibleText(page, `60% → ${endText}%`);
                await waitForAccessibleText(page, `${energy} kWh`);
                await waitForAccessibleText(page, "Elapsed Time");
                await waitForAccessibleText(page, "1h");
                await waitForAccessibleText(page, "Average power");
                await waitForAccessibleText(page, "11 kW");
            };
            await verifySummary();
            await capture(page, `summary-${evidence}`, summaryLabels);
            await page.reload();
            await verifySummary();
            await clickCanvasButton(page, "Done");
            await clickCanvasButton(page, "Settings");
            await clickCanvasText(page, "Charging history");
            await waitForAccessibleText(page, `60% → ${endText}%`);
            await waitForAccessibleText(page, `${energy} kWh`);
            await capture(page, `history-${evidence}`, ["Charging history", "Default", `60% → ${endText}%`,
                `${energy} kWh`, "1h 0m", "avg 11 kW"]);
            await page.reload();
            await waitForAccessibleText(page, `60% → ${endText}%`);
            await waitForAccessibleText(page, `${energy} kWh`);
            const card = page.getByRole("button").filter({ hasText: `60% → ${endText}%` }).first();
            await clickCanvas(page, card);
            await verifySummary();
            await clickCanvasButton(page, "Done");
            await waitForAccessibleText(page, `${energy} kWh`);
            assert.deepEqual(JSON.parse((await waitForRecord(page)).payload).backup.sessions[0],
                { ...saved, reviewed: true }, "History display and reload must preserve saved values");
            assert.deepEqual(errors, []);
            console.log(`Timer/history ${evidence} passed`);
            await context.close();
          }
        }
        // Exercise the same Settings hierarchy and screen callbacks as native.
        const parityContext = await browser.newContext({ locale: "en-US", viewport: { width: 1100, height: 900 } });
        await parityContext.addInitScript(() => {
            globalThis.sharedTexts = [];
            globalThis.shareFailure = false;
            Object.defineProperty(navigator, "share", { configurable: true, value: undefined });
            Object.defineProperty(navigator, "clipboard", { configurable: true,
                value: { writeText: async text => {
                    if (shareFailure) throw new Error("Clipboard permission denied");
                    sharedTexts.push(text);
                } } });
        });
        const parityPage = await parityContext.newPage();
        const parityErrors = [];
        parityPage.on("pageerror", error => {
            parityErrors.push(error.message);
            console.error("Parity page error:", error.stack);
        });
        await parityPage.goto(origin);
        assert.notEqual(await waitForRecord(parityPage), null);
        await parityPage.evaluate(async () => {
            const record = JSON.parse(await evTimerStorage.read());
            const state = JSON.parse(record.payload);
            state.backup.presets.push({ ...state.backup.presets[0], id: "home", name: "Home", position: 1 });
            const end = Date.now() - 3_600_000;
            state.backup.sessions = [{
                id: 42, batteryCapacity: 83, startPercent: 20, targetPercent: 100, efficiency: 1,
                presetId: "custom", presetName: "Default", presetEdited: false,
                segments: [{ startedAt: end - 3_600_000, powerKw: 11, efficiency: 1 }],
                endedAt: end, endPercent: 33.25, actualPercent: null, reviewed: true,
                interrupted: false, calibrationEnabled: false
            }];
            await evTimerStorage.write(record.revision, JSON.stringify(state));
            window.dispatchEvent(new Event("pageshow"));
        });
        await clickCanvasButton(parityPage, "Settings");
        console.log("Parity: opening settings/presets");
        await clickCanvasText(parityPage, "Presets");
        await waitForAccessibleText(parityPage, "Tap a name to rename");
        assert.equal(new URL(parityPage.url()).hash, "#/presets");
        await parityPage.goBack();
        await waitForAccessibleText(parityPage, "About");
        await parityPage.goForward();
        await waitForAccessibleText(parityPage, "Tap a name to rename");
        await clickCanvasText(parityPage, "Home");
        const nameInput = parityPage.getByRole("textbox").first();
        await clickCanvas(parityPage, nameInput);
        await parityPage.keyboard.press("Control+a");
        await parityPage.keyboard.type("Work", { delay: 50 });
        await clickCanvasButton(parityPage, "Save");
        const renamed = await waitForRecord(parityPage, record => record?.payload.includes('"name":"Work"'));
        assert.notEqual(renamed, null);
        await parityPage.screenshot({ path: path.join(screenshots, "rename-after-save.png") });
        await parityPage.getByRole("button", { name: "Back", exact: true }).waitFor({ timeout: 10_000 });
        assert.equal(await parityPage.getByRole("button", { name: "Save", exact: true }).count(), 0);
        await clickCanvasButton(parityPage, "Back");
        await clickCanvasText(parityPage, "Charging Power");
        await clickCanvasButton(parityPage, "Add Custom Power");
        await clickCanvas(parityPage, parityPage.getByRole("textbox").first());
        await parityPage.keyboard.type("8.5", { delay: 50 });
        await clickCanvasButton(parityPage, "Add");
        await parityPage.getByRole("button", { name: "Back", exact: true }).waitFor({ timeout: 10_000 });
        assert.notEqual(await waitForRecord(parityPage, record =>
            JSON.parse(record?.payload || "{}").backup?.preferences.draft?.chargingPower === 8.5), null);
        await clickCanvasButton(parityPage, "Remove 8.5 kW");
        assert.notEqual(await waitForRecord(parityPage, record =>
            Math.abs(JSON.parse(record?.payload || "{}").backup?.preferences.draft?.chargingPower - 3.6) < 0.00001), null);
        await clickCanvasButton(parityPage, "Back");
        await clickCanvasText(parityPage, "Charging history");
        console.log("Parity: opening saved summary and recording reading");
        await waitForAccessibleText(parityPage, "20% → 33.3%");
        const parityCard = parityPage.getByRole("button").filter({ hasText: "20% → 33.3%" }).first();
        await clickCanvas(parityPage, parityCard);
        await clickCanvasButton(parityPage, "Add car reading");
        await clickCanvas(parityPage, parityPage.getByRole("textbox").first());
        await parityPage.keyboard.type("32", { delay: 50 });
        await clickCanvasText(parityPage, "Read when timer stopped");
        await clickCanvasButton(parityPage, "Save");
        assert.notEqual(await waitForRecord(parityPage, record => {
            const saved = JSON.parse(record?.payload || "{}").backup?.sessions[0];
            return saved?.actualPercent === 32 && saved?.calibrationEnabled === true;
        }), null);
        await clickCanvasButton(parityPage, "Share");
        console.log("Parity: sharing");
        await parityPage.waitForFunction(() => sharedTexts.length === 1);
        assert.match((await parityPage.evaluate(() => sharedTexts[0])), /32/);
        await parityPage.evaluate(() => { shareFailure = true; });
        await clickCanvasButton(parityPage, "Share");
        await waitForAccessibleText(parityPage, "Could not share summary");
        await parityPage.reload();
        await waitForAccessibleText(parityPage, "20% → 32%");
        await clickCanvasButton(parityPage, "Done");
        await waitForAccessibleText(parityPage, "Charging history");
        await clickCanvasButton(parityPage, "Back");
        await clickCanvasText(parityPage, "Notify at 80%");
        assert.notEqual(await waitForRecord(parityPage, record =>
            JSON.parse(record?.payload || "{}").backup?.preferences.notifyAt80 === false), null);
        await clickCanvasText(parityPage, "Presets");
        console.log("Parity: checking calibration reset");
        await clickCanvasButton(parityPage, "Estimate information for Default");
        await waitForAccessibleText(parityPage, "1 reading");
        await clickCanvas(parityPage, parityPage.getByRole("button", { name: "Reset", exact: true }).nth(2));
        await waitForAccessibleText(parityPage, "Reset Default at 11 kW?");
        await clickCanvasButton(parityPage, "Reset");
        assert.notEqual(await waitForRecord(parityPage, record => {
            const saved = JSON.parse(record?.payload || "{}").backup?.sessions[0];
            return saved?.actualPercent === 32 && saved?.calibrationEnabled === false;
        }), null);
        await clickCanvasButton(parityPage, "Close");
        await clickCanvasButton(parityPage, "Back");
        await clickCanvasText(parityPage, "Open-source licenses");
        await waitForAccessibleText(parityPage, "Open-source licenses");
        await parityPage.waitForTimeout(1000);
        await capture(parityPage, "licenses", ["Open-source licenses"]);
        await clickCanvasButton(parityPage, "Back");
        await clickCanvasButton(parityPage, "Back");
        await waitForAccessibleText(parityPage, "Start Timer");
        assert.deepEqual(parityErrors, []);
        console.log("Parity: hierarchy/readings/sharing/licenses passed");
        await parityContext.close();

        const notificationContext = await browser.newContext({
            locale: "en-US", viewport: { width: 1100, height: 800 }
        });
        await notificationContext.addInitScript(() => {
            globalThis.notificationTest = { permission: "denied", requests: 0, attempts: 0, fail: true, rejectPermission: false };
            globalThis.Notification = class {
                static get permission() { return notificationTest.permission; }
                static async requestPermission() {
                    notificationTest.requests++;
                    if (notificationTest.rejectPermission) throw new Error("Permission request failed");
                    return notificationTest.permission;
                }
                constructor() {
                    notificationTest.attempts++;
                    if (notificationTest.fail) throw new Error("Foreground notification constructor unavailable");
                }
            };
        });
        const notificationPage = await notificationContext.newPage();
        const notificationErrors = [];
        notificationPage.on("pageerror", error => notificationErrors.push(error.message));
        notificationPage.on("console", message => console.log(`[notifications] ${message.type()}: ${message.text()}`));
        await notificationPage.goto(origin);
        assert.notEqual(await waitForRecord(notificationPage), null);
        await notificationPage.getByRole("button", { name: "Start Timer", exact: true }).waitFor();
        await notificationPage.waitForTimeout(3000);
        assert.equal(await notificationPage.evaluate(() => notificationTest.requests), 0,
            "Startup must not request notification permission");
        await clickCanvasButton(notificationPage, "Settings");
        await clickCanvasButton(notificationPage, "Enable foreground notifications");
        await notificationPage.waitForFunction(() => notificationTest.requests === 1);
        await notificationPage.waitForTimeout(300);
        await capture(notificationPage, "notification-permission",
            ["Settings", "A notification could not be delivered or permission was denied"]);
        fs.writeFileSync(path.join(screenshots, "notification-semantics.txt"), await notificationPage.locator("body").ariaSnapshot());
        await waitForAccessibleText(notificationPage, "A notification could not be delivered or permission was denied");
        assert.equal(JSON.parse((await waitForRecord(notificationPage)).payload).notificationsEnabled, false);
        await notificationPage.evaluate(() => { notificationTest.rejectPermission = true; });
        await clickCanvasButton(notificationPage, "Enable foreground notifications");
        await notificationPage.waitForFunction(() => notificationTest.requests === 2);
        await waitForAccessibleText(notificationPage, "A notification could not be delivered or permission was denied");
        assert.equal(JSON.parse((await waitForRecord(notificationPage)).payload).notificationsEnabled, false);
        await notificationPage.evaluate(() => { notificationTest.rejectPermission = false; });
        await notificationPage.evaluate(() => { notificationTest.permission = "granted"; });
        await clickCanvasButton(notificationPage, "Enable foreground notifications");
        assert.notEqual(await waitForRecord(notificationPage, record =>
            JSON.parse(record?.payload || "{}").notificationsEnabled === true), null);
        await notificationPage.getByRole("button", { name: "Disable foreground notifications", exact: true }).waitFor();
        await clickCanvasButton(notificationPage, "Back");
        await clickCanvasButton(notificationPage, "Start Timer");
        assert.notEqual(await waitForRecord(notificationPage, record =>
            JSON.parse(record?.payload || "{}").activeSession != null), null);
        await notificationPage.evaluate(async () => {
            const record = JSON.parse(await evTimerStorage.read());
            const state = JSON.parse(record.payload);
            for (const segment of state.activeSession.segments) segment.startedAt -= 30 * 24 * 3_600_000;
            await evTimerStorage.write(record.revision, JSON.stringify(state));
            window.dispatchEvent(new Event("pageshow"));
        });
        assert.notEqual(await waitForRecord(notificationPage, record => {
            const state = JSON.parse(record?.payload || "{}");
            return state.activeSession === null && state.completionNotificationAttempted === true;
        }), null, "Completion and its notification claim must persist even when notification construction fails");
        await waitForAccessibleText(notificationPage, "A notification could not be delivered or permission was denied");
        assert.equal(await notificationPage.evaluate(() => notificationTest.requests), 3);
        assert.equal(await notificationPage.evaluate(() => notificationTest.attempts), 1);
        await capture(notificationPage, "notification-failure",
            [...summaryLabels, "A notification could not be delivered or permission was denied"]);
        await notificationPage.reload();
        await waitForAccessibleText(notificationPage, "Charge summary");
        assert.equal(await notificationPage.evaluate(() => notificationTest.requests), 0);
        assert.equal(await notificationPage.evaluate(() => notificationTest.attempts), 0,
            "Reload must not re-attempt an already-claimed notification");
        assert.deepEqual(notificationErrors, [], "Notification failures must be handled, not escape the Wasm boundary");
        await notificationContext.close();
        console.log("Real Chromium: shared timer UI, durable start/reload, epoch catch-up/completion, backup roundtrip, IndexedDB conflicts and light/dark screenshots passed");
        if (dialogFailure) throw dialogFailure;
    } finally {
        await browser.close();
        server.closeAllConnections();
        await new Promise(resolve => server.close(resolve));
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
