package com.brownian.testify


import com.goide.psi.*
import com.goide.psi.impl.GoTypeUtil
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor

class MockingUnknownMethodInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : GoVisitor() {
            override fun visitCallExpr(o: GoCallExpr) {
                if(!isMockDotOnCall(o)) return super.visitCallExpr(o)

                val arguments = o.argumentList.expressionList
                if (arguments.isEmpty()) {
                    holder.registerProblem(o, "No method name specified for Mock.On() call", ProblemHighlightType.ERROR)
                    return
                }

                val methodNameExpression = arguments.first()
                if (!(methodNameExpression is GoStringLiteral && methodNameExpression.isConstant)) {
                    // then we can't check anything
                    return super.visitCallExpr(o)
                }

                val actualMethodName = methodNameExpression.decodedText

                val objectType = o.getReceiverType() ?: return

                val actualMethodDeclaration = objectType.findMethodDeclaration(actualMethodName)

                if(actualMethodDeclaration == null){
                    holder.registerProblem(o, "Could not find mocked method $actualMethodName", ProblemHighlightType.WARNING)
                }

            }
        }
    }
}