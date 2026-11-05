const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { button, text, clickCanvas } = require("./canvas.cjs");

async function verifyDialogFocus(browser, origin, screenshots) {
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
        await button(page, "🏠");
        await button(page, "Save");
        await page.screenshot({ path: path.join(screenshots, "rename-emoji-after-save.png") });
        console.log("Emoji rename semantics:", await page.locator("body").ariaSnapshot());
        await page.getByRole("button", { name: "Back", exact: true }).waitFor({ timeout: 10_000 });
        assert.equal(await page.getByRole("button", { name: "Save", exact: true }).count(), 0,
            "A dismissed rename dialog must not remain accessible");

        await text(page, "Default🏠");
        await clickCanvas(page, page.getByRole("textbox").first());
        await page.keyboard.press("Control+a");
        await page.keyboard.type("Focus test", { delay: 50 });
        await page.keyboard.press("Tab");
        await button(page, "Save");
        await page.screenshot({ path: path.join(screenshots, "rename-blurred-after-save.png") });
        console.log("Blurred rename semantics:", await page.locator("body").ariaSnapshot());
        await page.getByRole("button", { name: "Back", exact: true }).waitFor({ timeout: 10_000 });
        assert.equal(await page.getByRole("button", { name: "Save", exact: true }).count(), 0);
        assert.deepEqual(errors, []);
        console.log("Dialog focus: emoji-only and typed/blurred rename restore parent semantics");
    } catch (error) {
        fs.writeFileSync(path.join(screenshots, "dialog-focus-failure.html"), await page.content());
        fs.writeFileSync(path.join(screenshots, "dialog-focus-failure.txt"), await page.locator("body").ariaSnapshot());
        await page.screenshot({ path: path.join(screenshots, "dialog-focus-failure.png") });
        throw error;
    } finally {
        await context.close();
    }
}

module.exports = { verifyDialogFocus };
