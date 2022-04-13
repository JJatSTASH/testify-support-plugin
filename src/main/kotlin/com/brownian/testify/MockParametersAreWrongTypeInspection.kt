package com.brownian.testify


import com.goide.psi.*
import com.goide.psi.impl.GoTypeUtil
import com.goide.psi.impl.typesCompatibility.GoTypesCompatible
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.LanguageRefactoringSupport
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

class MockParametersAreWrongTypeInspection : LocalInspectionTool() {
    fun GoCallExpr.getObjectType(): GoType? = (this.expression.firstChild as GoExpression).getGoType(null)

    private fun GoNamedSignatureOwner.getMethodParameterTypes(): List<GoType>? =
        this.signature?.parameters?.parameterDeclarationList?.map { it.type!! }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : GoVisitor() {
            override fun visitCallExpr(onMethodCall: GoCallExpr) {
                if (!isMockDotOnCall(onMethodCall)) return super.visitCallExpr(onMethodCall)

                val methodArgumentExpressions = onMethodCall.argumentList.expressionList
                if (methodArgumentExpressions.isEmpty()) {
                    // let another inspection handle this, and just quit since we can't analyze
                    return super.visitCallExpr(onMethodCall)
                }

                val methodNameExpression = methodArgumentExpressions.first()
                if (!(methodNameExpression is GoStringLiteral && methodNameExpression.isConstant)) {
                    // then we can't check anything
                    return super.visitCallExpr(onMethodCall)
                }

                // we want to skip over the name of the mocked method and just match the method arguments
                val parameterMatcherExpressions = methodArgumentExpressions.drop(1)

                val actualMethodName = methodNameExpression.decodedText

                val mockedObjectType = onMethodCall.getObjectType() ?: return

                // if we can't find the declaration then we can't check anything. Let another inspection flag this as a problem, but this one has to stop
                val actualMethodDeclaration =
                    mockedObjectType.findMethodDeclaration(actualMethodName) ?: return super.visitCallExpr(
                        onMethodCall
                    )

                // if we can't get the return type of the declaration then we can't check anything.
                val properParameterTypes =
                    actualMethodDeclaration.getMethodParameterTypes() ?: return super.visitCallExpr(onMethodCall)

                val actualArgumentTypes = parameterMatcherExpressions.map { it.getGoType(null) }

                val numActualArguments = actualArgumentTypes.size
                val numProperParameters = properParameterTypes.size

                if (numActualArguments < numProperParameters) {
                    holder.registerProblem(
                        onMethodCall,
                        "Not enough method arguments for $actualMethodName mock: Expected $numProperParameters but found $numActualArguments"
                    )
                    return
                } else if (numActualArguments > numProperParameters) {
                    holder.registerProblem(
                        onMethodCall,
                        "Too many method arguments for $actualMethodName mock: Expected $numProperParameters but found $numActualArguments"
                    )
                    return
                }

                actualArgumentTypes.zip(properParameterTypes).forEachIndexed { idx, (actualType, properType) ->
                    val actualArgument = parameterMatcherExpressions[idx]

                    // handle testify.Mock matchers specially, since otherwise the apparent types will appear to mismatch:

                    if (isMockAnythingMatcher(actualArgument)) {
                        // this matcher matches everything, so we don't need to both checking assignability
                        return@forEachIndexed
                    } else if (isMockAnythingOfTypeMatcher(actualArgument)) {
                        checkMockAnythingOfTypeMatchesType(actualArgument as GoCallExpr, holder, properType)
                        return@forEachIndexed
                    } else if (isMockMatchedBy(actualArgument)) {
                        checkMockMatchedByMatchesType(actualArgument as GoCallExpr, holder, properType)
                        return@forEachIndexed
                    }

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
                        "Parameter $idx for method $actualMethodName is $properTypeName but mocked value is $actualTypeName",
                        ProblemHighlightType.ERROR
                    )
                }
            }
        }
    }

    private fun checkMockAnythingOfTypeMatchesType(
        mockAnythingOfTypeCall: GoCallExpr,
        holder: ProblemsHolder,
        properType: GoType
    ) {
        if (mockAnythingOfTypeCall.argumentList.expressionList.size != 1) {
            // this warning will be caught by the normal type system, and we can't analyze, so don't bother for now
            return
        }
        val matchedTypeNameArgument = mockAnythingOfTypeCall.argumentList.expressionList.first()
        if (matchedTypeNameArgument !is GoStringLiteral) {
            holder.registerProblem(
                matchedTypeNameArgument,
                "Consider replacing this type name with a string literal",
                ProblemHighlightType.WARNING
            )
            return
        }
        if (matchedTypeNameArgument.decodedText == properType.text) {
            return
        }
        // TODO: produce an auto-completion for this?
        // TODO: produce a quick fix for this
        holder.registerProblem(
            matchedTypeNameArgument,
            "Matched type name ${matchedTypeNameArgument.decodedText} doesn't match corresponding parameter type ${properType.text}",
            ProblemHighlightType.WARNING
        )
        return
    }

    private fun checkMockMatchedByMatchesType(
        mockMatchedByCall: GoCallExpr,
        holder: ProblemsHolder,
        properType: GoType
    ) {
        if (mockMatchedByCall.argumentList.expressionList.size != 1) {
            // this warning will be caught by the normal type system, and we can't analyze, so don't bother for now
            return
        }
        val matchingFunc = mockMatchedByCall.argumentList.expressionList.first()
        val matchingFuncUnknownType = matchingFunc.getGoType(null)
        if (!GoTypeUtil.isFunction(matchingFuncUnknownType, matchingFunc)) {
            holder.registerProblem(
                matchingFunc,
                "Argument to mock.MatchedBy() must be a single-argument function.",
                ProblemHighlightType.WARNING
            )
            return
        }
        val matchingFuncType = matchingFuncUnknownType!!.getUnderlyingType(matchingFunc) as GoFunctionType
        val parameters = matchingFuncType.signature?.parameters?.parameterDeclarationList
        if (parameters.isNullOrEmpty() || parameters.size > 1) {
            holder.registerProblem(
                matchingFunc,
                "Argument to mock.MatchedBy() must be a single-argument function.",
                ProblemHighlightType.WARNING,
                ChangeLambdaSignatureLocalQuickFix(matchingFunc)
            )
            return
        }

        if (!GoTypeUtil.equalTypes(parameters.first().type, properType, parameters.first(), true)) {
            holder.registerProblem(
                matchingFunc,
                "Argument to mock.MatchedBy() matcher must be ${properType.text}",
                ProblemHighlightType.WARNING,
                ChangeLambdaSignatureLocalQuickFix(matchingFunc)
            )
            return
        }

        if (!GoTypeUtil.isBoolean(matchingFuncType.resultType, matchingFunc)) {
            holder.registerProblem(
                matchingFunc,
                "Matcher argument to mock.MatchedBy() must return bool.",
                ProblemHighlightType.WARNING,
                ChangeLambdaSignatureLocalQuickFix(matchingFunc)
            )
            return
        }
    }
}

// FIXME: this doesn't actually work 😬
class ChangeLambdaSignatureLocalQuickFix(lambdaFunc: GoExpression?) :
    LocalQuickFixAndIntentionActionOnPsiElement(lambdaFunc) {
    override fun getFamilyName() = "Change Signature."

    override fun getText() = "Change Signature."

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val refactorSupport = LanguageRefactoringSupport.INSTANCE.forContext(startElement) ?: return
        val changeSignatureHandler = refactorSupport.changeSignatureHandler ?: return
        changeSignatureHandler.invoke(project, arrayOf(startElement), null)
    }
}