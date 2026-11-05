@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package info.anodsplace.evtimer.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import org.w3c.dom.HTMLElement

private class EditorEscapeInput : NavigationEventInput() {
    private var enabled = false

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        enabled = hasEnabledHandlers
    }

    fun back(): Boolean {
        if (!enabled) return false
        dispatchOnBackCompleted()
        return true
    }
}

// Compose routes canvas Escape through navigation events, but omits that step
// for its backing editor. Keep this repair at the web host boundary.
@JsFun("""(viewport, back) => {
    let composing = false;
    let tabbingFromEditor = false;
    let focusTask = null;
    const inEditor = event => event.composedPath().some(node =>
        node instanceof HTMLElement && node.classList.contains('compose-backing-field') &&
        (node instanceof HTMLInputElement || node instanceof HTMLTextAreaElement || node.isContentEditable) &&
        viewport.contains(node.getRootNode().host || node));
    const start = event => { if (inEditor(event)) composing = true; };
    const end = event => { if (inEditor(event)) composing = false; };
    const cancelRefocus = () => {
        tabbingFromEditor = false;
        if (focusTask !== null) clearTimeout(focusTask);
        focusTask = null;
    };
    const tab = event => {
        tabbingFromEditor = event.key === 'Tab' && inEditor(event);
    };
    const canvasIn = root => {
        for (const node of root.querySelectorAll('*')) {
            if (node instanceof HTMLCanvasElement && node.tabIndex >= 0) return node;
            if (node.shadowRoot) {
                const canvas = canvasIn(node.shadowRoot);
                if (canvas) return canvas;
            }
        }
        return null;
    };
    const focusout = event => {
        if (!tabbingFromEditor || event.relatedTarget !== null || !inEditor(event)) return;
        cancelRefocus();
        focusTask = setTimeout(() => {
            focusTask = null;
            if (!viewport.isConnected || document.visibilityState !== 'visible' || !document.hasFocus() ||
                (document.activeElement !== null && document.activeElement !== document.body)) return;
            const target = canvasIn(viewport) ||
                (viewport !== document.body && viewport.tabIndex >= 0 ? viewport : null);
            if (target) target.focus({ preventScroll: true });
        }, 0);
    };
    const keydown = event => {
        if (event.key !== 'Escape' || event.defaultPrevented || event.isComposing ||
            composing || event.keyCode === 229 || event.repeat || event.ctrlKey ||
            event.metaKey || event.altKey || !inEditor(event)) return;
        if (back()) event.preventDefault();
    };
    viewport.addEventListener('compositionstart', start);
    viewport.addEventListener('compositionend', end);
    viewport.addEventListener('keydown', keydown);
    viewport.addEventListener('keydown', tab, true);
    viewport.addEventListener('focusout', focusout);
    document.addEventListener('pointerdown', cancelRefocus, true);
    window.addEventListener('blur', cancelRefocus);
    return () => {
        cancelRefocus();
        viewport.removeEventListener('compositionstart', start);
        viewport.removeEventListener('compositionend', end);
        viewport.removeEventListener('keydown', keydown);
        viewport.removeEventListener('keydown', tab, true);
        viewport.removeEventListener('focusout', focusout);
        document.removeEventListener('pointerdown', cancelRefocus, true);
        window.removeEventListener('blur', cancelRefocus);
    };
}""")
private external fun installEditorEscape(viewport: HTMLElement, back: () -> Boolean): () -> Unit

@Composable
internal fun BrowserEscapeNavigation(viewport: HTMLElement) {
    val dispatcher = requireNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
    DisposableEffect(viewport, dispatcher) {
        val input = EditorEscapeInput()
        dispatcher.addInput(input)
        val dispose = installEditorEscape(viewport, input::back)
        onDispose {
            dispose()
            dispatcher.removeInput(input)
        }
    }
}
