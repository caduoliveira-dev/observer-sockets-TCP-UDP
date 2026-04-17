/*
 * ClienteHandler.java — É um ConcreteObserver, análogo ao Mobile.ts/Painel.ts.
 * Cada cliente conectado via TCP tem seu próprio ClienteHandler rodando
 * em uma thread separada. Essa classe tem dois papéis:
 *
 *   1) Implementa Observer: quando o ServidorLeilao chama notificar(),
 *      o método atualizar() desta classe envia a mensagem pelo socket
 *      até o cliente remoto.
 *
 *   2) Implementa Runnable: fica lendo comandos que o cliente envia
 *      (LOGIN, LANCE) e dispara as operações correspondentes no servidor.
 *
 * O protocolo textual é simples: uma linha por mensagem, no formato
 * "EVENTO:dados". Exemplos: "LOGIN:João", "LANCE:150.00",
 * "ITEM:PS5|Console novo|1000.0", "FIM:Maria|2500.0".
 */
import java.io.*;
import java.net.*;

public class ClienteHandler implements Observer, Runnable {

    private final Socket socket;
    private final ServidorLeilao servidor;
    private final PrintWriter out;
    private final BufferedReader in;
    private final Object escritaLock = new Object();

    private String nome = "Anônimo";
    private volatile boolean ativo = true;

    public ClienteHandler(Socket socket, ServidorLeilao servidor) throws IOException {
        this.socket = socket;
        this.servidor = servidor;
        this.out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        this.in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), "UTF-8"));
    }

    // --------- Observer ---------
    @Override
    public void atualizar(String evento, String dados) {
        if (!ativo) return;
        synchronized (escritaLock) {
            out.println(evento + ":" + dados);
        }
    }

    // --------- Runnable (thread de leitura do cliente) ---------
    @Override
    public void run() {
        try {
            // registra este handler como observer do servidor
            servidor.registrar(this);

            // ao conectar, manda o estado atual para o cliente recém-chegado
            ItemLeilao item = servidor.getItem();
            if (item != null) {
                atualizar("ITEM",
                        item.getNome() + "|" + item.getDescricao()
                                + "|" + item.getLanceMinimo());
                Lance l = servidor.getLanceAtual();
                if (l != null) atualizar("LANCE", l.getAutor() + "|" + l.getValor());
                if (!servidor.isLeilaoAtivo()) {
                    String vencedor = (l != null) ? l.getAutor() : "Nenhum";
                    double valor    = (l != null) ? l.getValor() : 0;
                    atualizar("FIM", vencedor + "|" + valor);
                }
            }

            String linha;
            while (ativo && (linha = in.readLine()) != null) {
                processar(linha.trim());
            }
        } catch (IOException e) {
            // conexão perdida — tratamento no finally
        } finally {
            ativo = false;
            servidor.remover(this);
            try { socket.close(); } catch (IOException ignore) {}
        }
    }

    private void processar(String msg) {
        if (msg.startsWith("LOGIN:")) {
            String n = msg.substring("LOGIN:".length()).trim();
            this.nome = n.isEmpty() ? "Anônimo" : n;
            synchronized (escritaLock) {
                out.println("OK:Bem-vindo, " + nome);
            }
        } else if (msg.startsWith("LANCE:")) {
            try {
                String valorStr = msg.substring("LANCE:".length()).trim().replace(',', '.');
                double valor = Double.parseDouble(valorStr);
                boolean aceito = servidor.receberLance(nome, valor);
                if (!aceito) {
                    synchronized (escritaLock) {
                        out.println("ERRO:Lance invalido. Deve ser maior que o lance atual e o leilao deve estar ativo.");
                    }
                }
            } catch (NumberFormatException e) {
                synchronized (escritaLock) {
                    out.println("ERRO:Valor numerico invalido.");
                }
            }
        }
    }

    public String getNome() { return nome; }
}