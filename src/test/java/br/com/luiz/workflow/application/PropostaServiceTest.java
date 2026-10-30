package br.com.luiz.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.luiz.workflow.adapters.out.memory.PropostaRepositoryEmMemoria;
import br.com.luiz.workflow.domain.Proposta;
import br.com.luiz.workflow.domain.StatusProposta;
import br.com.luiz.workflow.domain.TransicaoInvalidaException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PropostaServiceTest {

    private final RepositorioComOperacaoConcorrente repository = new RepositorioComOperacaoConcorrente();
    private final PropostaService service = new PropostaService(repository);

    @Test
    void criaPropostaEmRascunhoEPersiste() {
        Proposta criada = service.criar(new BigDecimal("5000"), 12);

        assertThat(criada.status()).isEqualTo(StatusProposta.RASCUNHO);
        assertThat(service.buscar(criada.id())).isPresent();
    }

    @Test
    void aprovaPropostaEmAnalise() {
        Proposta p = service.criar(new BigDecimal("5000"), 12);
        service.enviarParaAnalise(p.id());

        Proposta aprovada = service.aprovar(p.id());

        assertThat(aprovada.status()).isEqualTo(StatusProposta.APROVADA);
    }

    @Test
    void aprovarSemPassarPorAnaliseFalha() {
        Proposta p = service.criar(new BigDecimal("5000"), 12);

        assertThatThrownBy(() -> service.aprovar(p.id()))
            .isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void cancelaPropostaComMotivo() {
        Proposta p = service.criar(new BigDecimal("5000"), 12);

        Proposta cancelada = service.cancelar(p.id(), "Cliente desistiu");

        assertThat(cancelada.status()).isEqualTo(StatusProposta.CANCELADA);
        assertThat(cancelada.motivo()).isEqualTo("Cliente desistiu");
    }

    @Test
    void idInexistenteLancaNaoEncontrada() {
        assertThatThrownBy(() -> service.aprovar(UUID.randomUUID()))
            .isInstanceOf(PropostaNaoEncontradaException.class);
    }

    @Test
    void listaTodasAsPropostas() {
        service.criar(new BigDecimal("1000"), 6);
        service.criar(new BigDecimal("2000"), 12);

        assertThat(service.listar()).hasSize(2);
    }

    // ===== lock otimista =====

    @Test
    void cadaOperacaoDevolveAPropostaComAVersaoGravada() {
        Proposta criada = service.criar(new BigDecimal("5000"), 12);
        Proposta emAnalise = service.enviarParaAnalise(criada.id());
        Proposta aprovada = service.aprovar(criada.id());

        // O service devolve o RETORNO do salvar (com a versão nova), não o objeto que leu.
        assertThat(criada.versao()).isEqualTo(0L);
        assertThat(emAnalise.versao()).isEqualTo(1L);
        assertThat(aprovada.versao()).isEqualTo(2L);
    }

    @Test
    void alteracaoConcorrenteEntreBuscarESalvarEhRecusada() {
        UUID id = service.criar(new BigDecimal("5000"), 12).id(); // versão 0
        service.enviarParaAnalise(id);                             // versão 1

        // O analista clica "aprovar". Depois que o service leu a proposta (versão 1)
        // e antes de salvar, o cliente cancela pelo app: outra requisição, que grava a versão 2.
        repository.depoisDaProximaBusca(() -> service.cancelar(id, "Cliente desistiu"));

        assertThatThrownBy(() -> service.aprovar(id))
            .isInstanceOf(PropostaDesatualizadaException.class)
            .hasMessageContaining(id.toString());

        // Vence quem gravou primeiro: a aprovação não apagou o cancelamento.
        Proposta gravada = service.buscar(id).orElseThrow();
        assertThat(gravada.status()).isEqualTo(StatusProposta.CANCELADA);
        assertThat(gravada.motivo()).isEqualTo("Cliente desistiu");
        assertThat(gravada.versao()).isEqualTo(2L);
    }

    /**
     * Repositório em memória com um gancho de teste: logo depois de entregar a próxima leitura,
     * executa uma "outra requisição" sobre a mesma proposta. Reproduz, sem threads e sem sleep,
     * a intercalação A lê → B lê → B grava → A tenta gravar.
     */
    private static final class RepositorioComOperacaoConcorrente extends PropostaRepositoryEmMemoria {

        private Runnable operacaoConcorrente;

        void depoisDaProximaBusca(Runnable operacao) {
            this.operacaoConcorrente = operacao;
        }

        @Override
        public synchronized Optional<Proposta> buscarPorId(UUID id) {
            Optional<Proposta> lida = super.buscarPorId(id);
            Runnable operacao = operacaoConcorrente;
            operacaoConcorrente = null; // zera ANTES de rodar: a outra requisição também busca
            if (operacao != null) {
                operacao.run();
            }
            return lida;
        }
    }
}