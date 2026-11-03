const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const source = fs.readFileSync("src/wasmJsMain/resources/browser-notifications.js", "utf8");

function browser({ permission = "default", secure = true, visible = true, fail = false } = {}) {
    const instances = [];
    let requests = 0;
    class Notification {
        static permission = permission;
        static async requestPermission() { requests++; return permission; }
        constructor(title, options) {
            if (fail) throw new Error("Constructor unavailable");
            instances.push(this);
            this.title = title;
            this.options = options;
        }
    }
    const events = [];
    const context = vm.createContext({
        isSecureContext: secure, Notification,
        document: { visibilityState: visible ? "visible" : "hidden" },
        Event: class { constructor(type) { this.type = type; } },
        dispatchEvent: event => events.push(event.type)
    });
    vm.runInContext(source, context);
    return { api: context.evTimerNotifications, context, instances, events, requests: () => requests };
}

test("startup never requests permission; denial and insecure contexts are explicit", async () => {
    const denied = browser({ permission: "denied" });
    assert.equal(denied.requests(), 0);
    assert.equal(await denied.api.requestPermission(), "denied");
    assert.throws(() => denied.api.show("Target", "Body", "complete"), /not granted/);
    const insecure = browser({ secure: false });
    assert.equal(insecure.api.permission(), "unsupported");
    assert.equal(await insecure.api.requestPermission(), "unsupported");
    assert.equal(insecure.requests(), 0);
});

test("visible granted page delivers; hidden pages and constructor failures never succeed", () => {
    const granted = browser({ permission: "granted" });
    granted.api.show("Target", "Body", "complete");
    assert.equal(granted.instances.length, 1);
    assert.equal(granted.instances[0].title, "Target");
    granted.context.document.visibilityState = "hidden";
    assert.throws(() => granted.api.show("Target", "Body", "complete"), /visible/);
    assert.equal(granted.instances.length, 1);
    assert.throws(() => browser({ permission: "granted", fail: true }).api.show("Target", "Body", "complete"),
        /Constructor unavailable/);
});

test("asynchronous browser notification errors reach the UI error event", () => {
    const granted = browser({ permission: "granted" });
    granted.api.show("Target", "Body", "complete");
    granted.instances[0].onerror();
    assert.deepEqual(granted.events, ["ev-timer-notification-error"]);
});
