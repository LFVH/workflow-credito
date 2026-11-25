package br.com.luiz.workflow.adapters.out.persistence;

import br.com.luiz.workflow.application.port.out.Transacao;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Adaptador do port Transacao com o TransactionTemplate do Spring (transação programática).
 * PROPAGATION_REQUIRED (o padrão): se já houver transação nesta thread, participa dela.
 * RuntimeException (ou Error) dentro do trabalho: rollback, e a mesma exceção sai daqui.
 */
@Component
class TransacaoSpring implements Transacao {

    private final TransactionTemplate template;

    TransacaoSpring(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T executar(Supplier<T> trabalho) {
        return template.execute(status -> trabalho.get());
    }
}
