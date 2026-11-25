package br.com.luiz.workflow.config;

import br.com.luiz.workflow.application.ContratoService;
import br.com.luiz.workflow.application.PropostaService;
import br.com.luiz.workflow.application.port.out.ContratoRepository;
import br.com.luiz.workflow.application.port.out.PropostaRepository;
import br.com.luiz.workflow.application.port.out.PublicadorDeEventos;
import br.com.luiz.workflow.application.port.out.Transacao;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeansConfig {

    @Bean
    PropostaService propostaService(PropostaRepository repository, PublicadorDeEventos publicador,
                                    Transacao transacao) {
        return new PropostaService(repository, publicador, transacao);
    }

    @Bean
    ContratoService contratoService(ContratoRepository contratos, PropostaRepository propostas) {
        return new ContratoService(contratos, propostas);
    }
}
