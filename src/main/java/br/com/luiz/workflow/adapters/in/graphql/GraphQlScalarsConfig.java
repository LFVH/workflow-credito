package br.com.luiz.workflow.adapters.in.graphql;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

@Configuration
class GraphQlScalarsConfig {

    @Bean
    RuntimeWiringConfigurer scalars() {
        return wiring -> wiring.scalar(DecimalScalar.INSTANCE);
    }
}
