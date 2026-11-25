package br.com.luiz.workflow.application;

import br.com.luiz.workflow.application.port.out.PublicadorDeEventos;
import br.com.luiz.workflow.domain.MudancaDeStatus;
import java.util.ArrayList;
import java.util.List;

/** Fake do port: guarda, em ordem, tudo o que foi publicado. */
public class PublicadorDeEventosEmMemoria implements PublicadorDeEventos {

    private final List<MudancaDeStatus> publicados = new ArrayList<>();

    @Override
    public void publicar(List<MudancaDeStatus> eventos) {
        publicados.addAll(eventos);
    }

    /** Cópia do que foi publicado até agora, na ordem. */
    public List<MudancaDeStatus> publicados() {
        return List.copyOf(publicados);
    }
}
