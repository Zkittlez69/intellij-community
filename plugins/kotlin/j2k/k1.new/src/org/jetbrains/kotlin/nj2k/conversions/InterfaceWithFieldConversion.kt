// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.declarationList
import org.jetbrains.kotlin.nj2k.getOrCreateCompanionObject
import org.jetbrains.kotlin.nj2k.tree.*

internal class InterfaceWithFieldConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKClass) return recurse(element)
        if (element.classKind != JKClass.ClassKind.INTERFACE
            && element.classKind != JKClass.ClassKind.ANNOTATION
        ) return recurse(element)

        val fieldsToMoveToCompanion = element.declarationList
            .filterIsInstance<JKField>()
            .filter { field ->
                field.modality == Modality.FINAL || element.classKind == JKClass.ClassKind.ANNOTATION
            }
        if (fieldsToMoveToCompanion.isNotEmpty()) {
            element.classBody.declarations -= fieldsToMoveToCompanion
            val companion = element.getOrCreateCompanionObject()
            companion.classBody.declarations += fieldsToMoveToCompanion
        }
        return recurse(element)
    }
}