package com.brownian.testify

import com.goide.psi.GoCallExpr
import com.goide.psi.GoFunctionDeclaration
import com.goide.psi.GoMethodDeclaration
import com.goide.psi.GoNamedSignatureOwner
import com.goide.psi.impl.GoPsiUtil
import com.intellij.util.ObjectUtils


fun isMatchingMethodCall(expr: GoCallExpr, methodName: String, qualifiedName: String): Boolean {
    val callRef = GoPsiUtil.getCallReference(expr) ?: return false
    val text = callRef.identifier.text
    if (text != methodName) return false
    val declaration = ObjectUtils.tryCast(callRef.resolve(), GoNamedSignatureOwner::class.java) ?: return false
    if (declaration !is GoMethodDeclaration) return false

    return declaration.qualifiedName == qualifiedName
}

fun isMatchingFunctionCall(expr: GoCallExpr, functionName: String, qualifiedName: String): Boolean {
    val callRef = GoPsiUtil.getCallReference(expr) ?: return false
    val text = callRef.identifier.text
    if (text != functionName) return false
    val declaration = ObjectUtils.tryCast(callRef.resolve(), GoNamedSignatureOwner::class.java) ?: return false
    if (declaration !is GoFunctionDeclaration) return false

    return declaration.qualifiedName == qualifiedName
}