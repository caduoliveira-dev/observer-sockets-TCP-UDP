/*
 * TelaServidor.java — Interface gráfica (Swing) do servidor de leilão.
 * Permite iniciar o servidor em uma porta, cadastrar o item a ser
 * leiloado, acompanhar os lances recebidos em tempo real, ver quantos
 * participantes estão conectados e encerrar o leilão (gravando o
 * histórico em arquivo).
 */
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

public class TelaServidor extends JFrame {

    private ServidorLeilao servidor;

    private JTextField txtPorta;
    private JTextField txtNome, txtDescricao, txtLanceMinimo;
    private JButton btnIniciar, btnCadastrar, btnEncerrar;
    private JTextArea txtLog;
    private JLabel lblStatus, lblItem, lblLanceAtual, lblParticipantes;

    public TelaServidor() {
        setTitle("Servidor de Leilão");
        setSize(680, 640);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        montarGUI();

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (servidor != null) servidor.parar();
            }
        });
    }

    private void montarGUI() {
        // Painel 1 — iniciar servidor
        JPanel topo = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        topo.setBorder(new TitledBorder("1. Iniciar servidor"));
        topo.add(new JLabel("Porta TCP:"));
        txtPorta = new JTextField("5555", 6);
        topo.add(txtPorta);
        btnIniciar = new JButton("Iniciar servidor");
        topo.add(btnIniciar);

        // Painel 2 — cadastrar item
        JPanel itemPanel = new JPanel(new GridBagLayout());
        itemPanel.setBorder(new TitledBorder("2. Cadastrar item de leilão"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0; g.gridy = 0;
        itemPanel.add(new JLabel("Nome:"), g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        txtNome = new JTextField(20); itemPanel.add(txtNome, g);

        g.gridx = 0; g.gridy = 1; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        itemPanel.add(new JLabel("Descrição:"), g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        txtDescricao = new JTextField(20); itemPanel.add(txtDescricao, g);

        g.gridx = 0; g.gridy = 2; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        itemPanel.add(new JLabel("Lance mínimo (R$):"), g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        txtLanceMinimo = new JTextField(10); itemPanel.add(txtLanceMinimo, g);

        g.gridx = 1; g.gridy = 3; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        g.anchor = GridBagConstraints.EAST;
        btnCadastrar = new JButton("Cadastrar item / Iniciar leilão");
        btnCadastrar.setEnabled(false);
        itemPanel.add(btnCadastrar, g);

        // Painel 3 — estado do leilão
        JPanel statusPanel = new JPanel(new GridLayout(0, 1, 4, 4));
        statusPanel.setBorder(new TitledBorder("3. Estado do leilão"));
        lblStatus = new JLabel("Servidor: parado");
        lblItem = new JLabel("Item: -");
        lblLanceAtual = new JLabel("Lance atual: -");
        lblLanceAtual.setFont(lblLanceAtual.getFont().deriveFont(Font.BOLD, 14f));
        lblParticipantes = new JLabel("Participantes conectados: 0");
        statusPanel.add(lblStatus);
        statusPanel.add(lblItem);
        statusPanel.add(lblLanceAtual);
        statusPanel.add(lblParticipantes);
        btnEncerrar = new JButton("Encerrar leilão");
        btnEncerrar.setEnabled(false);
        statusPanel.add(btnEncerrar);

        // Painel 4 — log
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(new TitledBorder("Log do servidor"));
        txtLog = new JTextArea(12, 50);
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logPanel.add(new JScrollPane(txtLog), BorderLayout.CENTER);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(topo);
        north.add(itemPanel);
        north.add(statusPanel);

        setLayout(new BorderLayout());
        add(north, BorderLayout.NORTH);
        add(logPanel, BorderLayout.CENTER);

        btnIniciar.addActionListener(e -> iniciarServidor());
        btnCadastrar.addActionListener(e -> cadastrarItem());
        btnEncerrar.addActionListener(e -> encerrarLeilao());
    }

    private void iniciarServidor() {
        try {
            int porta = Integer.parseInt(txtPorta.getText().trim());
            servidor = new ServidorLeilao(porta);
            servidor.setLogListener(new ServidorLeilao.LogListener() {
                @Override public void onLog(String m) {
                    // Atualizações de GUI sempre na EDT
                    SwingUtilities.invokeLater(() -> adicionarLog(m));
                }
                @Override public void onEstadoMudou() {
                    SwingUtilities.invokeLater(() -> atualizarStatus());
                }
            });
            servidor.iniciar();

            btnIniciar.setEnabled(false);
            txtPorta.setEnabled(false);
            btnCadastrar.setEnabled(true);
            lblStatus.setText("Servidor: rodando na porta " + porta);
            adicionarLog("Servidor pronto para receber conexões.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao iniciar: " + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cadastrarItem() {
        if (servidor == null) return;
        try {
            String nome = txtNome.getText().trim();
            String desc = txtDescricao.getText().trim();
            if (nome.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Informe o nome do item.");
                return;
            }
            // Evita quebrar o protocolo (| é separador)
            if (nome.contains("|") || desc.contains("|")) {
                JOptionPane.showMessageDialog(this, "Não use o caractere '|' nos campos.");
                return;
            }
            double min = Double.parseDouble(
                    txtLanceMinimo.getText().trim().replace(',', '.'));
            ItemLeilao item = new ItemLeilao(nome, desc, min);
            servidor.cadastrarItem(item);
            btnEncerrar.setEnabled(true);
            btnCadastrar.setText("Cadastrar novo item (substitui o atual)");
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Lance mínimo inválido.",
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void encerrarLeilao() {
        if (servidor == null) return;
        int r = JOptionPane.showConfirmDialog(this,
                "Deseja realmente encerrar o leilão?",
                "Confirmar", JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION) {
            servidor.encerrarLeilao();
            btnEncerrar.setEnabled(false);
        }
    }

    private void atualizarStatus() {
        if (servidor == null) return;
        ItemLeilao i = servidor.getItem();
        if (i != null) {
            lblItem.setText(String.format(
                    "Item: %s (mínimo R$ %.2f) — %s",
                    i.getNome(), i.getLanceMinimo(),
                    servidor.isLeilaoAtivo() ? "LEILÃO ATIVO" : "ENCERRADO"));
        }
        Lance l = servidor.getLanceAtual();
        if (l != null) {
            lblLanceAtual.setText(String.format(
                    "Lance atual: R$ %.2f  (por %s)", l.getValor(), l.getAutor()));
        } else {
            lblLanceAtual.setText("Lance atual: -");
        }
        lblParticipantes.setText("Participantes conectados: " + servidor.getNumObservers());
    }

    private void adicionarLog(String msg) {
        txtLog.append("[" + java.time.LocalTime.now().withNano(0) + "] " + msg + "\n");
        txtLog.setCaretPosition(txtLog.getDocument().getLength());
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignore) {}
        SwingUtilities.invokeLater(() -> new TelaServidor().setVisible(true));
    }
}