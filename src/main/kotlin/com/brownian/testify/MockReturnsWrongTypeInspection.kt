package com.brownian.testify


import com.goide.psi.*
import com.goide.psi.impl.GoTypeUtil
import com.goide.psi.impl.typesCompatibility.GoTypesCompatible
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor

class MockReturnsWrongTypeInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : GoVisitor() {
            override fun visitCallExpr(returnMethodCall: GoCallExpr) {
                if (!isCallDotReturnCall(returnMethodCall)) return super.visitCallExpr(returnMethodCall)

                val returnValues = returnMethodCall.argumentList.expressionList

                if (returnMethodCall.expression.firstChild !is GoCallExpr) {
                    holder.registerProblem(
                        returnMethodCall,
                        "Using Mock.Return requires a Mock.On() call before it",
                        ProblemHighlightType.ERROR
                    )
                    return
                }
                val onMethodCall = returnMethodCall.expression.firstChild as GoCallExpr
                if (!isMockDotOnCall(onMethodCall)) {
                    holder.registerProblem(
                        returnMethodCall,
                        "Analyzing .Return() call requires a Mock.On() call before it; this site will be ignored",
                        ProblemHighlightType.WEAK_WARNING
                    )
                    return
                }

                val methodMatcherArguments = onMethodCall.argumentList.expressionList
                if (methodMatcherArguments.isEmpty()) {
                    // then we can't check anything. Let another inspection flag this as a problem, but this one has to stop
                    return super.visitCallExpr(returnMethodCall)
                }

                val methodNameExpression = methodMatcherArguments.first()
                if (!(methodNameExpression is GoStringLiteral && methodNameExpression.isConstant)) {
                    // then we can't check anything
                    return super.visitCallExpr(returnMethodCall)
                }

                val actualMethodName = methodNameExpression.decodedText

                val mockedObjectType = onMethodCall.getReceiverType() ?: return

                // if we can't find the declaration then we can't check anything. Let another inspection flag this as a problem, but this one has to stop
                val actualMethodDeclaration =
                    mockedObjectType.findMethodDeclaration(actualMethodName) ?: return super.visitCallExpr(
                        returnMethodCall
                    )

                // if we can't get the return type of the declaration then we can't check anything.
                val properReturnTypes =
                    actualMethodDeclaration.getMethodReturnTypes() ?: return super.visitCallExpr(returnMethodCall)

                val actualReturnTypes = returnValues.map { it.getGoType(null) }

                if (actualReturnTypes.size < properReturnTypes.size) {
                    holder.registerProblem(
                        returnMethodCall,
                        "not enough return parameters for $actualMethodName mock: expected $properReturnTypes"
                    )
                    return
                } else if (actualReturnTypes.size > properReturnTypes.size) {
                    holder.registerProblem(
                        returnMethodCall,
                        "too many return parameters for $actualMethodName mock: expected $properReturnTypes"
                    )
                    return
                }

                actualReturnTypes.zip(properReturnTypes).forEachIndexed { idx, (actualType, properType) ->
                    val actualArgument = returnValues[idx]

                    val assignability =
                        GoTypeUtil.checkAssignable(
                            properType,
                            actualArgument as GoTypeOwner,
                            actualType,
                            actualArgument,
                            true
                        )

                    if (assignability is GoTypesCompatible) {
                        return
                    }


                    val actualTypeName = if (actualArgument.text == "nil") {
                        // otherwise the printed actual type name would be "Type", which is useless
                        "nil"
                    } else {
                        actualType?.text
                    }
                    val properTypeName = properType.text

                    holder.registerProblem(
                        actualArgument,
                        "Return value $idx for method $actualMethodName is $properTypeName but mocked value is $actualTypeName",
                        ProblemHighlightType.WARNING
                    )
                }
            }
        }
    }

}