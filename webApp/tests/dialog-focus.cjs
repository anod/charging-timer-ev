const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { button, text, clickCanvas } = require("./canvas.cjs");

async function verifyDialogFocus(browser, origin, screenshots) {
    const results = [];
    for (const variant of ["emoji", "typed-blurred", "typed-focused"]) {
        const context = await browser.newContext({ locale: "en-US", viewport: { width: 1100, height: 900 } });
        const page = await context.newPage();
        const errors = [];
        page.on("pageerror", error => {
            errors.push(error.message);
            console.error("Dialog focus page error:", error.stack);
        });
        try {
            await page.goto(origin);
            await button(page, "Settings");
            await text(page, "Presets");
            await text(page, "Default");
            if (variant === "emoji") await button(page, "🏠");
            else {
                await clickCanvas(page, page.getByRole("textbox").first());
                await page.keyboard.press("Control+a");
                await page.keyboard.type("Focus test", { delay: 50 });
                if (variant === "typed-blurred") await page.keyboard.press("Tab");
            }
            await button(page, "Save");
            await page.screenshot({ path: path.join(screenshots, `rename-${variant}-after-save.png`) });
            let restored = true;
            try {
                await page.getByRole("button", { name: "Back", exact: true }).waitFor({ timeout: 10_000 });
                assert.equal(await page.getByRole("button", { name: "Save", exact: true }).count(), 0);
            } catch { restored = false; }
            const semantics = await page.locator("body").ariaSnapshot();
            fs.writeFileSync(path.join(screenshots, `dialog-${variant}.html`), await page.content());
            fs.writeFileSync(path.join(screenshots, `dialog-${variant}.txt`), semantics);
            const record = await page.evaluate(async () => await evTimerStorage.read());
            console.log(`Dialog focus ${variant}: restored=${restored}`, semantics, errors, record);
            results.push({ variant, restored, errors });
            if (restored) {
                const name = JSON.parse(JSON.parse(record).payload).backup.presets[0].name;
                for (const dismissal of ["cancel", "outside", "escape"]) {
                    await text(page, name);
                    await clickCanvas(page, page.getByRole("textbox").first());
                    await page.keyboard.type(" unsaved", { delay: 25 });
                    if (dismissal === "cancel") await button(page, "Cancel");
                    else if (dismissal === "escape") await page.keyboard.press("Escape");
                    else await page.mouse.click(8, 300);
                    await page.getByRole("button", { name: "Back", exact: true }).waitFor({ timeout: 10_000 });
                    assert.equal(await page.getByRole("button", { name: "Save", exact: true }).count(), 0,
                        `${dismissal} must restore parent semantics without an unrelated click`);
                }
            }
        } finally {
            await context.close();
        }
    }
    assert.ok(results.every(result => result.restored && result.errors.length === 0),
        "Every dismissed dialog must automatically restore parent semantics: " + JSON.stringify(results));
}

module.exports = { verifyDialogFocus };
