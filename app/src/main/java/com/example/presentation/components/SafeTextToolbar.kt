package com.example.presentation.components

import android.graphics.Rect as AndroidRect
import android.os.Build
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import kotlin.math.roundToInt

/**
 * Lifecycle-aware [TextToolbar] bound to the current window's [View].
 * Prevents duplicate `ActionMode.finish()` calls and avoids starting or destroying a
 * `TYPE_FLOATING` ActionMode after the host Dialog/BottomSheet/Activity DecorView has begun detaching.
 */
class SafeAndroidTextToolbar(
    private val view: View
) : TextToolbar {

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    private var activeActionMode: ActionMode? = null
    private var isDetached: Boolean = !view.isAttachedToWindow

    private var rect: Rect = Rect.Zero
    private var onCopyRequested: (() -> Unit)? = null
    private var onPasteRequested: (() -> Unit)? = null
    private var onCutRequested: (() -> Unit)? = null
    private var onSelectAllRequested: (() -> Unit)? = null

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            isDetached = false
        }

        override fun onViewDetachedFromWindow(v: View) {
            isDetached = true
            activeActionMode = null
            status = TextToolbarStatus.Hidden
        }
    }

    init {
        view.addOnAttachStateChangeListener(attachListener)
    }

    fun release() {
        view.removeOnAttachStateChangeListener(attachListener)
        hide()
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        this.rect = rect
        this.onCopyRequested = onCopyRequested
        this.onPasteRequested = onPasteRequested
        this.onCutRequested = onCutRequested
        this.onSelectAllRequested = onSelectAllRequested

        if (isDetached || !view.isAttachedToWindow) {
            activeActionMode = null
            status = TextToolbarStatus.Hidden
            return
        }

        val current = activeActionMode
        if (current == null) {
            if (!view.hasWindowFocus()) {
                status = TextToolbarStatus.Hidden
                return
            }
            val started = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Api23ActionModeHelper.startFloatingActionMode(view, createLegacyCallback()) { mode, outRect ->
                        outRect.set(
                            this.rect.left.roundToInt(),
                            this.rect.top.roundToInt(),
                            this.rect.right.roundToInt(),
                            this.rect.bottom.roundToInt()
                        )
                    }
                } else {
                    view.startActionMode(createLegacyCallback())
                }
            }.getOrNull()
            if (started != null) {
                activeActionMode = started
                status = TextToolbarStatus.Shown
            } else {
                status = TextToolbarStatus.Hidden
            }
        } else {
            status = TextToolbarStatus.Shown
            runCatching { current.invalidate() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                runCatching { Api23ActionModeHelper.invalidateContentRect(current) }
            }
        }
    }

    override fun hide() {
        val current = activeActionMode
        activeActionMode = null
        status = TextToolbarStatus.Hidden
        if (current != null && !isDetached && view.isAttachedToWindow) {
            runCatching { current.finish() }
        }
    }

    private fun createLegacyCallback(): ActionMode.Callback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            populateMenu(menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            if (mode !== activeActionMode) return false
            populateMenu(menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                MENU_COPY -> onCopyRequested?.invoke()
                MENU_PASTE -> onPasteRequested?.invoke()
                MENU_CUT -> onCutRequested?.invoke()
                MENU_SELECT_ALL -> onSelectAllRequested?.invoke()
                else -> return false
            }
            hide()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            if (activeActionMode === mode) {
                activeActionMode = null
                status = TextToolbarStatus.Hidden
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private object Api23ActionModeHelper {
        fun startFloatingActionMode(
            view: View,
            delegate: ActionMode.Callback,
            onGetRect: (ActionMode, AndroidRect) -> Unit
        ): ActionMode? {
            val callback2 = object : ActionMode.Callback2() {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean =
                    delegate.onCreateActionMode(mode, menu)

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean =
                    delegate.onPrepareActionMode(mode, menu)

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
                    delegate.onActionItemClicked(mode, item)

                override fun onDestroyActionMode(mode: ActionMode) =
                    delegate.onDestroyActionMode(mode)

                override fun onGetContentRect(mode: ActionMode, view: View, outRect: AndroidRect) =
                    onGetRect(mode, outRect)
            }
            return view.startActionMode(callback2, ActionMode.TYPE_FLOATING)
        }

        fun invalidateContentRect(mode: ActionMode) {
            mode.invalidateContentRect()
        }
    }

    private fun populateMenu(menu: Menu) {
        menu.clear()
        var order = 0
        if (onCopyRequested != null) {
            menu.add(Menu.NONE, MENU_COPY, order++, android.R.string.copy)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        if (onPasteRequested != null) {
            menu.add(Menu.NONE, MENU_PASTE, order++, android.R.string.paste)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        if (onCutRequested != null) {
            menu.add(Menu.NONE, MENU_CUT, order++, android.R.string.cut)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        if (onSelectAllRequested != null) {
            menu.add(Menu.NONE, MENU_SELECT_ALL, order, android.R.string.selectAll)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
    }

    private companion object {
        const val MENU_COPY = 0
        const val MENU_PASTE = 1
        const val MENU_CUT = 2
        const val MENU_SELECT_ALL = 3
    }
}

/**
 * Provides a [SafeAndroidTextToolbar] bound specifically to the current window's [LocalView].
 * Must be placed inside Activity roots AND inside `AlertDialog` / `ModalBottomSheet` content
 * blocks so each window tracks its own `DecorView` / `ActionMode` lifecycle independently.
 */
@Composable
fun ProvideSafeTextToolbar(content: @Composable () -> Unit) {
    val view = LocalView.current
    val toolbar = remember(view) { SafeAndroidTextToolbar(view) }
    DisposableEffect(toolbar) {
        onDispose { toolbar.release() }
    }
    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        content()
    }
}

/**
 * Clears focus, hides any active floating text toolbar, and dismisses the software keyboard
 * cleanly before closing dialogs, bottom sheets, or form screens.
 */
fun dismissTextInteractions(
    focusManager: FocusManager,
    textToolbar: TextToolbar,
    keyboardController: SoftwareKeyboardController?
) {
    runCatching { textToolbar.hide() }
    runCatching { focusManager.clearFocus(force = true) }
    runCatching { keyboardController?.hide() }
}

@Composable
fun CleanupTextInteractionsOnDispose() {
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current
    val keyboardController = LocalSoftwareKeyboardController.current
    DisposableEffect(focusManager, textToolbar, keyboardController) {
        onDispose {
            dismissTextInteractions(focusManager, textToolbar, keyboardController)
        }
    }
}
