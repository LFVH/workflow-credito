package br.com.luiz.workflow.adapters.in.rest;

import br.com.luiz.workflow.application.PropostaService;
import br.com.luiz.workflow.domain.Proposta;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Adaptador de entrada REST. Só traduz HTTP para os casos de uso do {@link PropostaService}
 * e o resultado de volta para HTTP; nenhuma regra de negócio mora aqui.
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
}
