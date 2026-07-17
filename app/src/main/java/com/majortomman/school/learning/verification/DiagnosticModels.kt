package com.majortomman.school.learning.verification

enum class VerificationStatus {
    INPUT_IN_PROGRESS,
    READY,
    CORRECT,
    INCORRECT,
    UNSUPPORTED,
}

enum class ErrorType {
    CALCULATION,
    SUBSTITUTION,
    SYMBOL,
    UNIT,
    FORMULA_SELECTION,
    DOMAIN,
    CHEMICAL_SUBSCRIPT,
    CHEMICAL_COEFFICIENT,
    EXPERIMENT_ORDER,
    DIAGRAM_LABEL,
    CONCEPT_CONFUSION,
}

data class DiagnosticStep(
    val title: String,
    val expression: String,
    val correct: Boolean? = null,
)

data class DiagnosticResult(
    val status: VerificationStatus,
    val normalizedAnswer: String? = null,
    val steps: List<DiagnosticStep> = emptyList(),
    val errorType: ErrorType? = null,
    val message: String,
)
