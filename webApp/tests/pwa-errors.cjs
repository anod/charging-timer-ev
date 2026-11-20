const assert = require("node:assert/strict");

function pageErrors(phase) {
    const errors = [];
    const resourceFailures = [];
    function observe(context, name) {
        const details = url => ({ context: name, phase: phase(), url, timestamp: Date.now() });
        context.on("weberror", event => {
            const entry = { ...details(event.page()?.url()), message: event.error().message,
                stack: event.error().stack };
            errors.push(entry);
        });
        context.on("requestfailed", request => {
            if (new URL(request.url()).pathname.endsWith(".cvr")) {
                resourceFailures.push({ ...details(request.url()), failure: request.failure()?.errorText });
            }
        });
        context.on("response", response => {
            if (!response.ok() && new URL(response.url()).pathname.endsWith(".cvr")) {
                resourceFailures.push({ ...details(response.url()), status: response.status() });
            }
        });
        return context;
    }
    return {
        observe,
        report: () => ({ errors, resourceFailures }),
        assertNoErrors: () => assert.equal(errors.length, 0,
            `Unexpected PWA page errors:\n${JSON.stringify({ errors, resourceFailures }, null, 2)}`)
    };
}

module.exports = { pageErrors };
