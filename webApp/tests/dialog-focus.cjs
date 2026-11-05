const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { button, text, clickCanvas, paintedDialogBaseline, assertPaintedDialogDismissed } = require("./canvas.cjs");

async function verifyDialogFocus(browser, origin, screenshots) {
    const results = [];
    const scenarios = ["emoji", "typed-blurred", "typed-focused"].flatMap(variant =>
        ["save", "cancel", "outside", "escape"].map(dismissal => ({
            variant, dismissal, scenario: `${variant}-${dismissal}`
        })));
    for (let repeat = 1; repeat <= 5; repeat++) {
        scenarios.push({ variant: "typed-blurred", dismissal: "escape",
            scenario: `typed-blurred-escape-repeat-${repeat}` });
    }
    for (const { variant, dismissal, scenario } of scenarios) {
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
            await page.evaluate(() => {
                const describe = node => node ? {
                    tag: node.tagName || node.nodeName, classes: node.className,
                    role: node.getAttribute?.("role")
                } : null;
                const active = () => {
                    let node = document.activeElement;
                    while (node?.shadowRoot?.activeElement) node = node.shadowRoot.activeElement;
                    return node;
                };
                const focused = () => describe(active());
                globalThis.dialogEditorFocused = () => {
                    const node = active();
                    return !!node?.classList?.contains("compose-backing-field") &&
                        (node instanceof HTMLInputElement || node instanceof HTMLTextAreaElement || node.isContentEditable);
                };
                globalThis.dialogKeyboardEvidence = [];
                window.addEventListener("keydown", event => {
                    const record = { type: event.type, key: event.key, target: describe(event.target),
                        path: event.composedPath().map(describe), focused: focused(),
                        prevented: event.defaultPrevented };
                    dialogKeyboardEvidence.push(record);
                    setTimeout(() => {
                        record.preventedAfter = event.defaultPrevented;
                        record.focusedAfter = focused();
                        record.targetConnectedAfter = event.target?.isConnected;
                    }, 0);
                }, true);
                for (const type of ["focusin", "focusout"]) {
                    window.addEventListener(type, event => {
                        dialogKeyboardEvidence.push({ type, target: describe(event.target),
                            relatedTarget: describe(event.relatedTarget), focused: focused() });
                    }, true);
                }
            });
            const baseline = await paintedDialogBaseline(page);
            await text(page, "Default");
            assert.ok(!(await page.screenshot({ clip: baseline.clip })).equals(baseline.image),
                "Painted comparison region must detect the open dialog");
            if (variant === "emoji") {
                await button(page, "🏠");
            }
            else {
                await clickCanvas(page, page.getByRole("textbox").first());
                await page.keyboard.press("Control+a");
                await page.keyboard.type("Focus test", { delay: 50 });
                if (variant === "typed-blurred") {
                    assert.ok(await page.evaluate(() => dialogEditorFocused()),
                        "Typing must begin with a focused backing editor");
                    for (let tab = 0; tab < 8; tab++) {
                        await page.keyboard.press("Tab");
                        await page.waitForTimeout(100);
                        if (!(await page.evaluate(() => dialogEditorFocused()))) break;
                    }
                    assert.equal(await page.evaluate(() => dialogEditorFocused()), false,
                        "Real Tab navigation must exit backing editor focus before blurred dismissal");
                }
            }
            if (dismissal === "save") await button(page, "Save");
            else if (dismissal === "cancel") await button(page, "Cancel");
            else if (dismissal === "escape") {
                if (variant === "typed-focused") {
                    await page.evaluate(() => {
                        const findEditor = root => {
                            for (const node of root.querySelectorAll("*")) {
                                if (node.classList.contains("compose-backing-field") &&
                                    (node instanceof HTMLInputElement || node instanceof HTMLTextAreaElement || node.isContentEditable)) return node;
                                if (node.shadowRoot) {
                                    const found = findEditor(node.shadowRoot);
                                    if (found) return found;
                            }
                        };
                        const editor = findEditor(document);
                        if (!editor) throw new Error("Escape guard test requires a backing editor");
                        const key = options => new KeyboardEvent("keydown", {
                            key: "Escape", bubbles: true, composed: true, cancelable: true, ...options
                        });
                        editor.dispatchEvent(new CompositionEvent("compositionstart", { bubbles: true, composed: true }));
                        editor.dispatchEvent(key({ isComposing: true }));
                        editor.dispatchEvent(key({}));
                        editor.dispatchEvent(new CompositionEvent("compositionend", { bubbles: true, composed: true }));
                        const handled = key({});
                        handled.preventDefault();
                        editor.dispatchEvent(handled);
                        editor.dispatchEvent(key({ repeat: true }));
                        editor.dispatchEvent(key({ keyCode: 229 }));
                    });
                    await page.waitForTimeout(100);
                    assert.ok(!(await page.screenshot({ clip: baseline.clip })).equals(baseline.image),
                        "IME, handled and repeated Escape must leave the dialog painted");
                }
                await page.keyboard.press("Escape");
            }
            else await page.mouse.click(8, 300);
            await assertPaintedDialogDismissed(page, baseline);
            assert.equal(new URL(page.url()).hash, "#/presets",
                "Dialog dismissal must not also navigate away from Presets");
            const record = await page.evaluate(async () => await evTimerStorage.read());
            const name = JSON.parse(JSON.parse(record).payload).backup.presets[0].name;
            assert.equal(name, dismissal === "save"
                ? (variant === "emoji" ? "Default🏠" : "Focus test") : "Default");
            const restored = await page.getByRole("dialog").count() === 0 &&
                await page.getByRole("button", { name: "Back", exact: true }).count() === 1 &&
                await page.getByRole("button", { name: "Save", exact: true }).count() === 0;
            results.push({ scenario, passed: true, errors,
                accessibility: restored ? "restored" : "known limitation CMP-9368" });
            if (!restored) console.warn(`${scenario}: accepted CMP-9368; painted dismissal and persistence passed`);
        } catch (error) {
            console.error(`Dialog focus ${scenario} failed:`, error.stack, errors);
            results.push({ scenario, passed: false, failure: error.message, errors });
        } finally {
            try {
                const semantics = await page.locator("body").ariaSnapshot();
                console.log(`Dialog focus ${scenario}:`, semantics, errors);
                await page.screenshot({ path: path.join(screenshots, `rename-${scenario}-after-dismissal.png`) });
                fs.writeFileSync(path.join(screenshots, `dialog-${scenario}.html`), await page.content());
                fs.writeFileSync(path.join(screenshots, `dialog-${scenario}.txt`), semantics);
                fs.writeFileSync(path.join(screenshots, `dialog-${scenario}-keyboard.json`),
                    JSON.stringify(await page.evaluate(() => globalThis.dialogKeyboardEvidence || []), null, 2));
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
    assert.ok(results.every(result => result.passed && result.errors.length === 0),
        "Every dialog must dismiss visibly and preserve the expected data without pageerrors: " + JSON.stringify(results));
}

module.exports = { verifyDialogFocus };
