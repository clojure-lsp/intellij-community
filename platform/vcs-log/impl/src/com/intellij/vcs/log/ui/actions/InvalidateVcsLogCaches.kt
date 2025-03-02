// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ModalTaskOwner
import com.intellij.openapi.progress.TaskCancellation
import com.intellij.openapi.progress.runBlockingModal
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.impl.invalidateCaches
import com.intellij.vcs.log.impl.storageIds
import com.intellij.vcs.log.util.VcsLogUtil
import org.jetbrains.annotations.Nls

private class InvalidateVcsLogCaches : DumbAwareAction(actionText(VcsLogBundle.message("vcs"))) {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val logManager = VcsProjectLog.getInstance(project).logManager
    if (logManager == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isVisible = true
    e.presentation.isEnabled = logManager.storageIds().isNotEmpty()
    e.presentation.text = actionText(VcsLogUtil.getVcsDisplayName(project, logManager))
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val projectLog = VcsProjectLog.getInstance(project)
    val logManager = projectLog.logManager ?: return

    val invalidateCacheFuture = projectLog.invalidateCaches(logManager) ?: return
    val vcsName = VcsLogUtil.getVcsDisplayName(project, logManager)
    runBlockingModal(owner = ModalTaskOwner.project(project),
                     title = VcsLogBundle.message("vcs.log.invalidate.caches.progress", vcsName),
                     cancellation = TaskCancellation.nonCancellable()) {
      invalidateCacheFuture.join()
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

private fun actionText(vcsName: String): @Nls String {
  return VcsLogBundle.message("vcs.log.invalidate.caches.text", vcsName)
}