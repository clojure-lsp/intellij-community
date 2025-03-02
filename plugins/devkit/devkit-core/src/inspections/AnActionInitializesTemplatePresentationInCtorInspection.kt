// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.isAnonymousOrLocal
import com.intellij.codeInspection.registerUProblem
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.InheritanceUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import javax.swing.Icon

internal class AnActionInitializesTemplatePresentationInCtorInspection : DevKitUastInspectionBase(UClass::class.java) {

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return UastHintedVisitorAdapter.create(holder.file.language, object : AbstractUastNonRecursiveVisitor() {
      override fun visitClass(node: UClass): Boolean {
        if (!isRegisteredAction(node)) return true
        val constructors = node.methods.filter { it.isConstructor }
        if (constructors.isEmpty()) {
          val ctorInitializingTemplatePresentation = getCtorInitializingTemplatePresentation(node, null)
          if (ctorInitializingTemplatePresentation != null) {
            val message = DevKitBundle.message("inspection.an.action.initializes.template.presentation.in.ctor.message")
            holder.registerUProblem(node, message)
          }
        }
        else {
          for (constructor in constructors) {
            val ctorInitializingTemplatePresentation = getCtorInitializingTemplatePresentation(node, constructor)
            if (ctorInitializingTemplatePresentation != null) {
              val message = DevKitBundle.message("inspection.an.action.initializes.template.presentation.in.ctor.message")
              holder.registerUProblem(constructor, message)
            }
          }
        }
        return true
      }
    }, arrayOf(UClass::class.java))
  }

  private fun isRegisteredAction(uClass: UClass): Boolean {
    if (uClass.isAnnotationType || uClass.isInterface || uClass.isAnonymousOrLocal()) return false
    if (!InheritanceUtil.isInheritor(uClass.javaPsi, AnAction::class.java.canonicalName)) return false
    val types = RegistrationCheckerUtil.getRegistrationTypes(uClass.javaPsi, RegistrationCheckerUtil.RegistrationType.ACTION)
    return !types.isNullOrEmpty()
  }

  private fun getSuperClass(uClass: UClass): UClass? {
    return uClass.javaPsi.superClass.toUElement(UClass::class.java)
  }

  private fun findNoArgCtor(uClass: UClass): UMethod? {
    return uClass.methods.filter { it.isConstructor }.find { it.uastParameters.isEmpty() }
  }

  private fun getCtorInitializingTemplatePresentation(uClass: UClass, constructor: UMethod?): UMethod? {
    return getNextClassAndConstructorInConstructorChain(uClass, constructor)?.second
  }

  /**
   * Performs a recursive traversal of the constructor call chain until the traversal is finished or until the [AnAction] constructor
   * that initializes the template presentation of the action is found.
   *
   * This method analyzes the body of the given constructor and checks for explicit calls to `this` or `super` as the first expression,
   * as well as implicit calls to `super`.
   *x
   * @param uClass The current class being analyzed.
   * @param constructor The current constructor being analyzed. A `null` value represents the default constructor (generated by the compiler).
   * @return A pair containing the class and its constructor that will be visited from the [constructor], or `null` if the traversal is finished
   * and the [AnAction] constructor that initializes the template presentation of the action is not found.
   */
  private fun getNextClassAndConstructorInConstructorChain(uClass: UClass, constructor: UMethod?): Pair<UClass, UMethod?>? {
    assert(constructor == null || constructor.isConstructor)
    if (constructor != null && isCtorInitializingTemplatePresentation(uClass, constructor)) {
      return Pair(uClass, constructor)
    }
    if (constructor != null) {
      val constructorBody = constructor.uastBody
      val firstExpression = (constructorBody as? UBlockExpression)?.expressions?.firstOrNull() ?: constructorBody
      if (firstExpression is UCallExpression) {
        val methodName = firstExpression.methodIdentifier?.name ?: firstExpression.methodName
        if (methodName == "this" || methodName == "super") {
          val thisOrSuperCtorCalledExplicitly = firstExpression.resolveToUElement()
          if (thisOrSuperCtorCalledExplicitly !is UMethod) return null
          val containingUClass = thisOrSuperCtorCalledExplicitly.getContainingUClass() ?: return null
          return getNextClassAndConstructorInConstructorChain(containingUClass, thisOrSuperCtorCalledExplicitly)
        }
      }
    }
    val superClass = getSuperClass(uClass) ?: return null
    val superCtorCalledImplicitly = findNoArgCtor(superClass)
    return getNextClassAndConstructorInConstructorChain(superClass, superCtorCalledImplicitly)
  }

  private fun isCtorInitializingTemplatePresentation(uClass: UClass, constructor: UMethod): Boolean {
    if (uClass.qualifiedName != AnAction::class.java.canonicalName) return false
    val uastParameters = constructor.uastParameters
    return uastParameters.size == 3 && uastParameters[0].type.canonicalText == STRING_SUPPLIER_FQN && uastParameters[1].type.canonicalText == STRING_SUPPLIER_FQN && uastParameters[2].type.canonicalText == Icon::class.java.canonicalName
  }
}

private const val STRING_SUPPLIER_FQN = "java.util.function.Supplier<java.lang.String>"