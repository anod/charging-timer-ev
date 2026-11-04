const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const { marked } = require("marked");

const distribution = path.resolve(__dirname, "../build/dist/wasmJs/productionExecutable");
const site = path.resolve(__dirname, "../build/pages");
const source = fs.readFileSync(path.resolve(__dirname, "../../docs/privacy.md"), "utf8");
const markdown = source.replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/, "");

// Recreate only this generated site directory, never the source distribution.
fs.rmSync(site, { recursive: true, force: true });
fs.cpSync(distribution, site, { recursive: true });
for (const required of ["index.html", "sw.js", "manifest.webmanifest", "icons", "composeResources"]) {
    assert.ok(fs.existsSync(path.join(site, required)), `Missing Pages asset: ${required}`);
}
fs.writeFileSync(path.join(site, "privacy.html"),
    '<!doctype html>\n<html lang="en"><head><meta charset="utf-8">' +
    '<meta name="viewport" content="width=device-width, initial-scale=1">' +
    '<title>EV Charging Timer privacy policy</title></head><body><main>\n' +
    marked.parse(markdown) + "\n</main></body></html>\n");

const files = fs.readdirSync(distribution, { recursive: true })
    .filter(name => fs.statSync(path.join(distribution, name)).isFile()).sort();
for (const name of files) {
    assert.deepEqual(fs.readFileSync(path.join(site, name)),
        fs.readFileSync(path.join(distribution, name)), `Distribution changed: ${name}`);
}
assert.ok(files.some(name => name.endsWith(".wasm")), "Missing production Wasm");
console.log(`Pages site: ${files.length} unchanged distribution files + privacy.html at ${site}`);
