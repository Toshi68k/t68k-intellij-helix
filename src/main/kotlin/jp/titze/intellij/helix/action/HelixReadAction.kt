// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.action

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable

object HelixReadAction {

    inline fun <T> compute(crossinline block: () -> T): T = ApplicationManager.getApplication().runReadAction(
        Computable {
            block()
        },
    )

    inline fun run(crossinline block: () -> Unit) {
        ApplicationManager.getApplication().runReadAction(Runnable { block() })
    }
}
