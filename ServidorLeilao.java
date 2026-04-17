/*
 * ServidorLeilao.java — É o ConcreteSubject (implementação concreta de Observable).
 * Equivale ao EstacaoMeteorologica.ts: mantém o estado (item atual, lance atual,
 * histórico de lances), uma lista de observers (os clientes conectados),
 * e os métodos que mudam o estado (cadastrarItem, receberLance, encerrarLeilao).
 *
 * Cada mudança relevante chama notificar(), que percorre a lista e chama
 * atualizar() em cada observer registrado, exatamente como no exemplo da estação.
 *
 * Esta classe também é responsável pela parte de rede: abre um ServerSocket TCP
 * e, para cada cliente que conecta, cria um ClienteHandler rodando em uma thread
 * separada. O ClienteHandler é o próprio observer daquele cliente.
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class ServidorLeilao implements Observable {

    /** Callback para a GUI do servidor acompanhar o que está acontecendo. */
    public interface LogListener {
        void onLog(String mensagem);
        void onEstadoMudou();
    }

    private final List<Observer> observers = new ArrayList<>();
    private ItemLeilao item;
    private Lance lanceAtual;
    private final List<Lance> historicoLances = new ArrayList<>();
    private boolean leilaoAtivo = false;

    private final int porta;
    private ServerSocket serverSocket;
    private Thread threadAccept;

    private LogListener logListener;

    public ServidorLeilao(int porta) {
        this.porta = porta;
    }

    public void setLogListener(LogListener l) { this.logListener = l; }

    private void log(String msg) {
        if (logListener != null) logListener.onLog(msg);
    }

    private void notificarEstadoMudou() {
        if (logListener != null) logListener.onEstadoMudou();
    }

    // ------------------------------------------------------------------
    //  Interface Observable
    // ------------------------------------------------------------------

    @Override
    public synchronized void registrar(Observer o) {
        observers.add(o);
        log("Observer registrado. Total: " + observers.size());
        notificarEstadoMudou();
    }

    @Override
    public synchronized void remover(Observer o) {
        if (observers.remove(o)) {
            log("Observer removido. Total: " + observers.size());
            notificarEstadoMudou();
        }
    }

    @Override
    public void notificar(String evento, String dados) {
        // Copiamos a lista dentro do lock e liberamos o lock antes de escrever
        // nos sockets; assim um cliente lento não trava os outros.
        List<Observer> copia;
        synchronized (this) {
            copia = new ArrayList<>(observers);
        }
        for (Observer o : copia) {
            try {
                o.atualizar(evento, dados);
            } catch (Exception e) {
                log("Erro ao notificar observer: " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    //  Regras de negócio do leilão
    // ------------------------------------------------------------------

    /** Cadastra um item e abre o leilão. */
    public void cadastrarItem(ItemLeilao novoItem) {
        String dados;
        synchronized (this) {
            this.item = novoItem;
            this.lanceAtual = null;
            this.historicoLances.clear();
            this.leilaoAtivo = true;
            dados = novoItem.getNome() + "|" + novoItem.getDescricao()
                    + "|" + novoItem.getLanceMinimo();
        }
        log("Item cadastrado: " + novoItem.getNome()
                + " (mínimo R$ " + novoItem.getLanceMinimo() + ")");
        notificar("ITEM", dados);
        notificarEstadoMudou();
    }

    /**
     * Recebe um lance. Retorna true se foi aceito (maior que o atual
     * e leilão ativo), false caso contrário.
     */
    public boolean receberLance(String autor, double valor) {
        Lance novo;
        synchronized (this) {
            if (!leilaoAtivo || item == null) return false;
            double minimo = (lanceAtual != null)
                    ? lanceAtual.getValor()
                    : item.getLanceMinimo();
            if (valor <= minimo) return false;

            novo = new Lance(autor, valor);
            this.lanceAtual = novo;
            historicoLances.add(novo);
        }
        log("Lance de " + autor + ": R$ " + valor);
        notificar("LANCE", autor + "|" + valor);
        notificarEstadoMudou();
        return true;
    }

    /** Encerra o leilão e grava o histórico em arquivo. */
    public void encerrarLeilao() {
        String dadosFim;
        boolean jaEncerrado;
        synchronized (this) {
            jaEncerrado = !leilaoAtivo;
            leilaoAtivo = false;
            String vencedor = (lanceAtual != null) ? lanceAtual.getAutor() : "Nenhum";
            double valor = (lanceAtual != null) ? lanceAtual.getValor() : 0;
            dadosFim = vencedor + "|" + valor;
        }
        if (jaEncerrado) return;
        log("Leilão encerrado. Resultado: " + dadosFim.replace("|", " com R$ "));
        notificar("FIM", dadosFim);
        salvarHistorico();
        notificarEstadoMudou();
    }

    /** Escreve um relatório completo do leilão em arquivo texto. */
    private synchronized void salvarHistorico() {
        if (item == null) return;
        String nomeArquivo = "historico_leilao_" + System.currentTimeMillis() + ".txt";
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(nomeArquivo), "UTF-8"))) {

            pw.println("=========================================================");
            pw.println("             HISTÓRICO DE LEILÃO");
            pw.println("=========================================================");
            pw.println("Item ............. " + item.getNome());
            pw.println("Descrição ........ " + item.getDescricao());
            pw.printf ("Lance mínimo ..... R$ %.2f%n", item.getLanceMinimo());
            pw.println();
            pw.println("--- LANCES (ordem cronológica) ---");
            if (historicoLances.isEmpty()) {
                pw.println("(nenhum lance foi efetuado)");
            } else {
                for (Lance l : historicoLances) pw.println(l);
            }
            pw.println();
            pw.println("--- RESULTADO FINAL ---");
            if (lanceAtual != null) {
                pw.println("Vencedor ......... " + lanceAtual.getAutor());
                pw.printf ("Valor pago ....... R$ %.2f%n", lanceAtual.getValor());
            } else {
                pw.println("Leilão encerrado sem lances.");
            }
            log("Histórico salvo em: " + new File(nomeArquivo).getAbsolutePath());
        } catch (IOException e) {
            log("Erro ao salvar histórico: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    //  Rede — TCP Sockets com threads
    // ------------------------------------------------------------------

    public void iniciar() throws IOException {
        serverSocket = new ServerSocket(porta);
        log("Servidor TCP ouvindo na porta " + porta);

        threadAccept = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket clienteSocket = serverSocket.accept();
                    log("Nova conexão: " + clienteSocket.getInetAddress());
                    // Cada cliente em sua própria thread, atendendo
                    // conexões concorrentes conforme exigido.
                    ClienteHandler handler = new ClienteHandler(clienteSocket, this);
                    Thread t = new Thread(handler);
                    t.setDaemon(true);
                    t.start();
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        log("Erro ao aceitar conexão: " + e.getMessage());
                    }
                }
            }
        }, "AcceptThread");
        threadAccept.setDaemon(true);
        threadAccept.start();
    }

    public void parar() {
        try { if (serverSocket != null) serverSocket.close(); }
        catch (IOException ignore) {}
    }

    // ------------------------------------------------------------------
    //  Getters usados pela GUI e pelos handlers
    // ------------------------------------------------------------------

    public synchronized ItemLeilao getItem()        { return item; }
    public synchronized Lance getLanceAtual()       { return lanceAtual; }
    public synchronized boolean isLeilaoAtivo()     { return leilaoAtivo; }
    public synchronized int getNumObservers()       { return observers.size(); }
}