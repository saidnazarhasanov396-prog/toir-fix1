package com.toir.entity;

/**
 * Kind of maintenance/repair work. Covers all 11 types from ТЗ §4.2.4:
 * ТО, осмотры, диагностика, текущий/средний/капитальный ремонт,
 * профилактика, сезонные, метрологические, электротехнические, КИПиА.
 */
public enum MaintenanceKind {
    /** Профилактическое ТО (регламентное). */
    PREVENTIVE,
    /** Диагностика / предиктивное обслуживание. */
    PREDICTIVE,
    /** Обслуживание по состоянию. */
    CONDITION_BASED,
    /** Осмотр. */
    INSPECTION,
    /** Диагностические работы. */
    DIAGNOSTIC,
    /** Текущий ремонт. */
    CURRENT_REPAIR,
    /** Средний ремонт. */
    MEDIUM_REPAIR,
    /** Капитальный ремонт. */
    OVERHAUL,
    /** Сезонные работы. */
    SEASONAL,
    /** Метрологические работы. */
    METROLOGICAL,
    /** Электротехнические работы. */
    ELECTRICAL,
    /** Работы КИПиА (контрольно-измерительные приборы и автоматика). */
    INSTRUMENTATION
}
