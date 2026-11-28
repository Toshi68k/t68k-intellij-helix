// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.action

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ThrowableComputable

object HelixReadAction {

    inline fun <T> compute(crossinline block: () -> T): T = ApplicationManager.getApplication().runReadAction(
        Computable {
            block()
        },
    )

    inline fun run(crossinline block: () -> Unit) {
        ApplicationManager.getApplication().runReadAction { block() }
    }

    @Suppress("UnstableApiUsage")
    fun <T> runWriteIntent(block: () -> T): T {
        val app = ApplicationManager.getApplication()
        if (app == null || app.isReadAccessAllowed) {
            return block()
        }
        // IdeEventQueue preprocessors run without a read/write-intent lock on 2025.1+.
        // Editor dispatch needs WriteIntent (ReadAction alone breaks WriteCommandAction).
        // Use Application.runWriteIntentReadAction (stable across 251-263; WriteIntentReadAction.compute
        // was renamed in 261+) and keep this method non-inline so @Suppress("UnstableApiUsage") stays isolated.
        return app.runWriteIntentReadAction(
            ThrowableComputable {
                block()
            },
        )
    }
}
