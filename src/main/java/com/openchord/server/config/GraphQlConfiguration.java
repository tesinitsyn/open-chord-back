package com.openchord.server.config;

import graphql.scalars.ExtendedScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

@Configuration
/** Registers scalar support that is shared by the GraphQL schema and Java transport records. */
public class GraphQlConfiguration {
    /** Adds long integers and offset date-times to the GraphQL runtime. */
    @Bean
    RuntimeWiringConfigurer scalarWiring() {
        return builder -> builder.scalar(ExtendedScalars.GraphQLLong).scalar(ExtendedScalars.DateTime);
    }
}
