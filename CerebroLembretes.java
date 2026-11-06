import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;

/**
 * Cérebro de Lembretes
 * Clique em uma região do cérebro e adicione lembretes para ela.
 * Os dados ficam salvos em ~/cerebro_lembretes.txt
 */
public class CerebroLembretes extends JFrame {

    // ====== Regiões (módulos) do cérebro ======
    enum Regiao {
        FRONTAL("Lobo Frontal", "Frontal", new Color(0xF4A261),
                "Planejamento, decisões, foco e controle de impulsos.",
                "Metas, decisões e tarefas que exigem concentração."),
        PARIETAL("Lobo Parietal", "Parietal", new Color(0x8AB17D),
                "Integra sentidos, noção de espaço, números e orientação.",
                "Estudos com números, lugares e coisas para organizar."),
        TEMPORAL("Lobo Temporal", "Temporal", new Color(0x6C9BD2),
                "Audição, linguagem, música e memória de longo prazo.",
                "Coisas para ouvir, idiomas, músicas e conversas."),
        OCCIPITAL("Lobo Occipital", "Occipital", new Color(0xB392AC),
                "Processamento da visão.",
                "Coisas para ver, assistir ou ler."),
        CEREBELO("Cerebelo", "Cerebelo", new Color(0x4DB6AC),
                "Coordenação motora, equilíbrio e aprendizado de habilidades.",
                "Treinos, hábitos e prática de habilidades."),
        TRONCO("Tronco Encefálico", "Tronco", new Color(0xC9A18B),
                "Respiração, batimentos cardíacos, sono e funções vitais.",
                "Rotina básica: sono, água, alimentação e saúde."),
        HIPOCAMPO("Hipocampo", "Hipocampo", new Color(0xE76F8B),
                "Formação de novas memórias e aprendizado.",
                "Revisões, coisas para memorizar e não esquecer."),
        AMIGDALA("Amígdala", "Amígdala", new Color(0xE5533D),
                "Emoções, medo, prazer e memória emocional.",
                "Pessoas, sentimentos e assuntos que mexem com você.");

        final String nome, curto, funcao, dica;
        final Color cor;

        Regiao(String nome, String curto, Color cor, String funcao, String dica) {
            this.nome = nome;
            this.curto = curto;
            this.cor = cor;
            this.funcao = funcao;
            this.dica = dica;
        }
    }

    // ====== Modelo de lembrete ======
    enum Prioridade {
        ALTA("Alta", new Color(0xE63946)),
        MEDIA("Média", new Color(0xF4A261)),
        BAIXA("Baixa", new Color(0x4DB6AC));

        final String nome;
        final Color cor;
        final String hex;

        Prioridade(String nome, Color cor) {
            this.nome = nome;
            this.cor = cor;
            this.hex = String.format("#%06X", cor.getRGB() & 0xFFFFFF);
        }

        @Override
        public String toString() {
            return nome;
        }
    }

    static class Lembrete {
        String texto;
        LocalDateTime quando; // pode ser null
        Prioridade prioridade;
        boolean feito;
        boolean avisado;

        Lembrete(String texto, LocalDateTime quando, Prioridade prioridade) {
            this.texto = texto;
            this.quando = quando;
            this.prioridade = prioridade;
        }
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Path ARQUIVO = Paths.get(System.getProperty("user.home"), "cerebro_lembretes.txt");
    private static final Color FUNDO = new Color(0x14182B);
    private static final Color FUNDO_LADO = new Color(0x1E2338);
    private static final Color TEXTO = new Color(0xE8EAF6);

    private final Map<Regiao, List<Lembrete>> dados = new EnumMap<>(Regiao.class);
    private final Map<Regiao, Color> cores = new EnumMap<>(Regiao.class);
    private Regiao selecionada = null;

    private PainelCerebro painel;
    private JLabel titulo, dicaLabel, statusLabel;
    private JTextArea descricao;
    private DefaultListModel<Lembrete> modelo = new DefaultListModel<>();
    private JList<Lembrete> lista;
    private JTextField campoTexto, campoData;
    private JComboBox<Prioridade> comboPrioridade;

    public CerebroLembretes() {
        super("Cérebro de Lembretes");
        for (Regiao r : Regiao.values()) {
            dados.put(r, new ArrayList<>());
            cores.put(r, r.cor);
        }
        carregar();
        montarTela();

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1120, 720);
        setMinimumSize(new Dimension(900, 560));
        setLocationRelativeTo(null);

