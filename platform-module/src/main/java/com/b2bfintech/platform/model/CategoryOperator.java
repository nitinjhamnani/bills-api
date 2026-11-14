package com.b2bfintech.platform.model;

import java.util.UUID;

/** One operator inside a category. Parameters stay empty until that operator is selected. */
public record CategoryOperator(
        UUID id,
        String operatorName,
        String operatorId,
        String provider,
        String location,
        String operatorParams,
        UUID categoryId,
        boolean active
) {
}
