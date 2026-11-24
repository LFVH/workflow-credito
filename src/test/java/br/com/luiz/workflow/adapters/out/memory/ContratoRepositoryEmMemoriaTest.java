package br.com.luiz.workflow.adapters.out.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.luiz.workflow.application.ContratoDesatualizadoException;
import br.com.luiz.workflow.domain.Contrato;
import br.com.luiz.workflow.domain.StatusContrato;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contrato do fake: ele precisa se comportar como o banco no que importa para os testes
 * do service (cópias, versão e um contrato por proposta).
 */
class ContratoRepositoryEmMemoriaTest {

    private final ContratoRepositoryEmMemoria repository = new ContratoRepositoryEmMemoria();

    @Test
    void contratoNovoEhGravadoComVersaoZero() {
        Contrato salvo = repository.salvar(contratoNovo());

        assertThat(salvo.versao()).isEqualTo(0L);
        assertThat(repository.buscarPorId(salvo.id()).orElseThrow().versao()).isEqualTo(0L);
    }

    @Test
    void cadaSalvarIncrementaAVersaoEACopiaLidaNaoMudaOGuardado() {
        Contrato salvo = repository.salvar(contratoNovo());

        repository.buscarPorId(salvo.id()).orElseThrow().assinar(); // alterou a cópia e NÃO salvou
        assertThat(repository.buscarPorId(salvo.id()).orElseThrow().status()).isEqualTo(StatusContrato.GERADO);

        salvo.assinar(); // continua com o objeto DEVOLVIDO pelo salvar
        Contrato assinado = repository.salvar(salvo);

        assertThat(assinado.versao()).isEqualTo(1L);
        assertThat(assinado.status()).isEqualTo(StatusContrato.ASSINADO);
    }

    @Test
    void duasCopiasDaMesmaVersaoSoAPrimeiraGrava() {
        UUID id = repository.salvar(contratoNovo()).id();
        Contrato daMesa = repository.buscarPorId(id).orElseThrow();
        Contrato doBackoffice = repository.buscarPorId(id).orElseThrow();

        daMesa.assinar();
        repository.salvar(daMesa);
        doBackoffice.cancelar("Documentação divergente");

        assertThatThrownBy(() -> repository.salvar(doBackoffice))
            .isInstanceOf(ContratoDesatualizadoException.class)
            .hasMessage("Contrato foi alterado por outra operação: " + id);
        assertThat(repository.buscarPorId(id).orElseThrow().status()).isEqualTo(StatusContrato.ASSINADO);
    }

    @Test
    void segundoContratoParaAMesmaPropostaEhRecusado() {
        UUID propostaId = UUID.randomUUID();
        repository.salvar(contratoNovo(propostaId));

        // Outro contrato (outro id) para a mesma proposta: o UNIQUE (proposta_id) da V5.
        assertThatThrownBy(() -> repository.salvar(contratoNovo(propostaId)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(propostaId.toString());
    }

    @Test
    void buscaPorUmaPropostaEPorVarias() {
        UUID comContrato1 = UUID.randomUUID();
        UUID comContrato2 = UUID.randomUUID();
        UUID semContrato = UUID.randomUUID();
        repository.salvar(contratoNovo(comContrato1));
        repository.salvar(contratoNovo(comContrato2));

        assertThat(repository.buscarPorPropostaId(comContrato1)).isPresent();
        assertThat(repository.buscarPorPropostaId(semContrato)).isEmpty();
        assertThat(repository.buscarPorPropostaIds(List.of(comContrato1, comContrato2, semContrato)))
            .extracting(Contrato::propostaId)
            .containsExactlyInAnyOrder(comContrato1, comContrato2);
    }

    private static Contrato contratoNovo() {
        return contratoNovo(UUID.randomUUID());
    }

    /** Contrato ainda não gravado (versão null) para a proposta informada. */
    private static Contrato contratoNovo(UUID propostaId) {
        return Contrato.reconstituir(UUID.randomUUID(), propostaId, new BigDecimal("15000.00"), 36,
            StatusContrato.GERADO, null, null);
    }
}
