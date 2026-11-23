package br.com.luiz.workflow.adapters.in.rest;

import br.com.luiz.workflow.application.PropostaNaoEncontradaException;
import br.com.luiz.workflow.application.PropostaService;
import br.com.luiz.workflow.domain.Proposta;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Adaptador de entrada REST. Só traduz HTTP para os casos de uso do {@link PropostaService}
 * e o resultado de volta para HTTP; nenhuma regra de negócio mora aqui.
 *
 * <p>Transições são ações (POST em um substantivo sob o recurso), nunca PATCH do campo status:
 * cada ação preserva a intenção, as exigências próprias (motivo) e pode ser autorizada à parte.
 */
@RestController
@RequestMapping(PropostaRestController.BASE)
public class PropostaRestController {

    static final String BASE = "/api/propostas";

    private final PropostaService service;

    public PropostaRestController(PropostaService service) {
        this.service = service;
    }

    /** 201 Created + Location: o cliente sabe onde a proposta nova mora sem montar URL. */
    @PostMapping
    public ResponseEntity<PropostaResponse> criar(@RequestBody CriarPropostaRequest corpo,
                                                  UriComponentsBuilder uri) {
        Proposta criada = service.criar(corpo.valor(), corpo.prazoMeses());
        URI local = uri.path(BASE + "/{id}").buildAndExpand(criada.id()).toUri();
        return ResponseEntity.created(local).body(PropostaResponse.de(criada));
    }

    @GetMapping("/{id}")
    public PropostaResponse buscar(@PathVariable UUID id) {
        return service.buscar(id)
            .map(PropostaResponse::de)
            .orElseThrow(() -> new PropostaNaoEncontradaException(id));
    }

    /**
     * Uma página por cursor (item 05). Os dois parâmetros são opcionais: sem limite, o service
     * aplica o padrão; sem cursor, é a primeira página. Lista vazia = não há mais propostas.
     */
    @GetMapping
    public List<PropostaResponse> listar(@RequestParam(required = false) UUID depoisDe,
                                         @RequestParam(required = false) Integer limite) {
        return service.listar(depoisDe, limite).stream().map(PropostaResponse::de).toList();
    }

    @PostMapping("/{id}/envio-para-analise")
    public PropostaResponse enviarParaAnalise(@PathVariable UUID id) {
        return PropostaResponse.de(service.enviarParaAnalise(id));
    }

    @PostMapping("/{id}/pedido-de-documentos")
    public PropostaResponse pedirDocumentos(@PathVariable UUID id) {
        return PropostaResponse.de(service.pedirDocumentos(id));
    }

    @PostMapping("/{id}/aprovacao")
    public PropostaResponse aprovar(@PathVariable UUID id) {
        return PropostaResponse.de(service.aprovar(id));
    }

    @PostMapping("/{id}/reprovacao")
    public PropostaResponse reprovar(@PathVariable UUID id, @RequestBody MotivoRequest corpo) {
        return PropostaResponse.de(service.reprovar(id, corpo.motivo()));
    }

    @PostMapping("/{id}/cancelamento")
    public PropostaResponse cancelar(@PathVariable UUID id, @RequestBody MotivoRequest corpo) {
        return PropostaResponse.de(service.cancelar(id, corpo.motivo()));
    }
}
