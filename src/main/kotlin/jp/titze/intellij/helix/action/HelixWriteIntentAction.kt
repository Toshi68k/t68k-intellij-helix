// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.action

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.ThrowableComputable

object HelixWriteIntentAction {

    inline fun <T> compute(crossinline block: () -> T): T {
        val app = ApplicationManager.getApplication()
        return if (app != null) {
            app.runWriteIntentReadAction(ThrowableComputable { block() })
        } else {
            block()
        }
    }

    inline fun run(crossinline block: () -> Unit) {
        val app = ApplicationManager.getApplication()
        if (app != null) {
            app.runWriteIntentReadAction(ThrowableComputable { block() })
        } else {
            block()
        }
    }
}
