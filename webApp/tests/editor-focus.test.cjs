const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

const source = fs.readFileSync("src/wasmJsMain/kotlin/info/anodsplace/evtimer/web/BrowserEscapeNavigation.kt", "utf8");
const script = source.match(/@JsFun\("""([\s\S]*?)"""\)/)[1];

function host() {
    class Element {
        constructor() {
            this.listeners = new Map();
            this.isConnected = true;
            this.classList = { contains: () => false };
        }
        addEventListener(type, callback) {
            const callbacks = this.listeners.get(type) || [];
            callbacks.push(callback);
            this.listeners.set(type, callbacks);
        }
        removeEventListener(type, callback) {
            this.listeners.set(type, (this.listeners.get(type) || []).filter(item => item !== callback));
        }
        emit(type, event) { for (const callback of this.listeners.get(type) || []) callback(event); }
        contains(node) { return node === editor || node === canvas; }
        getRootNode() { return document; }
        querySelectorAll() { return [canvas]; }
    }
    class Input extends Element {
        constructor() { super(); this.classList = { contains: name => name === "compose-backing-field" }; }
    }
    class Canvas extends Element {
        constructor() { super(); this.tabIndex = 0; this.focuses = []; }
        focus(options) { this.focuses.push(options); document.activeElement = this; }
    }
    const viewport = new Element();
    const editor = new Input();
    const canvas = new Canvas();
    const document = new Element();
    document.body = viewport;
    document.activeElement = viewport;
    document.visibilityState = "visible";
    let focused = true;
    document.hasFocus = () => focused;
    const window = new Element();
    const tasks = new Map();
    let sequence = 0;
    let backs = 0;
    const install = vm.runInNewContext(`(${script})`, {
        HTMLElement: Element, HTMLInputElement: Input, HTMLTextAreaElement: Input,
        HTMLCanvasElement: Canvas, document, window,
        setTimeout: callback => { tasks.set(++sequence, callback); return sequence; },
        clearTimeout: id => tasks.delete(id)
    });
    const dispose = install(viewport, () => { backs++; return true; });
    const event = (options = {}) => ({
        key: "Tab", relatedTarget: null, composedPath: () => [editor, viewport], ...options
    });
    const blur = (options = {}) => {
        viewport.emit("keydown", event());
        viewport.emit("focusout", event(options));
    };
    return { viewport, document, window, canvas, editor, dispose, blur, event,
        focused: value => { focused = value; }, backs: () => backs,
        flush: () => { for (const callback of tasks.values()) callback(); tasks.clear(); } };
}

test("Tab body-focus loss recovers canvas after the task without scrolling or dispatching Back", () => {
    const app = host();
    app.blur();
    assert.equal(app.canvas.focuses.length, 0);
    app.flush();
    assert.equal(app.document.activeElement, app.canvas);
    assert.equal(app.canvas.focuses[0].preventScroll, true);
    assert.equal(app.backs(), 0);
});

test("real focus targets, pointer input, hidden/unfocused pages and disposal prevent recovery", () => {
    for (const guard of [
        app => { app.document.activeElement = app.editor; },
        app => { app.document.visibilityState = "hidden"; },
        app => app.focused(false),
        app => { app.viewport.isConnected = false; },
        app => app.document.emit("pointerdown", {}),
        app => app.window.emit("blur", {}),
        app => app.dispose()
    ]) {
        const app = host();
        app.blur();
        guard(app);
        app.flush();
        assert.equal(app.canvas.focuses.length, 0);
    }
    const app = host();
    app.blur({ relatedTarget: app.canvas });
    app.flush();
    assert.equal(app.canvas.focuses.length, 0);
});

test("non-Tab focus loss and canvas/body Escape never invoke the editor fallback", () => {
    const app = host();
    app.viewport.emit("focusout", app.event());
    app.flush();
    assert.equal(app.canvas.focuses.length, 0);
    for (const node of [app.canvas, app.document.body]) {
        app.viewport.emit("keydown", app.event({ key: "Escape", composedPath: () => [node] }));
    }
    assert.equal(app.backs(), 0);
});