        // verifica alarmes a cada 15 segundos
        javax.swing.Timer timer = new javax.swing.Timer(15000, e -> verificarAlarmes());
        timer.setInitialDelay(1500);
        timer.start();
    }

    // ====== Interface ======
    private void montarTela() {
        painel = new PainelCerebro();

        JPanel lado = new JPanel(new BorderLayout(8, 8));
        lado.setBackground(FUNDO_LADO);
        lado.setBorder(new EmptyBorder(14, 14, 14, 14));
        lado.setPreferredSize(new Dimension(360, 600));

        // Cabeçalho
        JPanel cab = new JPanel();
        cab.setOpaque(false);
        cab.setLayout(new BoxLayout(cab, BoxLayout.Y_AXIS));
        titulo = new JLabel("Escolha uma região");
        titulo.setFont(new Font("SansSerif", Font.BOLD, 22));
        titulo.setForeground(TEXTO);
        descricao = new JTextArea("Clique em uma parte do cérebro ao lado para ver seus lembretes.");
        descricao.setLineWrap(true);
        descricao.setWrapStyleWord(true);
        descricao.setEditable(false);
        descricao.setOpaque(false);
        descricao.setForeground(TEXTO);
        descricao.setFont(new Font("SansSerif", Font.PLAIN, 13));
        descricao.setBorder(new EmptyBorder(4, 0, 4, 0));
        dicaLabel = new JLabel(" ");
        dicaLabel.setForeground(new Color(0x9FA8DA));
        dicaLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        for (JComponent c : new JComponent[]{titulo, descricao, dicaLabel}) c.setAlignmentX(Component.LEFT_ALIGNMENT);
        cab.add(titulo);
        cab.add(descricao);
        cab.add(dicaLabel);
        lado.add(cab, BorderLayout.NORTH);

        // Lista de lembretes
        lista = new JList<>(modelo);
        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lista.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                JLabel c = (JLabel) super.getListCellRendererComponent(l, v, i, sel, foc);
                Lembrete lem = (Lembrete) v;
                String t = html(lem.texto);
                String quando = lem.quando == null ? ""
                        : " <font color='#888888'>[" + FMT.format(lem.quando) + "]</font>";
                String bolinha = "<font color='" + lem.prioridade.hex + "'>&#9679;</font> ";
                c.setText("<html>" + bolinha + (lem.feito ? "<strike>" + t + "</strike> &#10004;" : t) + quando + "</html>");
                c.setBorder(new EmptyBorder(5, 8, 5, 8));
                return c;
            }
        });
        lista.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) alternarConcluido();
            }
        });
        JPanel centro = new JPanel(new BorderLayout(0, 6));
        centro.setOpaque(false);
        centro.add(new JScrollPane(lista), BorderLayout.CENTER);
        statusLabel = new JLabel(" ");
        statusLabel.setForeground(new Color(0x9FA8DA));
        centro.add(statusLabel, BorderLayout.SOUTH);
        lado.add(centro, BorderLayout.CENTER);

        // Formulário
        campoTexto = new JTextField();
        campoData = new JTextField();
        campoData.setToolTipText("Ex.: 10/11/2026 14:30 (deixe em branco para lembrete sem horário)");
        ActionListener add = e -> adicionar();
        campoTexto.addActionListener(add);
        campoData.addActionListener(add);

        JButton btnAdd = new JButton("Adicionar lembrete");
        btnAdd.addActionListener(add);
        JButton btnConcluir = new JButton("Concluir / Desfazer");
        btnConcluir.addActionListener(e -> alternarConcluido());
        JButton btnRemover = new JButton("Remover");
        btnRemover.addActionListener(e -> removerSelecionado());
        JButton btnLimpar = new JButton("Limpar concluídos");
        btnLimpar.addActionListener(e -> limparConcluidos());

        JPanel botoes = new JPanel(new GridLayout(1, 3, 6, 0));
        botoes.setOpaque(false);
        botoes.add(btnConcluir);
        botoes.add(btnRemover);
        botoes.add(btnLimpar);

        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        form.setOpaque(false);
        comboPrioridade = new JComboBox<>(Prioridade.values());
        comboPrioridade.setSelectedItem(Prioridade.MEDIA);

        JPanel linha = new JPanel(new BorderLayout(8, 0));
        linha.setOpaque(false);
        linha.add(celula("Data/hora (dd/MM/yyyy HH:mm)", campoData), BorderLayout.CENTER);
        linha.add(celula("Prioridade", comboPrioridade), BorderLayout.EAST);

        JButton btnCor = new JButton("Cor da região...");
        btnCor.addActionListener(e -> escolherCor());
        JButton btnCorPadrao = new JButton("Cor padrão");
        btnCorPadrao.addActionListener(e -> corPadrao());
        JPanel botoesCor = new JPanel(new GridLayout(1, 2, 6, 0));
        botoesCor.setOpaque(false);
        botoesCor.add(btnCor);
        botoesCor.add(btnCorPadrao);

        form.add(rotulo("Novo lembrete"));
        form.add(campoTexto);
        form.add(linha);
        form.add(btnAdd);
        form.add(botoes);
        form.add(botoesCor);
        lado.add(form, BorderLayout.SOUTH);

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.add(painel, BorderLayout.CENTER);
        raiz.add(lado, BorderLayout.EAST);
        setContentPane(raiz);
    }

    private JPanel celula(String texto, JComponent comp) {
        JPanel c = new JPanel(new GridLayout(2, 1, 2, 2));
        c.setOpaque(false);
        c.add(rotulo(texto));
        c.add(comp);
        return c;
    }

    private JLabel rotulo(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(TEXTO);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return l;
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ====== Ações ======
    private void selecionar(Regiao r) {
        selecionada = r;
        titulo.setText(r.nome);
        titulo.setForeground(paraTexto(cor(r)));
        descricao.setText(r.funcao);
        dicaLabel.setText("Sugestão: " + r.dica);
        atualizarLista();
        painel.repaint();
        campoTexto.requestFocusInWindow();
    }

    private void atualizarLista() {
        modelo.clear();
        if (selecionada != null) {
            List<Lembrete> copia = new ArrayList<>(dados.get(selecionada));
            copia.sort(Comparator.comparing((Lembrete l) -> l.feito).thenComparing(l -> l.prioridade));
            for (Lembrete l : copia) modelo.addElement(l);
        }
        int total = 0;
        for (Regiao r : Regiao.values()) total += pendentes(r);
        statusLabel.setText(selecionada == null ? "Pendentes no total: " + total
                : "Pendentes aqui: " + pendentes(selecionada) + "  |  Pendentes no total: " + total);
    }

    private int pendentes(Regiao r) {
        int n = 0;
        for (Lembrete l : dados.get(r)) if (!l.feito) n++;
        return n;
    }

    private void adicionar() {
        if (selecionada == null) {
            JOptionPane.showMessageDialog(this, "Clique primeiro em uma região do cérebro.");
            return;
        }
        String texto = campoTexto.getText().trim();
        if (texto.isEmpty()) return;

        LocalDateTime quando = null;
        String d = campoData.getText().trim();
        if (!d.isEmpty()) {
            try {
                quando = LocalDateTime.parse(d, FMT);
            } catch (DateTimeParseException ex) {
                JOptionPane.showMessageDialog(this,
                        "Data inválida. Use dd/MM/yyyy HH:mm (ex.: 10/11/2026 14:30)\nou deixe em branco.",
                        "Data e hora", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        dados.get(selecionada).add(new Lembrete(texto, quando, (Prioridade) comboPrioridade.getSelectedItem()));
        campoTexto.setText("");
        campoData.setText("");
        comboPrioridade.setSelectedItem(Prioridade.MEDIA);
        salvar();
        atualizarLista();
        painel.repaint();
        campoTexto.requestFocusInWindow();
    }

    private void alternarConcluido() {
        Lembrete l = lista.getSelectedValue();
        if (l == null) return;
        l.feito = !l.feito;
        salvar();
        atualizarLista();
        painel.repaint();
    }

    private void removerSelecionado() {
        Lembrete l = lista.getSelectedValue();
        if (l == null || selecionada == null) return;
        dados.get(selecionada).remove(l);
        salvar();
        atualizarLista();
        painel.repaint();
    }

    private void limparConcluidos() {
        if (selecionada == null) return;
        dados.get(selecionada).removeIf(l -> l.feito);
        salvar();
        atualizarLista();
        painel.repaint();
    }

    private void verificarAlarmes() {
        LocalDateTime agora = LocalDateTime.now();
        StringBuilder sb = new StringBuilder();
        for (Regiao r : Regiao.values()) {
            for (Lembrete l : dados.get(r)) {
                if (!l.feito && !l.avisado && l.quando != null && !l.quando.isAfter(agora)) {
                    l.avisado = true;
                    sb.append("• [").append(r.nome).append("] ").append(l.texto).append('\n');
                }
            }
        }
        if (sb.length() > 0) {
            salvar();
            painel.repaint();
            Toolkit.getDefaultToolkit().beep();
            JOptionPane.showMessageDialog(this, sb.toString(), "Hora do lembrete!", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ====== Cores e prioridades ======
    private Color cor(Regiao r) {
        return cores.get(r);
    }

    private static double luminosidade(Color c) {
        return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
    }

    /** Texto escuro sobre cores claras e branco sobre cores escuras. */
    private static Color textoSobre(Color fundo) {
        return luminosidade(fundo) > 150 ? new Color(0x1B1B2F) : Color.WHITE;
    }

    /** Cor legível sobre o fundo escuro do painel lateral. */
    private static Color paraTexto(Color c) {
        return luminosidade(c) < 100 ? TEXTO : c;
    }

    private Prioridade maiorPrioridade(Regiao r) {
        Prioridade maior = null;
        for (Lembrete l : dados.get(r)) {
            if (!l.feito && (maior == null || l.prioridade.ordinal() < maior.ordinal())) maior = l.prioridade;
        }
        return maior == null ? Prioridade.MEDIA : maior;
    }

    private void escolherCor() {
        if (selecionada == null) {
            JOptionPane.showMessageDialog(this, "Clique primeiro em uma região do cérebro.");
            return;
        }
        Color nova = JColorChooser.showDialog(this, "Cor de " + selecionada.nome, cor(selecionada));
        if (nova != null) {
            cores.put(selecionada, nova);
            titulo.setForeground(paraTexto(nova));
            salvar();
            painel.repaint();
        }
    }

    private void corPadrao() {
        if (selecionada == null) return;
        cores.put(selecionada, selecionada.cor);
        titulo.setForeground(paraTexto(selecionada.cor));
        salvar();
        painel.repaint();
    }

    // ====== Persistência ======
    private static String limpar(String s) {
        return s.replace("\t", " ").replace("\n", " ").replace("\r", " ");
    }

    private void salvar() {
        List<String> linhas = new ArrayList<>();
        for (Regiao r : Regiao.values()) {
            linhas.add(String.join("\t", "COR", r.name(), String.format("#%06X", cor(r).getRGB() & 0xFFFFFF)));
        }
        for (Regiao r : Regiao.values()) {
            for (Lembrete l : dados.get(r)) {
                linhas.add(String.join("\t", r.name(), l.feito ? "1" : "0", l.avisado ? "1" : "0",
                        l.quando == null ? "" : l.quando.toString(), l.prioridade.name(), limpar(l.texto)));
            }
        }
        try {
            Files.write(ARQUIVO, linhas, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Não foi possível salvar: " + e.getMessage());
        }
    }

    private void carregar() {
        if (!Files.exists(ARQUIVO)) return;
        try {
            for (String linha : Files.readAllLines(ARQUIVO, StandardCharsets.UTF_8)) {
                String[] p = linha.split("\t", 6);
                try {
                    if (p[0].equals("COR") && p.length >= 3) {
                        cores.put(Regiao.valueOf(p[1]), Color.decode(p[2]));
                        continue;
                    }
                    if (p.length < 5) continue;
                    boolean formatoNovo = p.length >= 6; // arquivos antigos não tinham prioridade
                    Regiao r = Regiao.valueOf(p[0]);
                    Prioridade prio = formatoNovo ? Prioridade.valueOf(p[4]) : Prioridade.MEDIA;
                    Lembrete l = new Lembrete(formatoNovo ? p[5] : p[4],
                            p[3].isEmpty() ? null : LocalDateTime.parse(p[3]), prio);
                    l.feito = p[1].equals("1");
                    l.avisado = p[2].equals("1");
                    dados.get(r).add(l);
                } catch (IllegalArgumentException | DateTimeParseException ex) {
                    // ignora linha inválida
                }
            }
        } catch (IOException e) {
            System.err.println("Não foi possível carregar: " + e.getMessage());
        }
    }

    // ====== Painel do cérebro (desenho + cliques) ======
    class PainelCerebro extends JPanel {
        static final int W = 700, H = 470;
        final Map<Regiao, Area> formas = new EnumMap<>(Regiao.class);
        final List<Regiao> ordem = Arrays.asList(Regiao.CEREBELO, Regiao.TRONCO, Regiao.FRONTAL,
                Regiao.PARIETAL, Regiao.OCCIPITAL, Regiao.TEMPORAL, Regiao.HIPOCAMPO, Regiao.AMIGDALA);
        Area cerebro;
        Regiao hover;
        AffineTransform at = new AffineTransform();

        PainelCerebro() {
            setBackground(FUNDO);
            setPreferredSize(new Dimension(720, 600));
            construirFormas();

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    Regiao r = regiaoEm(e.getPoint());
                    if (r != hover) {
                        hover = r;
                        setCursor(r == null ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        setToolTipText(r == null ? null : r.nome);
                        repaint();
                    }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Regiao r = regiaoEm(e.getPoint());
                    if (r != null) selecionar(r);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = null;
                    repaint();
                }
            });
        }

        private Area poly(int... xy) {
            Path2D.Double p = new Path2D.Double();
            p.moveTo(xy[0], xy[1]);
            for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
            p.closePath();
            return new Area(p);
        }

        private Area recorte(Area base, Area corte) {
            Area a = new Area(base);
            a.intersect(corte);
            return a;
        }

        private void construirFormas() {
            cerebro = new Area(new Ellipse2D.Double(90, 50, 520, 300));

            formas.put(Regiao.FRONTAL, recorte(cerebro,
                    poly(0, 0, 330, 0, 315, 120, 295, 235, 225, 290, 0, 290)));
            formas.put(Regiao.PARIETAL, recorte(cerebro,
                    poly(330, 0, 465, 0, 455, 150, 440, 245, 295, 235, 315, 120)));
            formas.put(Regiao.OCCIPITAL, recorte(cerebro,
                    poly(465, 0, 700, 0, 700, 400, 440, 400, 440, 245, 455, 150)));
            formas.put(Regiao.TEMPORAL, recorte(cerebro,
                    poly(150, 290, 225, 290, 295, 235, 440, 245, 440, 400, 150, 400)));

            Area cerebelo = new Area(new Ellipse2D.Double(420, 285, 185, 100));
            cerebelo.subtract(cerebro);
            formas.put(Regiao.CEREBELO, cerebelo);

            Area tronco = poly(365, 300, 425, 300, 445, 430, 400, 445, 385, 360);
            tronco.subtract(cerebro);
            tronco.subtract(cerebelo);
            formas.put(Regiao.TRONCO, tronco);

            Area hipocampo = new Area(new Ellipse2D.Double(335, 268, 95, 22));
            hipocampo.transform(AffineTransform.getRotateInstance(-0.12, 382, 279));
            formas.put(Regiao.HIPOCAMPO, hipocampo);

            formas.put(Regiao.AMIGDALA, new Area(new Ellipse2D.Double(296, 270, 30, 30)));
        }

        Regiao regiaoEm(Point pt) {
            try {
                Point2D p = at.inverseTransform(pt, null);
                for (int i = ordem.size() - 1; i >= 0; i--) {
                    if (formas.get(ordem.get(i)).contains(p)) return ordem.get(i);
                }
            } catch (NoninvertibleTransformException e) {
                // ignora
            }
            return null;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            double s = Math.min(getWidth() / (double) W, getHeight() / (double) H);
            at = new AffineTransform();
            at.translate((getWidth() - W * s) / 2, (getHeight() - H * s) / 2);
            at.scale(s, s);
            g.transform(at);

            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.setColor(TEXTO);
            g.drawString("Mapa do Cérebro", 20, 34);

            Stroke contorno = new BasicStroke(2.5f);
            Color corContorno = new Color(0x1B1B2F);

            // cerebelo, tronco e lobos
            for (int i = 0; i < 6; i++) {
                Regiao r = ordem.get(i);
                preencher(g, r, contorno, corContorno);
            }

            // dobras (giros) para dar aspecto de cérebro
            Shape clipAntigo = g.getClip();
            g.setClip(cerebro);
            g.setStroke(new BasicStroke(1.6f));
            g.setColor(new Color(255, 255, 255, 70));
            for (int y = 70; y < 350; y += 26) {
                Path2D.Double p = new Path2D.Double();
                p.moveTo(90, y);
                for (int x = 90; x <= 610; x += 6) p.lineTo(x, y + 7 * Math.sin(x / 14.0 + y / 9.0));
                g.draw(p);
            }
            g.setColor(new Color(0, 0, 0, 40));
            for (int x = 110; x < 610; x += 38) {
                Path2D.Double p = new Path2D.Double();
                p.moveTo(x, 50);
                for (int y = 50; y <= 350; y += 6) p.lineTo(x + 6 * Math.sin(y / 12.0 + x / 7.0), y);
                g.draw(p);
            }
            g.setClip(formas.get(Regiao.CEREBELO));
            g.setColor(new Color(0, 0, 0, 55));
            for (int y = 290; y < 390; y += 9) {
                Path2D.Double p = new Path2D.Double();
                p.moveTo(420, y);
                for (int x = 420; x <= 605; x += 6) p.lineTo(x, y + 2.5 * Math.sin(x / 9.0));
                g.draw(p);
            }
            g.setClip(clipAntigo);

            // estruturas internas
            preencher(g, Regiao.HIPOCAMPO, contorno, corContorno);
            preencher(g, Regiao.AMIGDALA, contorno, corContorno);

            // seleção
            if (selecionada != null) {
                g.setStroke(new BasicStroke(4f));
                g.setColor(Color.WHITE);
                g.draw(formas.get(selecionada));
            }

            // rótulos
            Color txt = null; // null = escolhe preto/branco conforme a cor da região
            rotulo(g, Regiao.FRONTAL, 190, 200, 15, txt);
            rotulo(g, Regiao.PARIETAL, 385, 110, 15, txt);
            rotulo(g, Regiao.OCCIPITAL, 540, 190, 15, txt);
            rotulo(g, Regiao.TEMPORAL, 365, 325, 14, txt);
            rotulo(g, Regiao.CEREBELO, 513, 362, 12, txt);
            rotulo(g, Regiao.TRONCO, 405, 405, 11, txt);
            rotulo(g, Regiao.HIPOCAMPO, 382, 283, 11, txt);

            // amígdala com linha de chamada
            g.setStroke(new BasicStroke(1.2f));
            g.setColor(new Color(255, 255, 255, 140));
            g.draw(new Line2D.Double(311, 300, 290, 372));
            rotulo(g, Regiao.AMIGDALA, 290, 388, 13, TEXTO);

            // dica inferior
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.setColor(new Color(0x9FA8DA));
            g.drawString(hover != null ? hover.nome : "Clique em uma região para ver e adicionar lembretes", 20, H - 12);

            g.dispose();
        }

        private void preencher(Graphics2D g, Regiao r, Stroke contorno, Color corContorno) {
            Area a = formas.get(r);
            g.setColor(r == hover ? cor(r).brighter() : cor(r));
            g.fill(a);
            g.setStroke(contorno);
            g.setColor(corContorno);
            g.draw(a);
        }

        private void rotulo(Graphics2D g, Regiao r, int cx, int cy, int tam, Color corTexto) {
            g.setFont(new Font("SansSerif", Font.BOLD, tam));
            FontMetrics fm = g.getFontMetrics();
            int w = fm.stringWidth(r.curto);
            g.setColor(corTexto != null ? corTexto : textoSobre(cor(r)));
            g.drawString(r.curto, cx - w / 2, cy);
            int n = pendentes(r);
            if (n > 0) badge(g, cx + w / 2 + 13, cy - 5, n, maiorPrioridade(r).cor);
        }

        private void badge(Graphics2D g, int cx, int cy, int n, Color fundo) {
            g.setColor(fundo);
            g.fill(new Ellipse2D.Double(cx - 11, cy - 11, 22, 22));
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new Ellipse2D.Double(cx - 11, cy - 11, 22, 22));
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            String t = n > 99 ? "99+" : String.valueOf(n);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(t, cx - fm.stringWidth(t) / 2, cy + 4);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CerebroLembretes().setVisible(true));
    }
}
