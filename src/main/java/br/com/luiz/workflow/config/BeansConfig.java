package br.com.luiz.workflow.config;

import br.com.luiz.workflow.application.PropostaService;
import br.com.luiz.workflow.application.port.out.PropostaRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeansConfig {
    @Bean
    PropostaService propostaService(PropostaRepository repository) {
        return new PropostaService(repository);
    }
}