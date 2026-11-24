package br.com.luiz.workflow.adapters.in.graphql;

import br.com.luiz.workflow.application.ContratoService;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class ContratoGraphQlController {

    private final ContratoService service;

    public ContratoGraphQlController(ContratoService service) {
        this.service = service;
    }

    @QueryMapping
    public ContratoPayload contrato(@Argument UUID id) {
        return service.buscar(id).map(ContratoPayload::de).orElse(null);
    }

    @MutationMapping
    public ContratoPayload gerarContrato(@Argument UUID propostaId) {
        return ContratoPayload.de(service.gerarPara(propostaId));
    }

    @MutationMapping
    public ContratoPayload assinarContrato(@Argument UUID id) {
        return ContratoPayload.de(service.assinar(id));
    }

    @MutationMapping
    public ContratoPayload ativarContrato(@Argument UUID id) {
        return ContratoPayload.de(service.ativar(id));
    }

    @MutationMapping
    public ContratoPayload liquidarContrato(@Argument UUID id) {
        return ContratoPayload.de(service.liquidar(id));
    }

    @MutationMapping
    public ContratoPayload cancelarContrato(@Argument UUID id, @Argument String motivo) {
        return ContratoPayload.de(service.cancelar(id, motivo));
    }
}
