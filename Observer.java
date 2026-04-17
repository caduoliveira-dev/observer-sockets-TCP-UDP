/*
 * Observer.java — Define a interface que todo observador deve seguir.
 * Quem implementar essa interface precisa ter o método atualizar(),
 * que será chamado quando houver uma mudança no estado do leilão.
 *
 * Adaptação em relação ao Observer.ts original: em vez de receber
 * (nome, valor) — que só cabiam no caso da estação meteorológica —,
 * aqui recebemos (evento, dados). 'evento' identifica o tipo de
 * notificação (ITEM, LANCE, FIM, ERRO) e 'dados' é a carga útil
 * serializada em uma String. Isso permite que o mesmo método sirva
 * para cadastro de item, novos lances e encerramento.
 */
public interface Observer {
    void atualizar(String evento, String dados);
}