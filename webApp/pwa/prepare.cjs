const fs = require("node:fs");
const path = require("node:path");
const { createHash } = require("node:crypto");

const digest = bytes => createHash("sha256").update(bytes).digest("hex");

function prepare(root) {
    // Hash every emitted byte, not the app marketing version. Stable filenames
    // (including Wasm and Compose resources) must still invalidate the whole shell.
    const files = fs.readdirSync(root, { recursive: true })
        .filter(name => fs.statSync(path.join(root, name)).isFile() && name !== "sw.js" &&
            !/(^|[\\/])privacy(?:[./\\]|$)/i.test(name))
        .sort();
    const assets = Object.fromEntries(files.map(name => [
        name.split(path.sep).map(encodeURIComponent).join("/"),
        digest(fs.readFileSync(path.join(root, name)))
    ]));
    if (!assets["index.html"] || !files.some(name => name.endsWith(".wasm"))) {
        throw new Error("PWA preparation requires a complete production Wasm distribution");
    }
    const template = fs.readFileSync(path.join(__dirname, "worker.js"), "utf8");
    const legacyTemplate = fs.readFileSync(path.join(__dirname, "legacy-worker.js"), "utf8")
        .replace(/\r\n/g, "\n");
    const strings = fs.readFileSync(path.join(__dirname,
        "../src/commonMain/composeResources/values/strings.xml"), "utf8");
    const errorText = strings.match(/<string name="web_offline_shell_unavailable">([^<]+)<\/string>/)?.[1];
    if (!errorText) throw new Error("Missing offline shell failure string");
    const version = digest(JSON.stringify(assets) + template + errorText + legacyTemplate);
    fs.writeFileSync(path.join(root, "sw.js"),
        `const VERSION = ${JSON.stringify(version)};\nconst ASSETS = ${JSON.stringify(assets)};\n` +
        `const ERROR_TEXT = ${JSON.stringify(errorText)};\n` +
        `const LEGACY_TEMPLATE = ${JSON.stringify(legacyTemplate)};\n${template}`);
    return version;
}

if (require.main === module) console.log(`PWA shell ${prepare(path.resolve(process.argv[2]))}`);
module.exports = { prepare };
