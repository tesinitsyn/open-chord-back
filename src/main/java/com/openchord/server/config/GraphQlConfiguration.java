package com.openchord.server.config;

import graphql.scalars.ExtendedScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Registers the non-default scalar implementations declared by the public GraphQL schema.
 */
@Configuration
public class GraphQlConfiguration {
    @Bean
    RuntimeWiringConfigurer scalarWiring() {
        return builder -> builder.scalar(ExtendedScalars.GraphQLLong).scalar(ExtendedScalars.DateTime);
    }
}
