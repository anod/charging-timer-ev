const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const resources = path.join(__dirname, "../src/wasmJsMain/resources");
const html = fs.readFileSync(path.join(resources, "index.html"), "utf8");
const manifest = JSON.parse(fs.readFileSync(path.join(resources, "manifest.webmanifest"), "utf8"));
const theme = fs.readFileSync(path.join(__dirname,
    "../../shared/src/commonMain/kotlin/info/anodsplace/evtimer/ui/Theme.kt"), "utf8");
const backgrounds = Object.fromEntries(["Light", "Dark"].map(scheme => {
    const block = theme.split(`internal val ${scheme}Colors =`)[1]?.split("\n)")[0];
    const hex = block?.match(/background = Color\(0xFF([0-9A-F]{6})\)/)?.[1];
    assert.ok(hex, `Shared ${scheme} background must be found`);
    return [scheme.toLowerCase(), `#${hex}`];
}));

test("every theme-color is conditional and matches the shared light/dark background", () => {
    const tags = [...html.matchAll(/<meta name="theme-color"[^>]*>/g)].map(match => match[0]);
    assert.equal(tags.length, 2);
    for (const [scheme, color] of Object.entries(backgrounds)) {
        const matching = tags.filter(tag => tag.includes(`media="(prefers-color-scheme: ${scheme})"`));
        assert.equal(matching.length, 1);
        assert.ok(matching[0].includes(`content="${color}"`));
    }
});

test("manifest preserves light defaults and declares draft dark overrides without changing identity", () => {
    assert.equal(manifest.theme_color, backgrounds.light);
    assert.equal(manifest.background_color, backgrounds.light);
    assert.deepEqual(manifest.color_scheme_dark, {
        theme_color: backgrounds.dark, background_color: backgrounds.dark
    });
    for (const member of ["id", "start_url", "scope"]) assert.equal(manifest[member], "./");
    assert.equal(manifest.display, "standalone");
});
