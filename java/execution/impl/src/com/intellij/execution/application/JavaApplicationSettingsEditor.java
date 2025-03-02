// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.application;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.ui.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Predicates;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.intellij.execution.ui.CommandLinePanel.setMinimumWidth;

public final class JavaApplicationSettingsEditor extends JavaSettingsEditorBase<ApplicationConfiguration> {

  public JavaApplicationSettingsEditor(ApplicationConfiguration configuration) {
    super(configuration);
  }

  @Override
  public boolean isInplaceValidationSupported() {
    return true;
  }

  @Override
  protected void customizeFragments(List<SettingsEditorFragment<ApplicationConfiguration, ?>> fragments,
                                    SettingsEditorFragment<ApplicationConfiguration, ModuleClasspathCombo> moduleClasspath,
                                    CommonParameterFragments<ApplicationConfiguration> commonParameterFragments) {
    fragments.add(SettingsEditorFragment.createTag("include.provided",
                                                   ExecutionBundle.message("application.configuration.include.provided.scope"),
                                                   ExecutionBundle.message("group.java.options"),
                                     configuration -> configuration.getOptions().isIncludeProvidedScope(),
                                     (configuration, value) -> configuration.getOptions().setIncludeProvidedScope(value)));
    fragments.add(commonParameterFragments.programArguments());
    fragments.add(new TargetPathFragment<>());
    fragments.add(commonParameterFragments.createRedirectFragment());
    SettingsEditorFragment<ApplicationConfiguration, ClassEditorField> mainClassFragment = createMainClass(moduleClasspath.component());
    fragments.add(mainClassFragment);
    DefaultJreSelector jreSelector = DefaultJreSelector.fromSourceRootsDependencies(moduleClasspath.component(), mainClassFragment.component());
    SettingsEditorFragment<ApplicationConfiguration, JrePathEditor> jrePath = CommonJavaFragments.createJrePath(jreSelector);
    fragments.add(jrePath);
    fragments.add(createShortenClasspath(moduleClasspath.component(), jrePath, true));
  }

  @NotNull
  private SettingsEditorFragment<ApplicationConfiguration, ClassEditorField> createMainClass(ModuleClasspathCombo classpathCombo) {
    ConfigurationModuleSelector moduleSelector = new ConfigurationModuleSelector(getProject(), classpathCombo);
    ClassEditorField mainClass = ClassEditorField.createClassField(getProject(), () -> classpathCombo.getSelectedModule(),
                                                                  ApplicationConfigurable.getVisibilityChecker(moduleSelector), null);
    mainClass.setBackground(UIUtil.getTextFieldBackground());
    mainClass.setShowPlaceholderWhenFocused(true);
    CommonParameterFragments.setMonospaced(mainClass);
    String placeholder = ExecutionBundle.message("application.configuration.main.class.placeholder");
    mainClass.setPlaceholder(placeholder);
    mainClass.getAccessibleContext().setAccessibleName(placeholder);
    setMinimumWidth(mainClass, 300);
    SettingsEditorFragment<ApplicationConfiguration, ClassEditorField> mainClassFragment =
      new SettingsEditorFragment<>("mainClass", ExecutionBundle.message("application.configuration.main.class"), null, mainClass, 20,
                                   (configuration, component) -> component.setClassName(configuration.getMainClassName()),
                                   (configuration, component) -> configuration.setMainClassName(component.getClassName()),
                                   Predicates.alwaysTrue()) {
        @Override
        public boolean isReadyForApply() {
          return myComponent.isReadyForApply();
        }
      };
    mainClassFragment.setHint(ExecutionBundle.message("application.configuration.main.class.hint"));
    mainClassFragment.setRemovable(false);
    mainClassFragment.setEditorGetter(field -> {
      Editor editor = field.getEditor();
      return editor == null ? field : editor.getContentComponent();
    });
    mainClassFragment.setValidation((configuration) ->
      Collections.singletonList(RuntimeConfigurationException.validate(mainClass, () -> configuration.checkClass())));
    return mainClassFragment;
  }
}
