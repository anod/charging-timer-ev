(() => {
    let status = "loading";
    globalThis.evTimerPwa = { status: () => status };
    const publish = value => {
        status = value;
        window.dispatchEvent(new Event("ev-timer-pwa-status"));
    };
    if (!isSecureContext || !("serviceWorker" in navigator)) {
        publish("unsupported");
        return;
    }
    const base = new URL("./", document.currentScript.src);
    let refresh = performance.getEntriesByType("navigation")[0]?.type === "reload";
    const reloadKey = `ev-timer-refresh:${base.pathname}`;
    const entryKey = `ev-timer-entry:${base.pathname}`;
    let internalReload = false;
    let refreshFlow = false;
    try {
        // Firefox can report a worker-served reload as "navigate". A reload
        // retains the history entry marked by the immediately departing page;
        // a new navigation or Back/Forward has a different entry/token.
        const departing = sessionStorage.getItem(entryKey);
        refresh ||= departing !== null && history.state?.[entryKey] === departing;
        sessionStorage.removeItem(entryKey);
        internalReload = sessionStorage.getItem(reloadKey) === "1";
        sessionStorage.removeItem(reloadKey);
    } catch (error) {
        console.error("PWA refresh guard unavailable", error);
        // Without a durable guard do not attempt an automatic reload.
        internalReload = true;
    }
    window.addEventListener("pagehide", () => {
        try {
            if (history.state !== null && (typeof history.state !== "object" || Array.isArray(history.state))) {
                throw new Error("PWA refresh marker requires an object history state");
            }
            const token = crypto.randomUUID();
            history.replaceState({ ...history.state, [entryKey]: token }, "");
            sessionStorage.setItem(entryKey, token);
        } catch (error) {
            console.error("PWA refresh marker unavailable", error);
        }
    });
    navigator.serviceWorker.addEventListener("message", event => {
        if (event.data?.type === "ev-timer-cache-error") {
            refreshFlow = false;
            publish("error");
        }
    });
    window.addEventListener("load", async () => {
        refreshFlow = refresh && !internalReload;
        try {
            const registration = await navigator.serviceWorker.register(new URL("sw.js", base), {
                scope: base.pathname, updateViaCache: "none"
            });
            const deadline = performance.now() + 15_000;
            let switching = false;
            const apply = async worker => {
                publish("update");
                if (!refreshFlow || switching || performance.now() > deadline) return;
                switching = true;
                const channel = new MessageChannel();
                let timer;
                let activated;
                try {
                    await new Promise((resolve, reject) => {
                        timer = setTimeout(() => reject(new Error("PWA activation timed out")), 15_000);
                        activated = () => {
                            if (worker.state === "activated") resolve();
                            else if (worker.state === "redundant") reject(new Error("PWA update discarded"));
                        };
                        worker.addEventListener("statechange", activated);
                        channel.port1.onmessage = () => activated();
                        worker.postMessage({ type: "ev-timer-activate" }, [channel.port2]);
                        activated();
                    });
                    sessionStorage.setItem(reloadKey, "1");
                    location.reload();
                } catch (error) {
                    console.error("PWA refresh activation failed", error);
                    publish("error");
                } finally {
                    clearTimeout(timer);
                    if (activated) worker.removeEventListener("statechange", activated);
                    channel.port1.close();
                    channel.port2.close();
                }
            };
            const watch = worker => {
                if (!worker) return;
                worker.addEventListener("statechange", () => {
                    if (worker.state === "installed") {
                        if (registration.active) void apply(worker);
                        else publish("ready");
                    } else if (worker.state === "redundant") {
                        refreshFlow = false;
                        publish("error");
                    }
                });
            };
            watch(registration.installing);
            registration.addEventListener("updatefound", () => watch(registration.installing));
            if (registration.waiting) void apply(registration.waiting);
            else if (registration.active) publish("ready");
            if (navigator.onLine) await registration.update();
            if (!registration.installing) refreshFlow = false;
        } catch (error) {
            refreshFlow = false;
            console.error("PWA registration/update failed", error);
            publish("error");
        }
    }, { once: true });
})();
