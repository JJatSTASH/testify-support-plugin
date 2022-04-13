package com.brownian.testify

import com.goide.psi.*
import com.goide.psi.impl.GoPsiUtil
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.util.ObjectUtils

typealias AssertEqualsCall = GoCallExpr

val AssertEqualsCall.expectedArgument get() = this.argumentList.expressionList.getOrNull(1)

val AssertEqualsCall.actualArgument get() = this.argumentList.expressionList.getOrNull(2)

open class AssertEqualsCallVisitor : GoVisitor() {
    override fun visitCallExpr(o: GoCallExpr) {
        if (isAssertEquals(o)) {
            this.visitAssertEqualsCall(o)
        } else {
            super.visitCallExpr(o)
        }
    }

    open fun visitAssertEqualsCall(o: AssertEqualsCall) {
        super.visitCallExpr(o)
    }

    private fun isAssertEquals(expr: GoCallExpr): Boolean {
        try {
            val callRef = GoPsiUtil.getCallReference(expr) ?: return false
            val text = callRef.identifier.text
            if (text != "Equal") return false
            val declaration = ObjectUtils.tryCast(callRef.resolve(), GoNamedSignatureOwner::class.java) ?: return false
            if (declaration !is GoFunctionDeclaration) return false

            val qualifiedName = declaration.qualifiedName
            return qualifiedName == "assert.Equal"
        } catch(e: Exception){
            // TODO: log this exception
            return false
        }
    }
}

class AssertEqualsSwappedExpectedActualInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : AssertEqualsCallVisitor() {
            override fun visitAssertEqualsCall(o: AssertEqualsCall) {
                val actual = o.actualArgument
                val expected = o.expectedArgument
                if (actual == null || expected == null) return
                if (actual.isConstant && !expected.isConstant) {
                    holder.registerProblem(
                        o,
                        "Expected and Actual values might be out of order",
                        ProblemHighlightType.WARNING,
                        SwapExpectedAndActualArgumentsIntention(o)
                    )
                }
            }
        }
    }
}

// TODO: use the "Flip comma" intention built in to Goland instead!
class SwapExpectedAndActualArgumentsIntention(call: AssertEqualsCall) : LocalQuickFixOnPsiElement(call) {
    override fun getFamilyName(): String = "Swap expected and actual arguments"

    override fun getText(): String = familyName

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, _ignored: PsiElement) {
        assert(startElement is AssertEqualsCall)

        val call = startElement as AssertEqualsCall

        val temp = call.actualArgument?.copy() ?: return
        call.actualArgument?.replace(call.expectedArgument ?: return)
        call.expectedArgument?.replace(temp)
    }

}