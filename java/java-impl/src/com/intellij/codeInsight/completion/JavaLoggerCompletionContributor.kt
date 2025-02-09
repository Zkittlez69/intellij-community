// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.generation.GenerateLoggerUtil
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.logging.JvmLogger
import com.intellij.lang.logging.JvmLoggerFieldDelegate
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiJavaToken
import com.intellij.util.ProcessingContext

class JavaLoggerCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC,
           psiElement()
             .withSuperParent(2, PsiExpressionStatement::class.java)
             .afterLeaf(StandardPatterns.or(
               psiElement(PsiJavaToken::class.java).withElementType(JavaTokenType.SEMICOLON),
               psiElement(PsiJavaToken::class.java).withElementType(JavaTokenType.COLON),
               psiElement(PsiJavaToken::class.java).withElementType(JavaTokenType.LBRACE),
               psiElement(PsiJavaToken::class.java).withElementType(JavaTokenType.ARROW),
             )),
           object : CompletionProvider<CompletionParameters>() {
             override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
               val javaResultWithSorting = JavaCompletionSorting.addJavaSorting(parameters, result)
               val module = ModuleUtil.findModuleForFile(parameters.originalFile) ?: return
               val project = module.project
               val availableLoggers = GenerateLoggerUtil.findSuitableLoggers(module, true)

               val element = parameters.originalPosition ?: return
               val allPlaces = GenerateLoggerUtil.getAllNestedClasses(element).toList()
               val possiblePlaces = GenerateLoggerUtil.getPossiblePlacesForLogger(element, availableLoggers)
               if (allPlaces.size != possiblePlaces.size) return

               val place = possiblePlaces.firstOrNull() ?: return

               for (logger in availableLoggers) {
                 val lookupElement = buildLoggerElement(project, place, logger)
                 javaResultWithSorting.addElement(lookupElement)
               }
             }
           })
  }

  private fun buildLoggerElement(project: Project, place: PsiClass, logger: JvmLogger): LookupElement =
    LoggerLookupElement(
      LookupElementBuilder
        .create(logger.loggerTypeName, JvmLoggerFieldDelegate.LOGGER_IDENTIFIER)
        .withTailText(" ${logger.loggerTypeName}")
        .withTypeText(logger.toString())
        .withInsertHandler { insertionContext, _ ->
          val loggerText = logger.createLogger(project, place) ?: return@withInsertHandler
          logger.insertLoggerAtClass(insertionContext.project, place, loggerText)
        },
      logger.loggerTypeName
    )
}
