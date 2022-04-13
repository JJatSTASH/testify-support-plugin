package com.brownian.testify

import com.goide.psi.*

fun isMockDotOnCall(expr: GoCallExpr): Boolean =
    isMatchingMethodCall(expr, "On", "Mock.On")

fun isCallDotOnCall(expr: GoCallExpr): Boolean =
    isMatchingMethodCall(expr, "On", "Call.On")

fun isCallDotReturnCall(expr: GoCallExpr): Boolean =
    isMatchingMethodCall(expr, "Return", "Call.Return")

fun isMockAnythingMatcher(actualArgument: GoExpression) =
    actualArgument.text == "mock.Anything"

 fun isMockAnythingOfTypeMatcher(actualArgument: GoExpression) =
    !actualArgument.isConstant && actualArgument is GoCallExpr && isMatchingFunctionCall(
        actualArgument,
        "AnythingOfType",
        "mock.AnythingOfType"
    )

 fun isMockMatchedBy(actualArgument: GoExpression) =
    !actualArgument.isConstant && actualArgument is GoCallExpr && isMatchingFunctionCall(
        actualArgument,
        "MatchedBy",
        "mock.MatchedBy"
    )