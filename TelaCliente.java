/*
 * TelaCliente.java — Interface gráfica (Swing) do comprador.
 * Permite conectar ao servidor, acompanhar em tempo real o item em
 * leilão e o lance atual, e enviar novos lances. Quando o leilão é
 * encerrado, mostra o vencedor.
 */
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

public class TelaCliente extends JFrame {

    private ClienteLeilao cliente;

    private JTextField txtHost, txtPorta, txtNome, txtLance;
    private JButton btnConectar, btnEnviar;
    private JTextArea txtLog;
    private JLabel lblItem, lblDescricao, lblLanceMinimo, lblLanceAtual, lblStatus;

    public TelaCliente() {
        setTitle("Cliente de Leilão");
        setSize(600, 640);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        montarGUI();

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (cliente != null) cliente.desconectar();
            }
        });
    }

    private void montarGUI() {
        // Painel 1 — conexão
        JPanel conn = new JPanel(new GridBagLayout());
        conn.setBorder(new TitledBorder("1. Conectar ao servidor"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;

        g.gridx = 0; g.gridy = 0;
        conn.add(new JLabel("Servidor:"), g);
        g.gridx = 1; txtHost = new JTextField("localhost", 14); conn.add(txtHost, g);
        g.gridx = 2; conn.add(new JLabel("Porta:"), g);
        g.gridx = 3; txtPorta = new JTextField("5555", 5); conn.add(txtPorta, g);

        g.gridx = 0; g.gridy = 1;
        conn.add(new JLabel("Seu nome:"), g);
        g.gridx = 1; g.gridwidth = 3; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
        txtNome = new JTextField(); conn.add(txtNome, g);

        g.gridx = 1; g.gridy = 2; g.gridwidth = 3;
        g.fill = GridBagConstraints.NONE; g.weightx = 0;
        g.anchor = GridBagConstraints.EAST;
        btnConectar = new JButton("Conectar");
        conn.add(btnConectar, g);

        // Painel 2 — informações do leilão (painel de monitoramento)
        JPanel info = new JPanel(new GridLayout(0, 1, 2, 2));
        info.setBorder(new TitledBorder("2. Leilão em tempo real"));
        lblItem = new JLabel("Item: (aguardando cadastro)");
        lblDescricao = new JLabel("Descrição: -");
        lblLanceMinimo = new JLabel("Lance mínimo: -");
        lblLanceAtual = new JLabel("Lance atual: -");
        lblLanceAtual.setFont(lblLanceAtual.getFont().deriveFont(Font.BOLD, 18f));
        lblLanceAtual.setForeground(new Color(0, 102, 0));
        lblStatus = new JLabel("Status: desconectado");
        info.add(lblItem);
        info.add(lblDescricao);
        info.add(lblLanceMinimo);
        info.add(lblLanceAtual);
        info.add(lblStatus);

        // Painel 3 — lance
        JPanel lance = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        lance.setBorder(new TitledBorder("3. Dar um lance"));
        lance.add(new JLabel("Valor R$:"));
        txtLance = new JTextField(10);
        lance.add(txtLance);
        btnEnviar = new JButton("Enviar lance");
        btnEnviar.setEnabled(false);
        lance.add(btnEnviar);

        // Painel 4 — histórico / mensagens
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(new TitledBorder("Histórico de mensagens"));
        txtLog = new JTextArea(12, 40);
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logPanel.add(new JScrollPane(txtLog), BorderLayout.CENTER);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(conn);
        north.add(info);
        north.add(lance);

        setLayout(new BorderLayout());
        add(north, BorderLayout.NORTH);
        add(logPanel, BorderLayout.CENTER);

        btnConectar.addActionListener(e -> conectar());
        btnEnviar.addActionListener(e -> enviarLance());
        txtLance.addActionListener(e -> enviarLance());
    }

    private void conectar() {
        String nome = txtNome.getText().trim();
        if (nome.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Informe seu nome.");
            return;
        }
        try {
            String host = txtHost.getText().trim();
            int porta = Integer.parseInt(txtPorta.getText().trim());

            cliente = new ClienteLeilao(host, porta, nome,
                    new ClienteLeilao.MensagemListener() {
                        @Override public void onMensagem(String evento, String dados) {
                            SwingUtilities.invokeLater(
                                    () -> processarMensagem(evento, dados));
                        }
                        @Override public void onDesconectado() {
                            SwingUtilities.invokeLater(() -> {
                                lblStatus.setText("Status: desconectado");
                                btnEnviar.setEnabled(false);
                                log("** Conexão encerrada.");
                            });
                        }
                    });
            cliente.conectar();

            btnConectar.setEnabled(false);
            txtHost.setEnabled(false);
            txtPorta.setEnabled(false);
            txtNome.setEnabled(false);
            btnEnviar.setEnabled(true);
            lblStatus.setText("Status: conectado como " + nome);
            log("** Conectado a " + host + ":" + porta + " como " + nome);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao conectar: " + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void processarMensagem(String evento, String dados) {
        String[] p = dados.split("\\|", -1);
        switch (evento) {
            case "ITEM":
                if (p.length >= 3) {
                    lblItem.setText("Item: " + p[0]);
                    lblDescricao.setText("Descrição: " + p[1]);
                    lblLanceMinimo.setText("Lance mínimo: R$ " + formatar(p[2]));
                    lblLanceAtual.setText("Lance atual: (nenhum ainda)");
                    log(">> Novo item em leilão: " + p[0]);
                }
                break;
            case "LANCE":
                if (p.length >= 2) {
                    lblLanceAtual.setText(String.format(
                            "Lance atual: R$ %s  (por %s)", formatar(p[1]), p[0]));
                    log(">> Lance de " + p[0] + ": R$ " + formatar(p[1]));
                }
                break;
            case "FIM":
                if (p.length >= 2) {
                    lblStatus.setText("Status: LEILÃO ENCERRADO");
                    btnEnviar.setEnabled(false);
                    log("** LEILÃO ENCERRADO **");
                    log("   Vencedor: " + p[0]);
                    log("   Valor final: R$ " + formatar(p[1]));
                    JOptionPane.showMessageDialog(this,
                            "Leilão encerrado!\n\nVencedor: " + p[0]
                                    + "\nValor pago: R$ " + formatar(p[1]),
                            "Fim do leilão", JOptionPane.INFORMATION_MESSAGE);
                }
                break;
            case "ERRO":
                log("!! ERRO: " + dados);
                JOptionPane.showMessageDialog(this, dados, "Erro",
                        JOptionPane.WARNING_MESSAGE);
                break;
            case "OK":
                log(dados);
                break;
            default:
                log(evento + ": " + dados);
        }
    }

    private void enviarLance() {
        if (cliente == null) return;
        try {
            double v = Double.parseDouble(
                    txtLance.getText().trim().replace(',', '.'));
            cliente.enviarLance(v);
            txtLance.setText("");
            log("<< Enviando lance: R$ " + String.format("%.2f", v));
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Valor inválido.",
                    "Erro", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Formata um número (String) para ter 2 casas decimais quando possível. */
    private String formatar(String v) {
        try { return String.format("%.2f", Double.parseDouble(v)); }
        catch (Exception e) { return v; }
    }

    private void log(String msg) {
        txtLog.append("[" + java.time.LocalTime.now().withNano(0) + "] " + msg + "\n");
        txtLog.setCaretPosition(txtLog.getDocument().getLength());
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignore) {}
        SwingUtilities.invokeLater(() -> new TelaCliente().setVisible(true));
    }
}