package com.b2bfintech.app.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fails the build if any {@code @RestController} endpoint method lacks an explicit {@code @PreAuthorize}
 * (either on the method or on the declaring controller) or still references legacy shared client roles.
 */
class EndpointGuardCoverageTest {

    @Test
    void everyControllerEndpointHasExplicitPreAuthorizeAndNoLegacyClientRoles() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> unguarded = new ArrayList<>();
        List<String> legacyRoleUsages = new ArrayList<>();

        for (BeanDefinition candidate : scanner.findCandidateComponents("com.b2bfintech")) {
            Class<?> clazz = Class.forName(candidate.getBeanClassName());
            PreAuthorize classGuard = AnnotatedElementUtils.findMergedAnnotation(clazz, PreAuthorize.class);
            if (classGuard != null && usesLegacyRole(classGuard.value())) {
                legacyRoleUsages.add(clazz.getSimpleName() + ": " + classGuard.value());
            }

            for (Method method : clazz.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                    continue;
                }
                PreAuthorize methodGuard = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
                if (methodGuard == null && classGuard == null) {
                    unguarded.add(clazz.getSimpleName() + "#" + method.getName());
                }
                if (methodGuard != null && usesLegacyRole(methodGuard.value())) {
                    legacyRoleUsages.add(clazz.getSimpleName() + "#" + method.getName() + ": " + methodGuard.value());
                }
            }
        }

        assertTrue(unguarded.isEmpty(), "Controllers with unguarded endpoints: " + unguarded);
        assertTrue(legacyRoleUsages.isEmpty(), "Endpoints still using legacy CLIENT_* roles: " + legacyRoleUsages);
    }

    private static boolean usesLegacyRole(String expression) {
        return expression.contains("CLIENT_ADMIN") || expression.contains("CLIENT_OPERATOR");
    }
}
