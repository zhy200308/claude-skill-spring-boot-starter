package com.claude.skill.autoconfigure;

import com.claude.skill.config.ClaudeProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;

@AutoConfiguration
@ConditionalOnClass(name = "org.springdoc.core.utils.SpringDocUtils")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnExpression("'${springdoc.api-docs.enabled:true}' == 'true' and '${claude.web.enabled:true}' == 'true' and '${claude.web.springdoc.enabled:true}' == 'true' and '${springdoc.group-configs[0].group:}' == ''")
public class ClaudeSpringdocAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "claudeSkillGroupedOpenApi")
    public Object claudeSkillGroupedOpenApi(ClaudeProperties properties) {
        String basePath = normalizeBasePath(properties.getWeb().getBasePath());
        String packageToScan = properties.getWeb().getSpringdoc().getPackageToScan();
        String group = properties.getWeb().getSpringdoc().getGroup();
        String pathsToMatch = "/".equals(basePath) ? "/**" : basePath + "/**";
        try {
            Class<?> groupedOpenApiClass = resolveGroupedOpenApiClass();
            Method builderMethod = groupedOpenApiClass.getMethod("builder");
            Object builder = builderMethod.invoke(null);
            Method groupMethod = builder.getClass().getMethod("group", String.class);
            Method pathsToMatchMethod = builder.getClass().getMethod("pathsToMatch", String[].class);
            Method packagesToScanMethod = builder.getClass().getMethod("packagesToScan", String[].class);
            Method buildMethod = builder.getClass().getMethod("build");
            builder = groupMethod.invoke(builder, group);
            builder = pathsToMatchMethod.invoke(builder, (Object) new String[]{pathsToMatch});
            if (packageToScan != null && !packageToScan.isBlank()) {
                builder = packagesToScanMethod.invoke(builder, (Object) new String[]{packageToScan});
            }
            return buildMethod.invoke(builder);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create springdoc GroupedOpenApi bean", ex);
        }
    }

    private Class<?> resolveGroupedOpenApiClass() {
        ClassLoader classLoader = ClaudeSpringdocAutoConfiguration.class.getClassLoader();
        try {
            return ClassUtils.forName("org.springdoc.core.models.GroupedOpenApi", classLoader);
        } catch (ClassNotFoundException ex) {
            try {
                return ClassUtils.forName("org.springdoc.core.GroupedOpenApi", classLoader);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("springdoc GroupedOpenApi class not found");
            }
        }
    }

    private String normalizeBasePath(String basePath) {
        if (basePath == null || basePath.isBlank()) {
            return "/claude";
        }
        String normalized = basePath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
