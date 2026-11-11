const { chromium } = require("playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const http = require("node:http");
const path = require("node:path");
const { button, clickCanvas } = require("./canvas.cjs");

const distribution = path.resolve("build/dist/wasmJs/productionExecutable");
const evidence = path.resolve("build/browser-evidence");
const mime = {
    ".wasm": "application/wasm",
    ".js": "application/javascript",
    ".mjs": "application/javascript",
    ".html": "text/html",
    ".json": "application/json"
};

async function waitForRecord(page, accept = () => true, timeout = 30_000) {
    const deadline = Date.now() + timeout;
    while (Date.now() < deadline) {
        const record = await page.evaluate(async () =>
            globalThis.evTimerStorage ? JSON.parse(await evTimerStorage.read()) : null);
        if (record !== null && accept(record)) return record;
        await page.waitForTimeout(100);
    }
    assert.fail("Browser storage did not reach the expected persisted state");
}

async function waitForText(page, value) {
    await page.getByText(value, { exact: true }).waitFor({ timeout: 15_000 });
}

async function fillCanvasTextField(page, field, value) {
    await revealCanvasControl(page, field);
    await clickCanvas(page, field);
    await page.keyboard.press("Control+a");
    await page.keyboard.type(value, { delay: 30 });
}

async function revealCanvasControl(page, control) {
    const viewport = page.viewportSize();
    for (let attempt = 0; attempt < 8; attempt++) {
        const bounds = await control.boundingBox();
        if (bounds && bounds.width > 0 && bounds.height > 0 &&
            bounds.y >= 0 && bounds.y + bounds.height <= viewport.height) return;
        const x = bounds
            ? Math.max(1, Math.min(viewport.width - 1, bounds.x + bounds.width / 2))
            : viewport.width / 2;
        await page.mouse.move(x, viewport.height / 2);
        await page.mouse.wheel(0, 500);
        await page.waitForTimeout(150);
    }
    assert.fail(`Canvas control could not be scrolled into view: ${control}`);
}

function payloadSettings(record) {
    const backup = JSON.parse(record.payload).backup;
    const preferences = backup.preferences;
    return preferences.draft ??
        backup.presets.find(preset => preset.id === preferences.selectedPresetId);
}

function selectedPreset(record) {
    const backup = JSON.parse(record.payload).backup;
    return backup.presets.find(preset => preset.id === backup.preferences.selectedPresetId);
}

async function setTextSetting(page, fieldName, value) {
    const field = page.getByRole("textbox", { name: fieldName, exact: true });
    await fillCanvasTextField(page, field, value);
}

async function captureLayoutEvidence(page, width, height, fileName) {
    await page.setViewportSize({ width, height });
    await page.reload();
    await waitForText(page, "Start Timer");

    const section = page.getByRole("button", { name: /^Expand Charging limit/ });
    await clickCanvas(page, section);
    await waitForText(page, "Cap starts at");
    await waitForText(page, "Cap ends at");
    await page.getByText(/Estimated time to target:/).waitFor();

    const startField = page.getByRole("textbox", { name: "Cap starts at input field, %", exact: true });
    const endField = page.getByRole("textbox", { name: "Cap ends at input field, %", exact: true });
    const powerField = page.getByRole("textbox", { name: "kilowatts", exact: true });
    const applyButton = page.getByRole("button", { name: "Apply", exact: true });
    const startBounds = await startField.boundingBox();
    const endBounds = await endField.boundingBox();
    const powerBounds = await powerField.boundingBox();
    const applyBounds = await applyButton.boundingBox();
    assert.ok(startBounds && endBounds && powerBounds && applyBounds,
        "Start/end controls and cap editor render at compact and wide widths");
    assert.ok(startBounds.y < endBounds.y && endBounds.y < powerBounds.y,
        "Start/end controls remain ordered before the power cap");
    const estimateBounds = await page.getByText(/Estimated time to target:/).boundingBox();
    assert.ok(estimateBounds, "The estimate remains visible with the controls");
    for (const bounds of [startBounds, endBounds, powerBounds, applyBounds, estimateBounds]) {
        assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= width,
            "Taper controls and estimate fit inside the compact or wide viewport");
    }
    assert.ok(
        Math.abs(powerBounds.y + powerBounds.height / 2 - applyBounds.y - applyBounds.height / 2) <= 1,
        "Apply is vertically centered on the power-cap field"
    );
    const screenshotPath = path.join(evidence, fileName);
    await page.screenshot({ path: screenshotPath });

    await fillCanvasTextField(page, powerField, "0");
    await waitForText(page, "Enter a power greater than 0.");
    const invalidPowerBounds = await powerField.boundingBox();
    const invalidApplyBounds = await applyButton.boundingBox();
    assert.ok(invalidPowerBounds && invalidApplyBounds);
    assert.ok(
        Math.abs(
            invalidPowerBounds.y + invalidPowerBounds.height / 2 -
            invalidApplyBounds.y - invalidApplyBounds.height / 2
        ) <= 1,
        "Validation text does not shift Apply away from the power-cap field"
    );
    return screenshotPath;
}

