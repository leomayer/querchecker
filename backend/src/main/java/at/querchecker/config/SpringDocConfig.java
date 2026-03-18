package at.querchecker.config;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.converters.PolymorphicModelConverter;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@Configuration
public class SpringDocConfig {

    /**
     * Replaces the default PolymorphicModelConverter with a cycle-breaking pass-through.
     *
     * Problem: springdoc 2.8.6 + Spring Boot 3.5.x infinitely recurses via
     * ModelResolver → Jackson AnnotatedClassResolver when introspecting self-referential
     * DTOs (WhCategoryDto.children: List<WhCategoryDto>).
     *
     * Fix: track in-progress type resolutions per thread. If a type is seen again
     * while already being resolved, return a $ref immediately instead of recursing.
     */
    @Bean
    public PolymorphicModelConverter polymorphicModelConverter(ObjectMapperProvider objectMapperProvider) {
        ThreadLocal<Set<Type>> resolving = ThreadLocal.withInitial(HashSet::new);
        return new PolymorphicModelConverter(objectMapperProvider) {
            @Override
            public Schema<?> resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
                Type rawType = type.getType();
                Set<Type> current = resolving.get();
                if (!current.add(rawType)) {
                    // Already resolving this type — return $ref to break the cycle
                    if (rawType instanceof Class<?> cls) {
                        return new Schema<>().$ref("#/components/schemas/" + cls.getSimpleName());
                    }
                    return null;
                }
                try {
                    return chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
                } finally {
                    current.remove(rawType);
                }
            }
        };
    }
}
