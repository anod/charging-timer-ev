const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { button, text, clickCanvas } = require("./canvas.cjs");

async function verifyDialogFocus(browser, origin, screenshots) {
    const results = [];
    for (const variant of ["emoji", "typed-blurred", "typed-focused"]) {
      for (const dismissal of ["save", "cancel", "outside", "escape"]) {
        const context = await browser.newContext({ locale: "en-US", viewport: { width: 1100, height: 900 } });
        const page = await context.newPage();
        const errors = [];
        const scenario = `${variant}-${dismissal}`;
        page.on("pageerror", error => {
            errors.push(error.message);
            console.error("Dialog focus page error:", error.stack);
        });
        try {
            await page.goto(origin);
            await button(page, "Settings");
            await text(page, "Presets");
            await text(page, "Default");
            if (variant === "emoji") {
                await clickCanvas(page, page.getByRole("checkbox", { name: "🏠", exact: true }));
            }
            else {
                await clickCanvas(page, page.getByRole("textbox").first());
                await page.keyboard.press("Control+a");
                await page.keyboard.type("Focus test", { delay: 50 });
                if (variant === "typed-blurred") await page.keyboard.press("Tab");
            }
            if (dismissal === "save") await button(page, "Save");
            else if (dismissal === "cancel") await button(page, "Cancel");
            else if (dismissal === "escape") await page.keyboard.press("Escape");
            else await page.mouse.click(8, 300);
            await page.getByRole("dialog").waitFor({ state: "hidden", timeout: 10_000 });
            await page.getByRole("button", { name: "Back", exact: true }).waitFor({ timeout: 10_000 });
            assert.equal(await page.getByRole("button", { name: "Save", exact: true }).count(), 0,
                `${scenario} must restore parent semantics without an unrelated click`);
            const record = await page.evaluate(async () => await evTimerStorage.read());
            const name = JSON.parse(JSON.parse(record).payload).backup.presets[0].name;
            assert.equal(name, dismissal === "save"
                ? (variant === "emoji" ? "Default🏠" : "Focus test") : "Default");
            results.push({ scenario, restored: true, errors });
        } catch (error) {
            console.error(`Dialog focus ${scenario} failed:`, error.stack, errors);
            results.push({ scenario, restored: false, failure: error.message, errors });
        } finally {
            try {
                const semantics = await page.locator("body").ariaSnapshot();
                console.log(`Dialog focus ${scenario}:`, semantics, errors);
                await page.screenshot({ path: path.join(screenshots, `rename-${scenario}-after-dismissal.png`) });
                fs.writeFileSync(path.join(screenshots, `dialog-${scenario}.html`), await page.content());
                fs.writeFileSync(path.join(screenshots, `dialog-${scenario}.txt`), semantics);
            } catch (error) {
                errors.push(`Evidence capture failed: ${error.message}`);
                console.error(`Dialog focus ${scenario} evidence failed:`, error.stack);
            } finally {
                try {
                    await context.close();
                } catch (error) {
                    errors.push(`Context cleanup failed: ${error.message}`);
                    console.error(`Dialog focus ${scenario} cleanup failed:`, error.stack);
                }
            }
        }
      }
    }
    fs.writeFileSync(path.join(screenshots, "dialog-results.json"), JSON.stringify(results, null, 2));
    assert.ok(results.every(result => result.restored && result.errors.length === 0),
        "Every dismissed dialog must automatically restore parent semantics: " + JSON.stringify(results));
}

module.exports = { verifyDialogFocus };
