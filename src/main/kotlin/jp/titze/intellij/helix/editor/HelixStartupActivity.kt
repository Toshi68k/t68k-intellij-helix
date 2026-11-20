// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Ensures the global [HelixEventDispatcher] is installed as soon as a project opens,
 * even if no file editor has been created yet (e.g. when focusing a tool window first).
 */
class HelixStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val app = ApplicationManager.getApplication() ?: return
        app.invokeLater {
            val lifecycle = app.getService(HelixPluginLifecycle::class.java)
            HelixEventDispatcher.install(lifecycle)
        }
    }
}
