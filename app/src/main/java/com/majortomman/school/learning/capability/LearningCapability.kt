package com.majortomman.school.learning.capability

enum class NumberDomain {
    NOT_APPLICABLE,
    NATURAL,
    INTEGER,
    RATIONAL,
    REAL,
    COMPLEX,
}

enum class ExtensionPolicy {
    NONE,
    NECESSARY_ONLY,
    OPTIONAL,
}

enum class ContentOrigin {
    TEXTBOOK_QUOTE,
    TEXTBOOK_SUMMARY,
    SCHOOL_EXPLANATION,
    OPTIONAL_EXTENSION,
    SIMULATION_DESCRIPTION,
}

enum class ConceptId {
    VARIABLE,
    CORRESPONDING_VALUE,
    FUNCTION,
    COORDINATE_POINT,
    FUNCTION_GRAPH,
    REAL_PART,
    IMAGINARY_PART,
    COMPLEX_PLANE,
    SPACE_POINT,
    SPACE_PROJECTION,
    EXACT_NUMBER,
    RATIONAL_NUMBER,
    RADICAL,
    PI_CONSTANT,
    SCIENTIFIC_NOTATION,
    POLYNOMIAL,
    FACTORIZATION,
    EQUATION,
    INEQUALITY,
    DISCRIMINANT,
    FUNCTION_DOMAIN,
    VECTOR,
    DOT_PRODUCT,
    CROSS_PRODUCT,
    LINE,
    CIRCLE,
    PLANE,
    GEOMETRIC_TRANSFORMATION,
    PROOF_STEP,
    PHYSICAL_QUANTITY,
    PHYSICAL_MODEL,
    MODEL_ASSUMPTION,
    UNIT,
    DIMENSION,
    SIGNIFICANT_FIGURES,
    MEASUREMENT_UNCERTAINTY,
    ELECTRIC_CURRENT,
    VOLTAGE,
    RESISTANCE,
    CIRCUIT_TOPOLOGY,
    SERIES_CIRCUIT,
    PARALLEL_CIRCUIT,
    KIRCHHOFF_CURRENT_LAW,
    KIRCHHOFF_VOLTAGE_LAW,
    ELECTRIC_POWER,
    ELECTRIC_ENERGY,
    JOULE_HEAT,
    AMMETER,
    VOLTMETER,
    CAPACITOR,
    INDUCTOR,
    DIODE,
    CHEMICAL_FORMULA,
    CHEMICAL_EQUATION,
    ATOM_COUNT,
    MASS_CONSERVATION,
    CELL_STRUCTURE,
    BIOLOGICAL_PROCESS,
    WORD_MEANING,
    WORD_FORM,
    SENTENCE_STRUCTURE,
    SENTENCE_ORDER,
    DIALOGUE_CONTEXT,
    LISTENING_COMPREHENSION,
    PRONUNCIATION,
    JAPANESE_READING,
    JAPANESE_PARTICLE,
    JAPANESE_CONJUGATION,
    SPEECH_REGISTER,
}

enum class OperationId {
    SUBSTITUTE,
    SOLVE_RELATION,
    VERIFY_EQUALITY,
    PARSE_EXPRESSION,
    SIMPLIFY_EXPRESSION,
    APPROXIMATE_EXACT_VALUE,
    ADD_POLYNOMIAL,
    MULTIPLY_POLYNOMIAL,
    DIFFERENTIATE_POLYNOMIAL,
    SOLVE_LINEAR_EQUATION,
    SOLVE_QUADRATIC_EQUATION,
    SOLVE_LINEAR_INEQUALITY,
    CHECK_FUNCTION_DOMAIN,
    COMPUTE_VECTOR,
    INTERSECT_GEOMETRY,
    TRANSFORM_GEOMETRY,
    VALIDATE_PROOF_STRUCTURE,
    VALIDATE_MODEL_CONDITIONS,
    CONVERT_UNIT,
    CHECK_DIMENSION,
    ROUND_SIGNIFICANT_FIGURES,
    CHECK_CIRCUIT_TOPOLOGY,
    SOLVE_DC_CIRCUIT,
    COMPUTE_EQUIVALENT_RESISTANCE,
    COMPUTE_ELECTRICAL_POWER,
    SIMULATE_SWITCH,
    PLOT_2D,
    PLOT_COMPLEX,
    PROJECT_3D,
    COUNT_ATOMS,
    BALANCE_EQUATION,
    LABEL_DIAGRAM,
    NORMALIZE_LANGUAGE_ANSWER,
    VERIFY_WORD_FORM,
    ORDER_SENTENCE,
    VERIFY_PARTICLE,
    VERIFY_CONJUGATION,
    SWITCH_READING,
    PLAY_AUDIO,
}

enum class WidgetType {
    RELATION_CALCULATOR,
    FORMULA_VERIFIER,
    EXACT_EXPRESSION,
    UNIT_CONVERTER,
    DIMENSION_CHECKER,
    POLYNOMIAL_WORKBENCH,
    EQUATION_SOLVER,
    VECTOR_GEOMETRY,
    PROOF_STEPS,
    CIRCUIT_EDITOR,
    CIRCUIT_SOLVER,
    ELECTRICAL_POWER,
    COORDINATE_GRAPH_2D,
    COMPLEX_PLANE,
    COORDINATE_3D,
    CHEMICAL_EQUATION,
    BIOLOGY_DIAGRAM,
    LANGUAGE_SENTENCE,
    LANGUAGE_DIALOGUE,
    JAPANESE_READING,
    LISTENING,
}

data class LessonCapability(
    val allowedConcepts: Set<ConceptId>,
    val enabledOperations: Set<OperationId>,
    val enabledWidgets: Set<WidgetType>,
    val numberDomain: NumberDomain,
    val extensionPolicy: ExtensionPolicy = ExtensionPolicy.NECESSARY_ONLY,
) {
    fun allows(concept: ConceptId): Boolean = concept in allowedConcepts

    fun allows(operation: OperationId): Boolean = operation in enabledOperations

    fun allows(widget: WidgetType): Boolean = widget in enabledWidgets

    fun validate(
        requestedConcepts: Set<ConceptId> = emptySet(),
        requestedOperations: Set<OperationId> = emptySet(),
        requestedWidgets: Set<WidgetType> = emptySet(),
    ): CapabilityValidation {
        val blockedConcepts = requestedConcepts - allowedConcepts
        val blockedOperations = requestedOperations - enabledOperations
        val blockedWidgets = requestedWidgets - enabledWidgets
        return CapabilityValidation(
            allowed = blockedConcepts.isEmpty() && blockedOperations.isEmpty() && blockedWidgets.isEmpty(),
            blockedConcepts = blockedConcepts,
            blockedOperations = blockedOperations,
            blockedWidgets = blockedWidgets,
        )
    }
}

data class CapabilityValidation(
    val allowed: Boolean,
    val blockedConcepts: Set<ConceptId>,
    val blockedOperations: Set<OperationId>,
    val blockedWidgets: Set<WidgetType>,
)
