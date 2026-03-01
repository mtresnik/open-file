package org.open.file

import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtensionContext

class SkipIfGitHub : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext?): ConditionEvaluationResult {
        val runnerEnvironment = System.getenv("RUNNER_ENVIRONMENT")
        if ("github-hosted".equals(runnerEnvironment, ignoreCase = true)) {
            return ConditionEvaluationResult.disabled("Skipping test on GitHub runner")
        }
        return ConditionEvaluationResult.enabled("Enabled on non-GitHub runners")
    }
}