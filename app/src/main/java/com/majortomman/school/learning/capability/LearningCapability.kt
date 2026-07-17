package com.majortomman.school.learning.capability

enum class NumberDomain {
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
    CHEMICAL_FORMULA,
    CHEMICAL_EQUATION,
    ATOM_COUNT,
    MASS_CONSERVATION,
    CELL_STRUCTURE,
    BIOLOGICAL_PROCESS,
}

enum class OperationId {
    SUBSTITUTE,
    SOLVE_RELATION,
    VERIFY_EQUALITY,
    PLOT_2D,
    PLOT_COMPLEX,
    PROJECT_3D,
    COUNT_ATOMS,
    BALANCE_EQUATION,
    LABEL_DIAGRAM,
}

enum class WidgetType {
    RELATION_CALCULATOR,
    FORMULA_VERIFIER,
    COORDINATE_GRAPH_2D,
    COMPLEX_PLANE,
    COORDINATE_3D,
    CHEMICAL_EQUATION,
    BIOLOGY_DIAGRAM,
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
