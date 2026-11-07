const { chromium } = require("playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const http = require("node:http");
const os = require("node:os");
const { prepare } = require("../pwa/prepare.cjs");

const root = path.resolve(process.argv[2] || "build/dist/wasmJs/productionExecutable");
const evidence = path.resolve(process.env.EV_THEME_EVIDENCE || "build/theme-evidence");
const before = process.env.EV_THEME_BEFORE_HTML && fs.readFileSync(process.env.EV_THEME_BEFORE_HTML);
const colors = { light: "#FBFDF8", dark: "#191C1A" };
const mime = { ".html": "text/html", ".js": "application/javascript", ".mjs": "application/javascript",
    ".wasm": "application/wasm", ".webmanifest": "application/manifest+json", ".png": "image/png" };

(async () => {
    fs.mkdirSync(evidence, { recursive: true });
    const baseline = before ? fs.mkdtempSync(path.join(os.tmpdir(), "ev-theme-baseline-")) : null;
    if (baseline) {
        fs.cpSync(root, baseline, { recursive: true });
        fs.writeFileSync(path.join(baseline, "index.html"), before);
        prepare(baseline);
    }
    let variant = "after";
    const server = http.createServer((request, response) => {
        const relative = decodeURIComponent(new URL(request.url, "http://localhost").pathname).slice(1);
        const serving = variant === "before" ? baseline : root;
        const file = path.resolve(serving, relative || "index.html");
        if (!file.startsWith(serving + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
            response.writeHead(404).end();
            return;
        }
        response.setHeader("Content-Type", mime[path.extname(file)] || "application/octet-stream");
        response.end(fs.readFileSync(file));
    });
    let browser;
    try {
        await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
        browser = await chromium.launch({ headless: true });
        const results = [];
        for (variant of before ? ["before", "after"] : ["after"]) {
            for (const viewport of [{ width: 390, height: 844 }, { width: 1100, height: 900 }]) {
                const context = await browser.newContext({ viewport, colorScheme: "light", locale: "en-US" });
                try {
                    const url = `http://127.0.0.1:${server.address().port}/`;
                    const device = viewport.width === 390 ? "phone-viewport" : "desktop";
                    const loading = await context.newPage();
                    await loading.route("**/*.js", route => route.abort());
                    await loading.goto(url);
                    for (const scheme of ["light", "dark"]) {
                        await loading.emulateMedia({ colorScheme: scheme });
                        const background = await loading.evaluate(() =>
                            getComputedStyle(document.documentElement).backgroundColor);
                        if (variant === "after") {
                            const rgb = colors[scheme].slice(1).match(/../g).map(hex => parseInt(hex, 16));
                            assert.equal(background, `rgb(${rgb.join(", ")})`,
                                "Page background must match before Wasm starts");
                        }
                        await loading.screenshot({ path: path.join(evidence,
                            `${variant}-${device}-${scheme}-before-wasm.png`) });
                    }
                    await loading.close();
                    const page = await context.newPage();
                    const errors = [];
                    page.on("pageerror", error => errors.push(error.message));
                    await page.goto(url);
                    await page.getByRole("button", { name: "Start Timer", exact: true }).waitFor();
                    await page.waitForFunction(() => globalThis.evTimerPwa?.status() === "ready");
                    for (const scheme of ["light", "dark", "light"]) {
                        await page.emulateMedia({ colorScheme: scheme });
                        await page.waitForTimeout(300);
                        assert.equal(await page.evaluate(() => evTimerPwa.status()), "ready",
                            "Theme evidence must not include fixture-induced offline errors");
                        const values = await page.evaluate(() => ({
                            themeColor: [...document.querySelectorAll('meta[name="theme-color"]')]
                                .find(meta => !meta.media || matchMedia(meta.media).matches)?.content,
                            backgrounds: [document.documentElement, document.body]
                                .map(element => getComputedStyle(element).backgroundColor)
                        }));
                        values.canvas = await page.locator("canvas").first().boundingBox();
                        if (variant === "after") {
                            assert.equal(values.themeColor, colors[scheme]);
                            const rgb = colors[scheme].slice(1).match(/../g).map(hex => parseInt(hex, 16));
                            assert.deepEqual(values.backgrounds, [1, 2].map(() => `rgb(${rgb.join(", ")})`));
                        } else {
                            assert.equal(values.themeColor, colors.light, "Reproduce dark-tag shadowing");
                        }
                        assert.deepEqual(values.canvas, { x: 0, y: 0, ...viewport },
                            "Canvas geometry must not regress");
                        await page.screenshot({ path: path.join(evidence, `${variant}-${device}-${scheme}.png`) });
                        results.push({ variant, viewport, scheme, ...values });
                    }
                    assert.deepEqual(errors, []);
                    if (variant === "after") {
                        const cdp = await context.newCDPSession(page);
                        const parsed = await cdp.send("Page.getAppManifest");
                        assert.deepEqual(parsed.errors, []);
                        const manifest = JSON.parse(parsed.data);
                        assert.equal(manifest.theme_color, colors.light);
                        assert.equal(manifest.color_scheme_dark.theme_color, colors.dark);
                    }
                } finally {
                    await context.close();
                }
            }
        }
        fs.writeFileSync(path.join(evidence, "theme-results.json"),
            JSON.stringify({ browser: browser.version(), androidSystemBarsVerified: false, results }, null, 2));
        console.log("Theme: matching meta/page colors, live light-dark-light switching, manifest and canvas geometry verified");
    } finally {
        if (browser) await browser.close();
        await new Promise(resolve => server.close(resolve));
        if (baseline) fs.rmSync(baseline, { recursive: true });
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
