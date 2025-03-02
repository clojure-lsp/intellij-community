// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl

import com.intellij.lang.Language
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.usages.ShowUsagesSettings.Companion.instance
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageViewPresentation
import com.intellij.usages.UsageViewSettings
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Service(Service.Level.PROJECT)
class UsageViewPopupManager(val project: Project) {
  fun createUsageViewPopup(targetLanguage: Language?): UsageViewPopup {
    if (targetLanguage == null) return UsageViewPopup(project)
    for (factory in UsageViewPopupFactory.EP_NAME.extensionList) {
      val result = factory.createUsageViewPopup(project, targetLanguage)
      if (result != null) {
        return result
      }
    }
    return UsageViewPopup(project)
  }
}

@Internal
open class UsageViewPopup(project: Project) :
  UsageViewImpl(project, UsageViewPresentation().apply { isDetachedMode = true }, UsageTarget.EMPTY_ARRAY, null) {
  override fun getUsageViewSettings(): UsageViewSettings = instance.state
}

@Internal
interface UsageViewPopupFactory {
  /**
   * Creates a language-specific "Show Usages" pop-up instance for displaying usages of a PSI element.
   * @param project Opened project
   * @param targetLanguage Programming language of a PSI element, usages of which must be displayed.
   * @return A pop-up instance or `null` if this factory does not handle usages in the provided programming language.
   */
  fun createUsageViewPopup(project: Project, targetLanguage: Language): UsageViewPopup?

  companion object {
    val EP_NAME = ExtensionPointName.create<UsageViewPopupFactory>("com.intellij.usageViewPopupFactory")
  }
}
