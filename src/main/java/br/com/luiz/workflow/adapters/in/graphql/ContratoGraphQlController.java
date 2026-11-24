package br.com.luiz.workflow.adapters.in.graphql;

import br.com.luiz.workflow.application.ContratoService;
import br.com.luiz.workflow.domain.Contrato;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
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

    /**
     * Campo Proposta.contrato EM LOTE. O Spring for GraphQL registra este método como um
     * DataLoader: em vez de resolver o campo proposta a proposta, ele junta as propostas da
     * resposta e chama este método UMA vez. Uma chamada ao service, um SELECT ... IN.
     * Retorno em Map: proposta que não está no mapa recebe null (= ainda sem contrato).
     * typeName explícito: o Spring deduziria "PropostaPayload" pelo tipo dos elementos da lista.
     */
    @BatchMapping(typeName = "Proposta", field = "contrato")
    public Map<PropostaPayload, ContratoPayload> contratoDaProposta(List<PropostaPayload> propostas) {
        List<UUID> ids = propostas.stream().map(PropostaPayload::id).toList();

        Map<UUID, ContratoPayload> porPropostaId = service.buscarPorPropostas(ids).stream()
            .collect(Collectors.toMap(Contrato::propostaId, ContratoPayload::de));

        return propostas.stream()
            .filter(p -> porPropostaId.containsKey(p.id())) // toMap não aceita valor null
            .collect(Collectors.toMap(Function.identity(), p -> porPropostaId.get(p.id())));
    }
}
