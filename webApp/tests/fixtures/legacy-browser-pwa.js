// Frozen pre-#96 adapter for upgrade compatibility tests.
(() => {
    let status = "loading";
    globalThis.evTimerPwa = { status: () => status };
    const publish = value => {
        status = value;
        window.dispatchEvent(new Event("ev-timer-pwa-status"));
    };
    const base = new URL("./", document.currentScript.src);
    navigator.serviceWorker.addEventListener("message", event => {
        if (event.data?.type === "ev-timer-cache-error") publish("error");
    });
    window.addEventListener("load", async () => {
        try {
            const registration = await navigator.serviceWorker.register(new URL("sw.js", base), {
                scope: base.pathname, updateViaCache: "none"
            });
            const watch = worker => {
                if (!worker) return;
                worker.addEventListener("statechange", () => {
                    if (worker.state === "installed") {
                        publish(registration.active ? "update" : "ready");
                    } else if (worker.state === "redundant") publish("error");
                });
            };
            watch(registration.installing);
            registration.addEventListener("updatefound", () => watch(registration.installing));
            if (registration.waiting) publish("update");
            else if (registration.active) publish("ready");
            if (navigator.onLine) await registration.update();
        } catch (error) {
            console.error("PWA registration/update failed", error);
            publish("error");
        }
    }, { once: true });
})();
