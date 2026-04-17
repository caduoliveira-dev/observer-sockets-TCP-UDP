/*
 * ClienteLeilao.java — Lógica de rede do lado do cliente (comprador).
 * Abre e mantém a conexão TCP com o servidor, envia comandos
 * (LOGIN, LANCE) e repassa mensagens recebidas para um listener
 * (que será a GUI do cliente, TelaCliente).
 */
import java.io.*;
import java.net.*;

public class ClienteLeilao {

    public interface MensagemListener {
        void onMensagem(String evento, String dados);
        void onDesconectado();
    }

    private final String host;
    private final int porta;
    private final String nome;
    private final MensagemListener listener;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread threadLeitura;
    private volatile boolean ativo = false;

    public ClienteLeilao(String host, int porta, String nome, MensagemListener listener) {
        this.host = host;
        this.porta = porta;
        this.nome = nome;
        this.listener = listener;
    }

    public void conectar() throws IOException {
        socket = new Socket(host, porta);
        out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), "UTF-8"));
        ativo = true;

        // Envia identificação
        out.println("LOGIN:" + nome);

        // Thread que lê mensagens do servidor em background
        threadLeitura = new Thread(() -> {
            try {
                String linha;
                while (ativo && (linha = in.readLine()) != null) {
                    int idx = linha.indexOf(':');
                    if (idx >= 0) {
                        String evento = linha.substring(0, idx);
                        String dados  = linha.substring(idx + 1);
                        listener.onMensagem(evento, dados);
                    } else {
                        listener.onMensagem("INFO", linha);
                    }
                }
            } catch (IOException ignore) {
            } finally {
                ativo = false;
                listener.onDesconectado();
            }
        }, "ClienteLeituraThread");
        threadLeitura.setDaemon(true);
        threadLeitura.start();
    }

    public void enviarLance(double valor) {
        if (!ativo) return;
        out.println("LANCE:" + valor);
    }

    public void desconectar() {
        ativo = false;
        try { if (socket != null) socket.close(); }
        catch (IOException ignore) {}
    }
}