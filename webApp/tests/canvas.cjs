const assert = require("node:assert/strict");

async function clickCanvas(page, locator) {
    const deadline = Date.now() + 30_000;
    while (Date.now() < deadline) {
        if (await locator.count() !== 1) {
            await page.waitForTimeout(100);
            continue;
        }
        const bounds = await locator.boundingBox();
        const viewport = page.viewportSize();
        if (bounds && (bounds.y < 0 || bounds.y + bounds.height > viewport.height)) {
            await page.mouse.move(viewport.width / 2, viewport.height / 2);
            await page.mouse.wheel(0, bounds.y < 0 ? bounds.y - 32 : bounds.y + bounds.height - viewport.height + 32);
            await page.waitForTimeout(100);
            continue;
        }
        if (!await locator.isVisible()) {
            await page.waitForTimeout(100);
            continue;
        }
        await page.waitForTimeout(500);
        await locator.evaluate(() => new Promise(resolve =>
            requestAnimationFrame(() => requestAnimationFrame(resolve))));
        const after = await locator.boundingBox();
        if (bounds && after && after.width > 0 && after.height > 0 &&
            ["x", "y", "width", "height"].every(key => Math.abs(bounds[key] - after[key]) < 0.5) &&
            await locator.isEnabled()) {
            await page.mouse.click(after.x + after.width / 2, after.y + after.height / 2);
            return;
        }
        await page.waitForTimeout(50);
    }
    console.error("Canvas control failure:", locator.toString(), page.url(), await page.locator("body").ariaSnapshot());
    assert.fail("Expected stable, enabled canvas control");
}

const button = (page, name) => clickCanvas(page, page.getByRole("button", { name, exact: true }));
const text = (page, name) => {
    const prefix = new RegExp("^" + name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "(?:\\s|$)");
    return clickCanvas(page, page.getByRole("button", { name: prefix })
        .or(page.getByRole("checkbox", { name: prefix }))
        .or(page.getByText(name, { exact: true })).first());
};

async function paintedDialogBaseline(page, scrimOnly = false) {
    const viewport = page.viewportSize();
    const clip = scrimOnly
        ? { x: 8, y: 300, width: 32, height: 32 }
        : { x: viewport.width / 2 - 64, y: viewport.height / 2 - 64, width: 128, height: 128 };
    await page.waitForTimeout(300);
    return { clip, image: await page.screenshot({ clip }) };
}

async function assertPaintedDialogDismissed(page, baseline) {
    const deadline = Date.now() + 10_000;
    while (Date.now() < deadline) {
        if ((await page.screenshot({ clip: baseline.clip })).equals(baseline.image)) return;
        await page.waitForTimeout(100);
    }
    assert.fail("Painted dialog must disappear without additional pointer or keyboard input");
}

module.exports = { clickCanvas, button, text, paintedDialogBaseline, assertPaintedDialogDismissed };
