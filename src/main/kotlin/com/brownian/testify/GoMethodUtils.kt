package com.brownian.testify

import com.goide.psi.*
import com.goide.psi.impl.GoTypeUtil

fun GoCallExpr.getReceiverType(): GoType? = (this.expression.firstChild as GoExpression).getGoType(null)
fun GoNamedSignatureOwner.getMethodReturnTypes(): List<GoType>? {
    val possiblyCompoundType: GoResult = this.signature?.result ?: return null
    return if (possiblyCompoundType.type == null) {
        possiblyCompoundType.parameters?.parameterDeclarationList?.map { it.type!! }
    } else {
        listOf(possiblyCompoundType.type!!)
    }
}

fun GoType.findMethodDeclaration(methodName: String): GoNamedSignatureOwner? =
    GoTypeUtil.findImplementedMethods(this, null).firstOrNull { it.name == methodName }