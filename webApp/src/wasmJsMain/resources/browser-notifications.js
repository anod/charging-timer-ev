/* Foreground only: deliberately no service worker, push, or background scheduler. */
(function (root) {
    "use strict";
    function permission() {
        return root.isSecureContext && typeof root.Notification === "function"
            ? root.Notification.permission : "unsupported";
    }
    root.evTimerNotifications = {
        permission,
        requestPermission() {
            if (permission() === "unsupported") return Promise.resolve("unsupported");
            return root.Notification.requestPermission();
        },
        show(title, body, tag) {
            if (root.document.visibilityState !== "visible") {
                throw new Error("Notifications are only available while this page is visible");
            }
            if (permission() !== "granted") throw new Error("Notification permission is not granted");
            // Some browsers (including iOS) expose Notification but forbid its constructor.
            // Propagate that failure; never imply an alert has been scheduled.
            const notification = new root.Notification(title, { body, tag });
            notification.onerror = () => {
                root.dispatchEvent(new root.Event("ev-timer-notification-error"));
            };
        }
    };
})(globalThis);
