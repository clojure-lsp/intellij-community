// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performance.performancePlugin.commands

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsShowConfirmationOption
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.openapi.vfs.findFileOrDirectory
import com.intellij.platform.diagnostic.telemetry.impl.useWithScope
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.impl.file.PsiDirectoryImpl
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import io.opentelemetry.context.Context

/**
 * Command to add Java file to project
 * Example: %createKotlinFile fileName, dstDir, fileType - data, file, enum, interface, sealed, annotation, script, worksheet, object]
 */
class CreateKotlinFileCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

    companion object {
        const val NAME = "createKotlinFile"
        const val PREFIX = CMD_PREFIX + NAME
        val POSSIBLE_FILE_TYPES = mapOf(
            Pair("class", "Kotlin Class"),
            Pair("script", "Kotlin script"),
            Pair("worksheet", "Kotlin worksheet"),
            Pair("data", "Kotlin Data Class"),
            Pair("enum", "Kotlin Enum"),
            Pair("annotation", "Kotlin Annotation"),
            Pair("object", "Kotlin Object"),
            Pair("file", "Kotlin File"),
            Pair("interface", "Kotlin Interface")
        )
    }

    override suspend fun doExecute(context: PlaybackContext) {
        val (fileName, filePath, fileType) = extractCommandArgument(PREFIX).replace("\\s","").split(",")
        val directory = PsiDirectoryImpl(
            PsiManagerImpl(context.project),
            (context.project.guessProjectDir() ?: throw RuntimeException("'guessProjectDir' dir returned 'null'"))
                .findFileOrDirectory(filePath) ?: throw RuntimeException("Can't find file $filePath")
        )

        val templateName = POSSIBLE_FILE_TYPES[fileType.lowercase()]
        if (templateName == null) throw RuntimeException("File type must be one of '${POSSIBLE_FILE_TYPES.keys}'")
        val template = FileTemplateManager.getInstance(directory.project).getInternalTemplate(templateName)

        //Disable vcs dialog which appears on adding new file to the project tree
        ProjectLevelVcsManagerEx
            .getInstanceEx(context.project)
            .getConfirmation(VcsConfiguration.StandardConfirmation.ADD)
            .value = VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY

        ApplicationManager.getApplication().invokeAndWait(Context.current().wrap(Runnable {
            PerformanceTestSpan.TRACER.spanBuilder(NAME).useWithScope {
                CreateFileFromTemplateAction
                    .createFileFromTemplate(fileName, template, directory, null, true)
            }
        }))

    }

    override fun getName(): String = NAME

}