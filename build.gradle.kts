// Top-level build file where you can add configuration options common to all sub-projects/modules.
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.dagger.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.gms.google.services) apply false
    alias(libs.plugins.google.firebase.crashlytics) apply false
    alias(libs.plugins.google.firebase.perf) apply false
}

tasks.register("ciTest") {
    dependsOn(":app:testDebugUnitTest", ":app:connectedDebugAndroidTest")
}

tasks.register("verifyWarningBudget") {
    group = "verification"
    description = "Fails build when warning counts exceed the agreed baseline budget."
    dependsOn(":app:lintDebug")

    val lintReportPath = "app/build/reports/lint-results-debug.xml"
    val warningSummaryPath = "app/build/reports/warnings/warning-budget-summary.md"
    inputs.file(file(lintReportPath)).optional()
    outputs.file(file(warningSummaryPath))

    doLast {
        val lintReport = file(lintReportPath)
        if (!lintReport.exists()) {
            throw GradleException(
                "Missing lint report at ${lintReport.path}. Run :app:lintDebug before verifyWarningBudget.",
            )
        }

        // Current clean lint baseline. Fail only when a change increases the total;
        // keep the per-rule breakdown in the generated report for targeted cleanup.
        val warningBaseline = 403
        val warningCountsByIssueId = linkedMapOf<String, Int>()

        val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            setExpandEntityReferences(false)
        }
        val documentBuilder = documentBuilderFactory.newDocumentBuilder()
        val document = documentBuilder.parse(lintReport)
        val issueNodes = document.getElementsByTagName("issue")

        var warningCount = 0
        for (index in 0 until issueNodes.length) {
            val issue = issueNodes.item(index)
            val severity = issue.attributes?.getNamedItem("severity")?.nodeValue
            if (severity != "Warning") continue

            val issueId = issue.attributes?.getNamedItem("id")?.nodeValue ?: continue
            warningCount += 1
            warningCountsByIssueId[issueId] = warningCountsByIssueId.getOrDefault(issueId, 0) + 1
        }

        val reportDir = file("app/build/reports/warnings")
        reportDir.mkdirs()
        val summaryFile = File(reportDir, "warning-budget-summary.md")
        summaryFile.writeText(
            buildString {
                appendLine("# Warning Budget Report")
                appendLine()
                appendLine("- Baseline: $warningBaseline")
                appendLine("- Current: $warningCount")
                appendLine("- Delta: ${warningCount - warningBaseline}")
                appendLine()
                appendLine("## Warnings by lint rule")
                appendLine()
                appendLine("| Rule | Count |")
                appendLine("|---|---:|")
                warningCountsByIssueId
                    .toList()
                    .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
                    .forEach { (issueId, count) -> appendLine("| $issueId | $count |") }
            },
        )

        val budgetExceeded = warningCount > warningBaseline

        if (budgetExceeded) {
            throw GradleException(
                "Warning budget exceeded: $warningCount > baseline $warningBaseline. " +
                    "See ${summaryFile.path} for details.",
            )
        }
    }
}
