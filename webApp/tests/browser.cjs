const { chromium } = require("playwright");
const assert = require("node:assert/strict");
const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");
const { button: clickCanvasButton, text: clickCanvasText, clickCanvas,
    paintedDialogBaseline, assertPaintedDialogDismissed } = require("./canvas.cjs");
const { verifyDialogFocus } = require("./dialog-focus.cjs");

const root = path.resolve("build/dist/wasmJs/productionExecutable");
const screenshots = path.resolve("build/browser-evidence");
const mime = { ".wasm": "application/wasm", ".js": "application/javascript",
    ".mjs": "application/javascript", ".html": "text/html", ".json": "application/json" };

async function waitForRecord(page, accept = record => record !== null, timeout = 120_000) {
    const deadline = Date.now() + timeout;
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

async function waitForAccessibleTextAbsent(page, text) {
    const deadline = Date.now() + 10_000;
    while ((await page.locator("body").ariaSnapshot()).includes(text)) {
        assert.ok(Date.now() < deadline, `Accessible UI must not contain: ${text}`);
        await page.waitForTimeout(100);
    }
}

const configurationLabels = ["Charging Timer for EV", "Battery: 83 kWh", "Max: 100%",
    "Start", "Charging Power", "3.6 kW", "7.0 kW", "11.0 kW", "22.0 kW", "Start Timer"];
const runningLabels = ["Charging in Progress", "Charging Power", "Current %", "Target %",
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

async function verifyPowerUndo(browser, origin) {
    const context = await browser.newContext({ locale: "en-US", viewport: { width: 800, height: 480 } });
    const page = await context.newPage();
    const settings = record => {
        const { preferences, presets } = JSON.parse(record.payload).backup;
        return preferences.draft ?? presets.find(preset => preset.id === preferences.selectedPresetId);
    };
    const hasPower = (record, power) => settings(record).powerOptions.some(value => Math.abs(value - power) < 0.001);
    try {
        await page.goto(origin);
        assert.ok(await waitForRecord(page));
        await page.evaluate(async () => {
            const record = JSON.parse(await evTimerStorage.read());
            const state = JSON.parse(record.payload);
            state.backup.presets[0].powerOptions = [3.6, 7, 11, 15, 20, 25, 30];
            await evTimerStorage.write(record.revision, JSON.stringify(state));
        });
        await page.reload();
        await waitForAccessibleText(page, "Start Timer");
        await clickCanvasButton(page, "Settings");
        await clickCanvasText(page, "Charging Power");
        await clickCanvasButton(page, "Remove 3.6 kW");
        await waitForAccessibleText(page, "3.6 kW removed");
        const undo = page.getByRole("button", { name: "Undo", exact: true });
        const box = await undo.boundingBox();
        assert.ok(box && box.width >= 48 && box.height >= 48, "Undo has a 48px pointer target");
        console.log(`Undo pointer bounds: ${box.width} x ${box.height} CSS pixels`);
        await page.mouse.click(box.x + box.width / 2, box.y + box.height - 2);
        assert.ok(await waitForRecord(page, r => r && settings(r).chargingPower === 11 && hasPower(r, 3.6)),
            "Undo edge click restores the removed power without changing the selection");
        console.log("Undo bottom edge click restored power");

        await clickCanvasButton(page, "Remove 3.6 kW");
        await waitForAccessibleText(page, "3.6 kW removed");
        const topBox = await undo.boundingBox();
        assert.ok(topBox && topBox.width >= 48 && topBox.height >= 48, "Undo retains a 48px pointer target");
        await page.mouse.click(topBox.x + topBox.width / 2, topBox.y + 2);
        assert.ok(await waitForRecord(page, r => r && hasPower(r, 3.6), 8_000),
            "Undo top edge click restores power");
        console.log("Undo top edge click restored power");

        await clickCanvasButton(page, "Remove 3.6 kW");
        await waitForAccessibleText(page, "Undo");
        const lastRemove = page.getByRole("button", { name: "Remove 30.0 kW", exact: true });
        await page.mouse.move(400, 300);
        let lastBox;
        for (let i = 0; i < 10; i++) {
            lastBox = await lastRemove.boundingBox();
            const undoBox = await undo.boundingBox();
            if (lastBox && lastBox.width > 0 && lastBox.height > 0 && undoBox &&
                lastBox.y >= 0 && lastBox.y + lastBox.height < undoBox.y) break;
            await page.mouse.wheel(0, 1000);
            await page.waitForTimeout(100);
        }
        const undoBox = await undo.boundingBox();
        assert.ok(lastBox && lastBox.width > 0 && lastBox.height > 0 && undoBox &&
            lastBox.y >= 0 && lastBox.y + lastBox.height < undoBox.y,
            "Last row is fully visible above the snackbar");
        console.log("Last row pointer bounds:", lastBox, "Undo bounds:", undoBox);
        await page.screenshot({ path: path.join(screenshots, "power-undo-last-row.png") });
        await page.mouse.click(lastBox.x + lastBox.width / 2, lastBox.y + lastBox.height / 2);
        await waitForAccessibleText(page, "30.0 kW removed");
        const last = await waitForRecord(page, r => r && !hasPower(r, 30));
        assert.ok(last, "Last row remains pointer-accessible while snackbar is shown");
        for (let i = 0; i < 8; i++) {
            await page.keyboard.press("Tab");
        }
        await page.keyboard.press("Enter");
        assert.ok(await waitForRecord(page, r => r && hasPower(r, 30), 8_000),
            "Undo responds to keyboard activation");
        console.log("Last row pointer access while snackbar visible and keyboard Undo verified");
    } finally {
        await context.close();
    }
}

async function verifyTwoPaneSettingsBack(browser, origin) {
    const context = await browser.newContext({
        locale: "en-US", viewport: { width: 1200, height: 900 }
    });
    const page = await context.newPage();
    const errors = [];
    page.on("pageerror", error => errors.push(error.message));
    try {
        await page.goto(origin);
        assert.notEqual(await waitForRecord(page), null);
        await page.evaluate(async () => {
            const record = JSON.parse(await evTimerStorage.read());
            const state = JSON.parse(record.payload);
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
        assert.equal(new URL(page.url()).hash, "#/timer");
        await waitForAccessibleText(page, "Start Timer");

        await clickCanvasButton(page, "Settings");
        await clickCanvasText(page, "Presets");
        await waitForAccessibleText(page, "Use the edit button to rename. Drag the handle to reorder.");
        await clickCanvasButton(page, "Back");
        await page.waitForFunction(() => location.hash === "#/timer");
        await waitForAccessibleText(page, "Start Timer");
        assert.equal(new URL(page.url()).hash, "#/timer",
            "The Settings header Back exits the selected detail and Settings together");

        await clickCanvasButton(page, "Settings");
        await clickCanvasText(page, "Presets");
        await waitForAccessibleText(page, "Use the edit button to rename. Drag the handle to reorder.");
        await page.goBack();
        await page.waitForFunction(() => location.hash === "#/timer");
        await waitForAccessibleText(page, "Start Timer");
        assert.equal(new URL(page.url()).hash, "#/timer",
            "One browser Back exits two-pane Settings instead of stopping on its list");

        await clickCanvasButton(page, "Settings");
        await clickCanvasText(page, "Charging history");
        await waitForAccessibleText(page, "20% → 33.3%");
        await clickCanvas(page, page.getByRole("button").filter({ hasText: "20% → 33.3%" }).first());
        await waitForAccessibleText(page, "Charge summary");
        assert.equal(new URL(page.url()).hash, "#/summary/42");
        await page.goBack();
        await page.waitForFunction(() => location.hash === "#/history");
        await waitForAccessibleText(page, "Charging history");
        assert.equal(new URL(page.url()).hash, "#/history",
            "Back from a History summary returns to History before leaving Settings");
        await clickCanvasButton(page, "Back");
        await page.waitForFunction(() => location.hash === "#/timer");
        await waitForAccessibleText(page, "Start Timer");
        assert.equal(new URL(page.url()).hash, "#/timer");
        assert.deepEqual(errors, [], "Two-pane Settings Back must not raise page errors");
    } finally {
        await context.close();
    }
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
        if (process.env.TWO_PANE_SETTINGS_BACK_ONLY === "1") {
            await verifyTwoPaneSettingsBack(browser, origin);
            console.log("Two-pane Settings header/system Back and nested History Back passed");
            return;
        }
        await verifyTwoPaneSettingsBack(browser, origin);
        console.log("Two-pane Settings header/system Back and nested History Back passed");
        await verifyPowerUndo(browser, origin);
        if (process.env.POWER_UNDO_ONLY === "1") return;
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
        // Stable Compose paints progress without exposing a progressbar role.
        // No resource-dependent controls may be exposed before strings load.
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
            assert.equal(snapshot.trim(), "",
                "Before resources load, no text or controls may be exposed");
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
            // Backup controls are in the Settings list pane at this width.
            await page.mouse.move(250, 450);
            await page.mouse.wheel(0, 600);
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
            await page.mouse.move(250, 450);
            await page.mouse.wheel(0, 600);
            await page.waitForTimeout(300);
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
            if (device === "desktop") {
                assert.equal(await page.getByText("Choose a setting to view its details", { exact: true }).count(), 0,
                    "A completion summary must remain full-screen without a Settings entry");
                await capture(page, `summary-completion-wide-${colorScheme}`, summaryLabels);
            }
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
            if (device === "desktop") {
                await waitForAccessibleText(page, "Charging history");
                await capture(page, `summary-history-wide-${colorScheme}`,
                    ["Charging history", ...summaryLabels]);
            }
            await clickCanvasButton(page, "Done");
            await waitForAccessibleText(page, `${energy} kWh`);
            assert.deepEqual(JSON.parse((await waitForRecord(page)).payload).backup.sessions[0],
                { ...saved, reviewed: true }, "History display and reload must preserve saved values");
            assert.deepEqual(errors, []);
            console.log(`Timer/history ${evidence} passed`);
            await context.close();
          }
        }
        const settingsLayoutContext = await browser.newContext({
            locale: "en-US", viewport: { width: 500, height: 800 }
        });
        const settingsLayoutPage = await settingsLayoutContext.newPage();
        const settingsLayoutErrors = [];
        settingsLayoutPage.on("pageerror", error => settingsLayoutErrors.push(error.message));
        await settingsLayoutPage.goto(origin);
        assert.notEqual(await waitForRecord(settingsLayoutPage), null);
        await clickCanvasButton(settingsLayoutPage, "Settings");
        await waitForAccessibleText(settingsLayoutPage, "Settings");
        await clickCanvasText(settingsLayoutPage, "Presets");
        await waitForAccessibleText(settingsLayoutPage, "Use the edit button to rename. Drag the handle to reorder.");
        assert.equal(await settingsLayoutPage.getByRole("button", { name: "Back", exact: true }).count(), 1);
        await settingsLayoutPage.setViewportSize({ width: 1200, height: 900 });
        await waitForAccessibleText(settingsLayoutPage, "Settings");
        await waitForAccessibleText(settingsLayoutPage, "Use the edit button to rename. Drag the handle to reorder.");
        assert.equal(await settingsLayoutPage.getByText("Choose a setting to view its details", { exact: true }).count(), 0,
            "The selected detail replaces the empty-state placeholder");
        assert.equal(new URL(settingsLayoutPage.url()).hash, "#/presets");
        assert.equal(await settingsLayoutPage.getByRole("button", { name: "Back", exact: true }).count(), 1,
            "The wide layout keeps only the Settings-list back button, not a detail-pane back arrow");
        await settingsLayoutPage.setViewportSize({ width: 500, height: 800 });
        await waitForAccessibleTextAbsent(settingsLayoutPage, "Charging history");
        assert.equal(new URL(settingsLayoutPage.url()).hash, "#/presets");
        await settingsLayoutPage.goBack();
        await settingsLayoutPage.waitForFunction(() => location.hash === "#/settings");
        assert.equal(new URL(settingsLayoutPage.url()).hash, "#/settings",
            "Back after rendering compact Settings returns to Settings, not the timer");
        await waitForAccessibleText(settingsLayoutPage, "Charging history");
        await settingsLayoutPage.setViewportSize({ width: 1200, height: 900 });
        await waitForAccessibleText(settingsLayoutPage, "Choose a setting to view its details");
        assert.equal(new URL(settingsLayoutPage.url()).hash, "#/settings");
        assert.deepEqual(settingsLayoutErrors, []);
        await settingsLayoutContext.close();

        // Exercise the same Settings hierarchy and screen callbacks as native.
        const parityContext = await browser.newContext({ locale: "en-US", viewport: { width: 800, height: 900 } });
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
        await waitForAccessibleText(parityPage, "Use the edit button to rename. Drag the handle to reorder.");
        assert.equal(new URL(parityPage.url()).hash, "#/presets");
        await parityPage.goBack();
        await waitForAccessibleText(parityPage, "About");
        await parityPage.goForward();
        await waitForAccessibleText(parityPage, "Use the edit button to rename. Drag the handle to reorder.");
        const renameBaseline = await paintedDialogBaseline(parityPage);
        const backBounds = await parityPage.getByRole("button", { name: "Back", exact: true }).boundingBox();
        assert.ok(backBounds);
        await clickCanvasButton(parityPage, "Rename Home");
        const nameInput = parityPage.getByRole("textbox").first();
        await clickCanvas(parityPage, nameInput);
        await parityPage.keyboard.press("Control+a");
        await parityPage.keyboard.type("Work", { delay: 50 });
        await parityPage.screenshot({ path: path.join(screenshots, "rename-before-save.png") });
        fs.writeFileSync(path.join(screenshots, "rename-before-save.txt"),
            await parityPage.locator("body").ariaSnapshot());
        await clickCanvasButton(parityPage, "Save");
        await parityPage.screenshot({ path: path.join(screenshots, "rename-after-save.png") });
        fs.writeFileSync(path.join(screenshots, "rename-after-save.txt"),
            await parityPage.locator("body").ariaSnapshot());
        fs.writeFileSync(path.join(screenshots, "rename-after-save-record.json"),
            await parityPage.evaluate(async () => await evTimerStorage.read()));
        const renamed = await waitForRecord(parityPage, record => record?.payload.includes('"name":"Work"'));
        assert.notEqual(renamed, null, `Rename must persist Work; page errors: ${JSON.stringify(parityErrors)}`);
        await assertPaintedDialogDismissed(parityPage, renameBaseline);
        console.log("Rename accessibility after dismissal (accepted CMP-9368):",
            await parityPage.locator("body").ariaSnapshot());
        await parityPage.evaluate(() => {
            const originalRead = evTimerStorage.read;
            let release;
            const gate = new Promise(resolve => { release = resolve; });
            globalThis.reconcileGate = { entered: false, release };
            evTimerStorage.read = async function (...args) {
                evTimerStorage.read = originalRead;
                reconcileGate.entered = true;
                await gate;
                return originalRead.apply(this, args);
            };
            const replace = history.replaceState.bind(history);
            globalThis.routeWrites = [];
            history.replaceState = (...args) => {
                routeWrites.push(args[2]);
                return replace(...args);
            };
            window.dispatchEvent(new Event("pageshow"));
        });
        await parityPage.waitForFunction(() => reconcileGate.entered);
        await parityPage.mouse.click(backBounds.x + backBounds.width / 2, backBounds.y + backBounds.height / 2);
        await parityPage.waitForFunction(() => location.hash === "#/settings");
        await parityPage.evaluate(() => { routeWrites.length = 0; reconcileGate.release(); });
        await parityPage.waitForTimeout(500);
        assert.equal(new URL(parityPage.url()).hash, "#/settings");
        assert.ok(!(await parityPage.evaluate(() => routeWrites)).includes("#/presets"),
            "A reconciliation suspended across Back must never rewrite the previous route");
        // Reload starts the next independent workflow with a fresh semantics owner;
        // it is not counted as accessibility restoration after dismissal.
        await parityPage.reload();
        try {
            await waitForAccessibleText(parityPage, "Charging Power");
            assert.equal(new URL(parityPage.url()).hash, "#/settings");
        } finally {
            try {
                await parityPage.screenshot({ path: path.join(screenshots, "settings-after-rename-reload.png") });
                fs.writeFileSync(path.join(screenshots, "settings-after-rename-reload.json"),
                    JSON.stringify({ url: parityPage.url(), errors: parityErrors,
                        semantics: await parityPage.locator("body").ariaSnapshot() }, null, 2));
            } catch (error) {
                console.error("Settings reload evidence capture failed:", error.stack);
            }
        }
        // The About section follows browser backup controls and is below the
        // initial viewport on stable Compose. Verify it by actually scrolling.
        await parityPage.mouse.move(550, 400);
        await parityPage.mouse.wheel(0, 1200);
        await waitForAccessibleText(parityPage, "About");
        await parityPage.mouse.wheel(0, -2000);
        await waitForAccessibleText(parityPage, "Charging Power");
        await clickCanvasText(parityPage, "Presets");
        await waitForAccessibleText(parityPage, "Use the edit button to rename. Drag the handle to reorder.");
        await parityPage.evaluate(() => {
            const originalRead = evTimerStorage.read;
            let release;
            const gate = new Promise(resolve => { release = resolve; });
            globalThis.reconcileGate = { entered: false, release };
            evTimerStorage.read = async function (...args) {
                evTimerStorage.read = originalRead;
                reconcileGate.entered = true;
                await gate;
                return originalRead.apply(this, args);
            };
            globalThis.routeWrites = [];
            const replace = history.replaceState.bind(history);
            history.replaceState = (...args) => {
                routeWrites.push(args[2]);
                return replace(...args);
            };
        });
        // Wait for the real periodic reconciliation, then cross two browser
        // history events while its IndexedDB read remains suspended.
        await parityPage.waitForFunction(() => reconcileGate.entered);
        await parityPage.goBack();
        await parityPage.waitForFunction(() => location.hash === "#/settings");
        await parityPage.goBack();
        await parityPage.waitForFunction(() => location.hash === "#/timer");
        await parityPage.evaluate(() => { routeWrites.length = 0; reconcileGate.release(); });
        await parityPage.waitForTimeout(500);
        assert.equal(new URL(parityPage.url()).hash, "#/timer");
        const writesAfterDoubleBack = await parityPage.evaluate(() => routeWrites);
        assert.ok(!writesAfterDoubleBack.includes("#/settings") && !writesAfterDoubleBack.includes("#/presets"),
            "Queued Back operations must not replay superseded destinations");
        await parityPage.reload();
        await waitForAccessibleText(parityPage, "Start Timer");
        await clickCanvasButton(parityPage, "Settings");
        await clickCanvasText(parityPage, "Charging Power");
        const powerBaseline = await paintedDialogBaseline(parityPage, true);
        await parityPage.screenshot({ path: path.join(screenshots, "power-before-open.png") });
        await clickCanvasButton(parityPage, "Add Custom Power");
        await parityPage.screenshot({ path: path.join(screenshots, "power-open.png") });
        assert.ok(!(await parityPage.screenshot({ clip: powerBaseline.clip })).equals(powerBaseline.image),
            "Power dialog must paint a scrim in the comparison region");
        await clickCanvas(parityPage, parityPage.getByRole("textbox").first());
        await parityPage.keyboard.type("8.5", { delay: 50 });
        await clickCanvasButton(parityPage, "Add");
        assert.notEqual(await waitForRecord(parityPage, record =>
            JSON.parse(record?.payload || "{}").backup?.preferences.draft?.chargingPower === 8.5), null);
        await parityPage.screenshot({ path: path.join(screenshots, "power-after-add.png") });
        await assertPaintedDialogDismissed(parityPage, powerBaseline);
        await parityPage.reload();
        await waitForAccessibleText(parityPage, "8.5 kW");
        await clickCanvasButton(parityPage, "Remove 8.5 kW");
        assert.notEqual(await waitForRecord(parityPage, record =>
            Math.abs(JSON.parse(record?.payload || "{}").backup?.preferences.draft?.chargingPower - 3.6) < 0.00001), null);
        await clickCanvasButton(parityPage, "Back");
        await clickCanvasText(parityPage, "Charging history");
        console.log("Parity: opening saved summary and recording reading");
        await waitForAccessibleText(parityPage, "20% → 33.3%");
        const parityCard = parityPage.getByRole("button").filter({ hasText: "20% → 33.3%" }).first();
        await clickCanvas(parityPage, parityCard);
        await waitForAccessibleText(parityPage, "Car reading");
        await waitForAccessibleText(parityPage, "Not recorded");
        const readingRow = await parityPage.getByRole("button", { name: "Add car reading", exact: true }).boundingBox();
        assert.ok(readingRow && readingRow.height >= 48, "Car reading row has a 48px target");
        const readingBaseline = await paintedDialogBaseline(parityPage, true);
        await clickCanvasButton(parityPage, "Add car reading");
        assert.ok(!(await parityPage.screenshot({ clip: readingBaseline.clip })).equals(readingBaseline.image),
            "Reading dialog must paint a scrim in the comparison region");
        await clickCanvas(parityPage, parityPage.getByRole("textbox").first());
        await parityPage.keyboard.type("32", { delay: 50 });
        await clickCanvasText(parityPage, "Read when timer stopped");
        await clickCanvasButton(parityPage, "Save");
        assert.notEqual(await waitForRecord(parityPage, record => {
            const saved = JSON.parse(record?.payload || "{}").backup?.sessions[0];
            return saved?.actualPercent === 32 && saved?.calibrationEnabled === true;
        }), null);
        await parityPage.screenshot({ path: path.join(screenshots, "reading-after-save.png") });
        fs.writeFileSync(path.join(screenshots, "reading-after-save.txt"),
            await parityPage.locator("body").ariaSnapshot());
        await assertPaintedDialogDismissed(parityPage, readingBaseline);
        await parityPage.reload();
        await waitForAccessibleText(parityPage, "20% → 32%");
        await waitForAccessibleText(parityPage, "Car reading");
        await waitForAccessibleText(parityPage, "Edit car reading");
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
        const calibrationBaseline = await paintedDialogBaseline(parityPage, true);
        await clickCanvasButton(parityPage, "Estimate information for Default");
        await waitForAccessibleText(parityPage, "1 reading");
        const closeBounds = await parityPage.getByRole("button", { name: "Close", exact: true }).boundingBox();
        assert.ok(closeBounds);
        assert.ok(!(await parityPage.screenshot({ clip: calibrationBaseline.clip })).equals(calibrationBaseline.image),
            "Calibration dialog must paint a scrim in the comparison region");
        await clickCanvas(parityPage, parityPage.getByRole("button", { name: "Reset", exact: true }).nth(2));
        await waitForAccessibleText(parityPage, "Reset Default at 11 kW?");
        await clickCanvasButton(parityPage, "Reset");
        assert.notEqual(await waitForRecord(parityPage, record => {
            const saved = JSON.parse(record?.payload || "{}").backup?.sessions[0];
            return saved?.actualPercent === 32 && saved?.calibrationEnabled === false;
        }), null);
        await parityPage.screenshot({ path: path.join(screenshots, "calibration-after-reset.png") });
        await parityPage.mouse.click(closeBounds.x + closeBounds.width / 2, closeBounds.y + closeBounds.height / 2);
        await assertPaintedDialogDismissed(parityPage, calibrationBaseline);
        await parityPage.reload();
        await waitForAccessibleText(parityPage, "Use the edit button to rename. Drag the handle to reorder.");
        await clickCanvasButton(parityPage, "Back");
        await parityPage.mouse.move(550, 400);
        await parityPage.mouse.wheel(0, 1200);
        await waitForAccessibleText(parityPage, "Open-source licenses");
        await clickCanvasButton(parityPage, "Open-source licenses");
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
