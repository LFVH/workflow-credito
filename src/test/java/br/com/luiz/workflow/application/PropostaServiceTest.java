package br.com.luiz.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.luiz.workflow.adapters.out.memory.PropostaRepositoryEmMemoria;
import br.com.luiz.workflow.domain.Proposta;
import br.com.luiz.workflow.domain.StatusProposta;
import br.com.luiz.workflow.domain.TransicaoInvalidaException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    // ===== paginação keyset =====

    @Test
    void listaNaOrdemDeCriacaoRespeitandoOLimite() {
        List<UUID> criadas = criarVarias(3);

        assertThat(ids(service.listar(null, 2))).containsExactly(criadas.get(0), criadas.get(1));
    }

    @Test
    void semLimiteUsaOLimitePadrao() {
        List<UUID> criadas = criarVarias(PropostaService.LIMITE_PADRAO + 5);

        assertThat(ids(service.listar(null, null)))
            .containsExactlyElementsOf(criadas.subList(0, PropostaService.LIMITE_PADRAO));
    }

    @Test
    void limiteMaximoEhAceito() {
        criarVarias(PropostaService.LIMITE_MAXIMO + 1);

        assertThat(service.listar(null, PropostaService.LIMITE_MAXIMO))
            .hasSize(PropostaService.LIMITE_MAXIMO);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    void limiteForaDoIntervaloEhRecusado(int limite) {
        assertThatThrownBy(() -> service.listar(null, limite))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Limite deve estar entre 1 e 100");
    }

    @Test
    void cursorDevolveAsPropostasCriadasDepoisDele() {
        List<UUID> criadas = criarVarias(5);

        // O cursor é a 2ª; a página começa na 3ª (o próprio cursor não volta).
        assertThat(ids(service.listar(criadas.get(1), 2)))
            .containsExactly(criadas.get(2), criadas.get(3));
    }

    @Test
    void percorrerTodasAsPaginasNaoRepeteNemPulaProposta() {
        List<UUID> criadas = criarVarias(7);
        List<UUID> vistas = new ArrayList<>();
        List<Integer> tamanhos = new ArrayList<>();

        UUID cursor = null;
        for (int volta = 0; volta < 10; volta++) { // teto: um cursor ignorado faria o loop girar para sempre
            List<Proposta> pagina = service.listar(cursor, 3);
            if (pagina.isEmpty()) {
                break;
            }
            tamanhos.add(pagina.size());
            vistas.addAll(ids(pagina));
            cursor = pagina.getLast().id(); // o cursor é o id da ÚLTIMA da página
        }

        assertThat(tamanhos).containsExactly(3, 3, 1);
        assertThat(vistas).containsExactlyElementsOf(criadas);
    }

    @Test
    void cursorDesconhecidoDevolveListaVazia() {
        criarVarias(3);

        assertThat(service.listar(UUID.randomUUID(), 10)).isEmpty();
    }

    @Test
    void mudarDeStatusNaoMudaAPosicaoNaLista() {
        List<UUID> criadas = criarVarias(3);
        service.enviarParaAnalise(criadas.get(0)); // a primeira é gravada de novo

        // No banco, criada_em não muda no UPDATE: a primeira continua primeira.
        assertThat(ids(service.listar(null, 10))).containsExactlyElementsOf(criadas);
    }

    // ===== apoio =====

    /** Cria propostas em sequência e devolve os ids na ordem de criação. */
    private List<UUID> criarVarias(int quantidade) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            ids.add(service.criar(new BigDecimal("1000"), 12).id());
        }
        return ids;
    }

    private static List<UUID> ids(List<Proposta> propostas) {
        return propostas.stream().map(Proposta::id).toList();
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
