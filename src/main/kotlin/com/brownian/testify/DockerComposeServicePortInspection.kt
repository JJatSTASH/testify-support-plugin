package com.brownian.testify

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.*
import java.util.function.Consumer

typealias ServiceConfig = YAMLKeyValue

fun YAMLValue.getChildWithKey(key: String): YAMLKeyValue? =
    this.children.filterIsInstance<YAMLKeyValue>().firstOrNull { it.keyText == key }

fun YAMLValue.visitChildKeyValues(visitor: Consumer<YAMLKeyValue>) =
    this.children.filterIsInstance<YAMLKeyValue>().forEach(visitor)

fun YAMLDocument.visitServices(visitor: Consumer<ServiceConfig>) {
    topLevelValue?.getChildWithKey("services")?.value?.visitChildKeyValues(visitor)
}

typealias PortsKeyValue = YAMLKeyValue

fun ServiceConfig.getPortsConfig() : PortsKeyValue? = when (this.value) {
    is YAMLMapping -> (this.value as YAMLMapping).getKeyValueByKey("ports")
    else -> null
}

fun PortsKeyValue?.getPorts() = when(val value = this?.value){
    is YAMLSequence -> value.items.map { it.value?.text.orEmpty() }.toTypedArray()
    is YAMLScalar -> arrayOf(value.text)
    else -> emptyArray()
}

fun ServiceConfig.requirePort(expectedSetting: String, holder: ProblemsHolder) {
    val portsConfig = this.getPortsConfig()
    val actualPortValues = portsConfig.getPorts()
    when {
        portsConfig == null || actualPortValues.isEmpty() -> {
            val message = "Service $keyText is usually $expectedSetting but was not specified"
            holder.registerProblem(portsConfig?:this, message, ProblemHighlightType.WARNING, SetPortSettingsQuickFix("Add", this, expectedSetting))
        }
        actualPortValues.size == 1 -> {
            val actualSetting = actualPortValues.first()
            if(actualSetting == expectedSetting) return // no problem!
            val message = "Service $keyText is usually $expectedSetting but was $actualSetting"
            holder.registerProblem(portsConfig.value?:portsConfig, message, ProblemHighlightType.WARNING, SetPortSettingsQuickFix("Set", this, expectedSetting))
        }
        else -> {
            val message = "Service $keyText is usually single value $expectedSetting but was multiple values: $actualPortValues"
            holder.registerProblem(portsConfig, message, ProblemHighlightType.WARNING, SetPortSettingsQuickFix("Set", this, expectedSetting))
        }
    }
}

class DockerComposeServicePortInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : YamlPsiElementVisitor() {
            override fun visitDocument(document: YAMLDocument) {
                // This inspection ONLY checks docker-compose.yml files for standard port numbers
                if(document.containingFile.name != "docker-compose.yml") return
                document.visitServices { serviceConfig ->
                    when(serviceConfig.keyText){
                        "db" -> serviceConfig.requirePort("\"5432:5432\"", holder)
                        "grpc" -> serviceConfig.requirePort("\"4003:80\"", holder)
                        "gateway" -> serviceConfig.requirePort("\"4002:80\"", holder)
                        "api" -> serviceConfig.requirePort("\"4000:80\"", holder)
                    }
                }
            }
        }
    }
}

class SetPortSettingsQuickFix(val actionVerb: String, serviceConfig : ServiceConfig, val newPortSetting : String) : LocalQuickFixOnPsiElement(serviceConfig){
    private val serviceName = serviceConfig.name

    override fun getFamilyName() = "$actionVerb standard port forward specification"

    override fun getText(): String = "$actionVerb standard $serviceName port forward specification $newPortSetting"

    private fun YAMLElementGenerator.createPortsSpecification(newPortSetting: String)  =
        PsiTreeUtil.findChildOfType(createDummyYamlWithText("ports:\n\t- $newPortSetting"), YAMLKeyValue::class.java)!!

    override fun invoke(project: Project, file: PsiFile, serviceConfig: PsiElement, _ignored : PsiElement) {
        if (serviceConfig !is ServiceConfig) return

        val elementGenerator = YAMLElementGenerator.getInstance(project)
        val portsSpec = elementGenerator.createPortsSpecification(newPortSetting)

        when (val value = serviceConfig.value) {
            null -> serviceConfig.setValue(portsSpec.parentMapping!!)
            is YAMLMapping -> when(value.getKeyValueByKey("ports")){
                null -> value.putKeyValue(portsSpec)
                else -> value.getKeyValueByKey("ports")!!.setValue(portsSpec.value!!)
            }

            // the value is something other than a YAMLMapping, so we can't insert a key/value pair...
            // but let the user figure that out! If it's a problem they'll just hit undo
            else -> value.addAfter(portsSpec, null)
        }
    }
}