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
        ? { x: 8, y: 8, width: 8, height: 8 }
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

async function assertTimePredictionLabels(page) {
    const ticks = page.getByText(/^\+\d+(?:h(?: \d+m)?|m) · \d+%$/, { exact: true });
    await ticks.first().waitFor();
    const texts = await ticks.allTextContents();
    assert.ok(texts.length >= 1 && texts.length <= 4, `At most four time labels: ${texts}`);
    assert.match(texts.at(-1), / · 100%$/, "The target label is retained");
    const boxes = [];
    for (const tick of await ticks.all()) {
        const text = await tick.textContent();
        const minutes = Number(text.match(/(\d+)m/)?.[1] || 0);
        assert.equal(minutes % 5, 0, `Duration is rounded to five minutes: ${text}`);
        const box = await tick.boundingBox();
        assert.ok(box);
        boxes.push(box);
    }
    boxes.sort((a, b) => a.x - b.x);
    for (let i = 1; i < boxes.length; i++) {
        assert.ok(Math.abs(boxes[i].y - boxes[0].y) < 1, "Time labels stay on one row");
        assert.ok(boxes[i].x >= boxes[i - 1].x + boxes[i - 1].width + 7,
            "Time labels do not overlap");
    }
}

module.exports = { clickCanvas, button, text, paintedDialogBaseline, assertPaintedDialogDismissed,
    assertTimePredictionLabels };