(async () => {
    fs.mkdirSync(evidence, { recursive: true });
    const server = http.createServer((request, response) => {
        const url = new URL(request.url, "http://localhost");
        const file = path.resolve(distribution, "." +
            decodeURIComponent(url.pathname === "/" ? "/index.html" : url.pathname));
        if (!file.startsWith(distribution + path.sep)) {
            response.writeHead(403).end();
            return;
        }
        fs.readFile(file, (error, bytes) => {
            if (error) {
                response.writeHead(404).end();
                return;
            }
            response.setHeader("Content-Type", mime[path.extname(file)] || "application/octet-stream");
            response.end(bytes);
        });
    });
    await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
    const browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({
        locale: "en-US",
        viewport: { width: 1100, height: 900 }
    });
    const page = await context.newPage();
    const pageErrors = [];
    const consoleErrors = [];
    page.on("pageerror", error => pageErrors.push(error.stack || error.message));
    page.on("console", message => {
        if (message.type() === "error") consoleErrors.push(message.text());
    });
    const reportPath = path.join(evidence, "taper-runtime-report.json");
    const report = { result: "running", pageErrors, consoleErrors };

    try {
        await page.goto(`http://127.0.0.1:${server.address().port}/`);
        await waitForRecord(page);
        await waitForText(page, "Start Timer");

        const layoutEvidence = [];
        layoutEvidence.push(await captureLayoutEvidence(page, 390, 1600, "taper-compact-layout.png"));
        layoutEvidence.push(await captureLayoutEvidence(page, 1000, 1600, "taper-wide-layout.png"));
        report.layoutEvidence = layoutEvidence;
        await page.setViewportSize({ width: 1100, height: 900 });
        await page.reload();
        await waitForText(page, "Start Timer");

        await clickCanvas(page, page.getByRole("button", { name: /^Expand Charging limit/ }));
        await waitForText(page, "Cap starts at");
        await setTextSetting(page, "Cap starts at input field, %", "70");
        await setTextSetting(page, "Cap ends at input field, %", "90");
        await setTextSetting(page, "kilowatts", "20");
        await revealCanvasControl(page, page.getByRole("button", { name: "Apply", exact: true }));
        await button(page, "Apply");
        await waitForRecord(page, record => {
            const settings = payloadSettings(record);
            return settings.taperStartPercent === 70 && settings.taperEndPercent === 90 &&
                settings.taperPowerCapKw === 20;
        });

        await button(page, "Add custom power");
        await waitForText(page, "Add Custom Power");
        const powerField = page.getByRole("textbox").last();
        await fillCanvasTextField(page, powerField, "150");
        const dcSuggestion = page.getByRole("button", { name: /^150(?:\.0)? kW$/ });
        await clickCanvas(page, dcSuggestion);
        await button(page, "Add");
        await waitForRecord(page, record => payloadSettings(record).powerOptions.includes(150));
        await page.reload();
        await waitForText(page, "Start Timer");
        const dcPowerChip = page.getByRole("button", { name: "150.0 kW", exact: true });
        await clickCanvas(page, dcPowerChip);
        await waitForRecord(page, record => payloadSettings(record).chargingPower === 150);

        await clickCanvas(page, page.getByRole("button", { name: /^Default \*$/ }));
        await button(page, "Update preset");
        const savedPresetRecord = await waitForRecord(page, record => {
            const preset = selectedPreset(record);
            return preset?.chargingPower === 150 && preset.taperStartPercent === 70 &&
                preset.taperEndPercent === 90 && preset.taperPowerCapKw === 20;
        });
        const savedPreset = selectedPreset(savedPresetRecord);
        assert.ok(savedPreset.powerOptions.includes(150));

        await page.reload();
        await waitForText(page, "Start Timer");
        const reloaded = await waitForRecord(page, record => {
            const preset = selectedPreset(record);
            return preset?.chargingPower === 150 && preset.taperStartPercent === 70 &&
                preset.taperEndPercent === 90 && preset.taperPowerCapKw === 20;
        });
        assert.deepEqual(selectedPreset(reloaded), savedPreset,
            "The updated preset and its taper settings survive reload");

        await button(page, "Start Timer");
        const activeRecord = await waitForRecord(page, record =>
            JSON.parse(record.payload).activeSession !== null);
        const active = JSON.parse(activeRecord.payload).activeSession;
        assert.equal(active.taperStartPercent, 70);
        assert.equal(active.taperEndPercent, 90);
        assert.equal(active.taperPowerCapKw, 20);
        assert.equal(active.segments[0].powerKw, 150);
        await page.reload();
        const resumed = await waitForRecord(page, record =>
            JSON.parse(record.payload).activeSession !== null);
        assert.deepEqual(JSON.parse(resumed.payload).activeSession, active,
            "Reload resumes the same DC session snapshot");
        await waitForText(page, "Charging in Progress");
        const targetTick = page.getByText(/^\+\d+m · 100%$/, { exact: true });
        await targetTick.waitFor();
        assert.equal(await targetTick.textContent(), "+55m · 100%",
            "DC endpoint planning uses the 70–90% 20 kW cap");

        await button(page, "Stop Timer");
        await waitForText(page, "Charge summary");
        const finished = await waitForRecord(page, record => {
            const state = JSON.parse(record.payload);
            return state.activeSession === null && state.backup.sessions.length === 1;
        });
        const history = JSON.parse(finished.payload).backup.sessions[0];
        assert.equal(history.taperStartPercent, 70);
        assert.equal(history.taperEndPercent, 90);
        assert.equal(history.taperPowerCapKw, 20);
        assert.equal(history.segments[0].powerKw, 150);

        await button(page, "Done");
        await button(page, "Settings");
        const exportButton = page.getByRole("button", { name: "Export backup", exact: true });
        await revealCanvasControl(page, exportButton);
        const downloadPromise = page.waitForEvent("download", { timeout: 10_000 }).catch(() => null);
        await button(page, "Export backup");
        await waitForText(page,
            "Download requested. Check your browser downloads to confirm the file was saved.");
        const download = await downloadPromise;
        assert.ok(download, "Backup export must trigger a browser download");
        assert.equal(await download.failure(), null);
        const backupBytes = fs.readFileSync(await download.path());
        const exported = JSON.parse(backupBytes.toString("utf8"));
        const exportedPreset = exported.presets.find(preset => preset.id === "custom");
        assert.equal(exportedPreset.taperStartPercent, 70);
        assert.equal(exportedPreset.taperEndPercent, 90);
        assert.equal(exportedPreset.taperPowerCapKw, 20);
        const exportedSession = exported.sessions[0];
        assert.equal(exportedSession.taperStartPercent, 70);
        assert.equal(exportedSession.taperEndPercent, 90);
        assert.equal(exportedSession.taperPowerCapKw, 20);
        assert.equal(exportedSession.segments[0].powerKw, 150);

        const importButton = page.getByRole("button", { name: "Import backup", exact: true });
        await revealCanvasControl(page, importButton);
        const [chooser] = await Promise.all([
            page.waitForEvent("filechooser"),
            button(page, "Import backup")
        ]);
        await chooser.setFiles({
            name: "taper-backup.json",
            mimeType: "application/json",
            buffer: backupBytes
        });
        await button(page, "Replace data");
        const restored = await waitForRecord(page, record => {
            const state = JSON.parse(record.payload);
            return state.backup.sessions.length === 1 &&
                state.backup.presets.some(preset => preset.id === "custom" &&
                    preset.taperStartPercent === 70 && preset.taperEndPercent === 90 &&
                    preset.taperPowerCapKw === 20);
        });
        const restoredState = JSON.parse(restored.payload);
        assert.equal(restoredState.backup.sessions[0].taperStartPercent, 70);
        assert.equal(restoredState.backup.sessions[0].taperEndPercent, 90);
        assert.equal(restoredState.backup.sessions[0].taperPowerCapKw, 20);
        assert.equal(restoredState.backup.sessions[0].segments[0].powerKw, 150);
        await page.reload();
        const restoredAfterReload = await waitForRecord(page, record =>
            JSON.parse(record.payload).backup.sessions.length === 1);
        assert.equal(JSON.parse(restoredAfterReload.payload).backup.presets
            .find(preset => preset.id === "custom").taperPowerCapKw, 20);
        assert.deepEqual(pageErrors, [], "Focused taper scenario must not raise page errors");
        report.result = "passed";
        report.assertions = [
            "UI edits save 70%/90%/20 kW into the selected preset",
            "150 kW DC preset reloads with the saved taper controls",
            "Running snapshot persists across reload and predicts +55m to 100%",
            "Finished session and preset taper values survive browser backup export/restore and reload"
        ];
        fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
        console.log("Focused taper browser scenario passed");
        console.log(`Report: ${reportPath}`);
        console.log(`Page errors: ${pageErrors.length}; console errors: ${consoleErrors.length}`);
        if (consoleErrors.length) console.log(JSON.stringify(consoleErrors, null, 2));
    } catch (error) {
        report.result = "failed";
        report.failure = error.stack || error.message;
        report.bodyText = await page.locator("body").innerText().catch(() => "");
        report.failureScreenshot = path.join(evidence, "taper-runtime-failure.png");
        await page.screenshot({ path: report.failureScreenshot }).catch(() => {});
        fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
        console.error(`Focused taper browser scenario failed. Report: ${reportPath}`);
        console.error(`Unexpected page errors: ${JSON.stringify(pageErrors)}`);
        console.error(`Console errors: ${JSON.stringify(consoleErrors)}`);
        throw error;
    } finally {
        await context.close();
        await browser.close();
        await new Promise(resolve => server.close(resolve));
    }
})().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
