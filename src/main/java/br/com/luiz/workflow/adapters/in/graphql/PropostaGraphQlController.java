package br.com.luiz.workflow.adapters.in.graphql;

import br.com.luiz.workflow.application.PropostaService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class PropostaGraphQlController {

    private final PropostaService service;

    public PropostaGraphQlController(PropostaService service) {
        this.service = service;
    }

    @QueryMapping
    public PropostaPayload proposta(@Argument UUID id) {
        return service.buscar(id).map(PropostaPayload::de).orElse(null);
    }

    @QueryMapping
    public List<PropostaPayload> propostas() {
        return service.listar().stream().map(PropostaPayload::de).toList();
    }

    @MutationMapping
    public PropostaPayload criarProposta(@Argument BigDecimal valor, @Argument int prazoMeses) {
        return PropostaPayload.de(service.criar(valor, prazoMeses));
    }

    @MutationMapping
    public PropostaPayload enviarParaAnalise(@Argument UUID id) {
        return PropostaPayload.de(service.enviarParaAnalise(id));
    }

    @MutationMapping
    public PropostaPayload pedirDocumentos(@Argument UUID id) {
        return PropostaPayload.de(service.pedirDocumentos(id));
    }

    @MutationMapping
    public PropostaPayload aprovarProposta(@Argument UUID id) {
        return PropostaPayload.de(service.aprovar(id));
    }

    @MutationMapping
    public PropostaPayload reprovarProposta(@Argument UUID id, @Argument String motivo) {
        return PropostaPayload.de(service.reprovar(id, motivo));
    }

    @MutationMapping
    public PropostaPayload cancelarProposta(@Argument UUID id, @Argument String motivo) {
        return PropostaPayload.de(service.cancelar(id, motivo));
    }
}