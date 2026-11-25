// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import jp.titze.intellij.helix.config.HelixConfigLoader

/**
 * Ensures the global [HelixEventDispatcher] is installed as soon as a project opens,
 * loads project-local configuration (.helix/intellij.toml), and registers disposal.
 */
class HelixStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val app = ApplicationManager.getApplication() ?: return
        app.invokeLater {
            val lifecycle = app.getService(HelixPluginLifecycle::class.java)
            HelixEventDispatcher.install(lifecycle)
            HelixConfigLoader.loadProjectConfig(project)
            Disposer.register(project) {
                HelixConfigLoader.unloadProjectConfig(project)
            }
        }
    }
}
