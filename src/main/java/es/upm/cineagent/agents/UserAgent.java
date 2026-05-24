package es.upm.cineagent.agents;

import es.upm.cineagent.launcher.AgentBase;
import es.upm.cineagent.launcher.AgentModel;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AgenteUsuario: muestra un formulario Swing donde el usuario
 * introduce sus preferencias cinematográficas. Envía las preferencias
 * al AgentePercepcion y espera confirmación del AgenteVisualizacion.
 */
public class UserAgent extends AgentBase {

    public static final String NICKNAME = "UserAgent";

    // Componentes del formulario
    private JFrame userFrame;
    private final Map<String, JCheckBox> genreBoxes = new LinkedHashMap<>();
    private JSlider ratingSlider;
    private JLabel ratingValueLabel;
    private JComboBox<String> decadeBox;
    private JComboBox<String> languageBox;
    private JButton searchBtn;

    // Comunicación entre el hilo EDT (GUI) y el hilo de JADE
    private volatile String pendingPrefs = null;
    private WaitForUserBehaviour waitBehaviour;

    @Override
    protected void setup() {
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.setup();
        this.type = AgentModel.USER;
        registerAgentDF();

        // Añadimos el comportamiento principal
        waitBehaviour = new WaitForUserBehaviour();
        addBehaviour(waitBehaviour);

        // Construimos la GUI en el hilo de eventos de Swing (EDT)
        SwingUtilities.invokeLater(this::buildGUI);

        log("AgenteUsuario iniciado y registrado en el DF.");
    }

    /**
     * Construye y muestra el formulario de preferencias.
     */
    private void buildGUI() {
        userFrame = new JFrame("CineAgent \u2014 Buscador de Pel\u00edculas");
        userFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        userFrame.setSize(480, 600); // Aumentato leggermente per dare più respiro
        userFrame.setResizable(false);

        // --- AGGIUNTA ICONA ---
        try {
            java.awt.Image icon = javax.imageio.ImageIO.read(getClass().getResource("/logo.png"));
            userFrame.setIconImage(icon);
        } catch (Exception ex) {
            System.out.println("Errore nel caricamento del logo: " + ex.getMessage());
        }

        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
        mainPanel.setBackground(Color.WHITE); // Sfondo bianco pulito

        // --- Título ---
        JLabel titleLabel = new JLabel(" Cine\nAgent", SwingConstants.CENTER);
       try {
            java.awt.Image img = javax.imageio.ImageIO.read(getClass().getResource("/logo.png"));
            java.awt.Image scaledImg = img.getScaledInstance(64, 64, java.awt.Image.SCALE_SMOOTH);
            titleLabel.setIcon(new ImageIcon(scaledImg));
        } catch (Exception ex) { }
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // --- Formulario central ---
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBackground(Color.WHITE); // Mantiene lo sfondo bianco

        // G\u00e9neros
        JLabel genreLabel = new JLabel("G\u00e9neros favoritos:");
        genreLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        formPanel.add(genreLabel);
        formPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        String[][] genres = {
                { "Acci\u00f3n", "28" }, { "Comedia", "35" }, { "Terror", "27" },
                { "Drama", "18" }, { "Ciencia Ficci\u00f3n", "878" }, { "Romance", "10749" },
                { "Animaci\u00f3n", "16" }, { "Thriller", "53" }, { "Aventura", "12" }
        };
        JPanel genrePanel = new JPanel(new GridLayout(3, 3, 10, 8));
        genrePanel.setBackground(Color.WHITE);
        for (String[] genre : genres) {
            JCheckBox cb = new JCheckBox(genre[0]);
            cb.setBackground(Color.WHITE);
            cb.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            genreBoxes.put(genre[1], cb);
            genrePanel.add(cb);
        }
        formPanel.add(genrePanel);
        formPanel.add(Box.createRigidArea(new Dimension(0, 20))); // Più spazio tra le sezioni

        // Valoraci\u00f3n m\u00ednima
        JLabel ratingTitle = new JLabel("Valoraci\u00f3n m\u00ednima de TMDB:");
        ratingTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        formPanel.add(ratingTitle);
        formPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        JPanel ratingPanel = new JPanel(new BorderLayout(10, 0));
        ratingPanel.setBackground(Color.WHITE);
        ratingSlider = new JSlider(0, 100, 50);
        ratingSlider.setBackground(Color.WHITE);
        ratingSlider.setMajorTickSpacing(25);
        ratingSlider.setPaintTicks(true);
        ratingValueLabel = new JLabel("5.0 / 10");
        ratingValueLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        ratingValueLabel.setPreferredSize(new Dimension(65, 20));
        ratingSlider.addChangeListener(
                e -> ratingValueLabel.setText(String.format("%.1f / 10", ratingSlider.getValue() / 10.0)));
        ratingPanel.add(ratingSlider, BorderLayout.CENTER);
        ratingPanel.add(ratingValueLabel, BorderLayout.EAST);
        formPanel.add(ratingPanel);
        formPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        // \u00c9poca
        JLabel decadeTitle = new JLabel("\u00c9poca preferida:");
        decadeTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        formPanel.add(decadeTitle);
        formPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        decadeBox = new JComboBox<>(new String[] {
                "Cualquier \u00e9poca", "A\u00f1os 80", "A\u00f1os 90", "A\u00f1os 2000", "A\u00f1os 2010",
                "A\u00f1os 2020"
        });
        decadeBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        decadeBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35)); // Altezza aumentata
        formPanel.add(decadeBox);
        formPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        // Idioma original
        JLabel langTitle = new JLabel("Idioma original:");
        langTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        formPanel.add(langTitle);
        formPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        languageBox = new JComboBox<>(new String[] {
                "Cualquier idioma", "Ingl\u00e9s", "Espa\u00f1ol", "Franc\u00e9s", "Japon\u00e9s"
        });
        languageBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        languageBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        formPanel.add(languageBox);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // --- Botón de búsqueda ---
        searchBtn = new JButton(" Buscar películas");
        try {
            ImageIcon searchIcon = new ImageIcon(
                    new ImageIcon(getClass().getResource("/magnifying-glass.png"))
                            .getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH));
            searchBtn.setIcon(searchIcon);
            searchBtn.setIconTextGap(8);
        } catch (Exception ex) { }
        searchBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        searchBtn.setPreferredSize(new Dimension(0, 50)); 
        searchBtn.setCursor(new Cursor(Cursor.HAND_CURSOR)); 
        searchBtn.setBackground(new Color(33, 150, 243)); 
        searchBtn.setForeground(Color.BLACK); 
        searchBtn.setFocusPainted(false); 
        searchBtn.addActionListener(e -> onSearchClicked());
        mainPanel.add(searchBtn, BorderLayout.SOUTH);

        userFrame.setContentPane(mainPanel);
        userFrame.setLocationRelativeTo(null);
        userFrame.setVisible(true);
    }

    /**
     * Se llama cuando el usuario pulsa "Buscar películas".
     * Serializa las preferencias y despierta el comportamiento JADE.
     */
    private void onSearchClicked() {
        String prefs = buildPreferencesJson();
        searchBtn.setEnabled(false);
        searchBtn.setText("Buscando...");
        pendingPrefs = prefs;
        waitBehaviour.restart(); // Despierta el comportamiento bloqueado
    }

    /**
     * Recoge los valores del formulario y los serializa en JSON.
     */
    private String buildPreferencesJson() {
        // Géneros seleccionados
        StringBuilder genres = new StringBuilder();
        for (Map.Entry<String, JCheckBox> entry : genreBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                if (genres.length() > 0)
                    genres.append(",");
                genres.append(entry.getKey());
            }
        }

        double minRating = ratingSlider.getValue() / 10.0;

        // Transformar selección de época a año inicial
        String selectedDecade = (String) decadeBox.getSelectedItem();
        String decadeYear = "";
        switch (selectedDecade) {
            case "A\u00f1os 80":
                decadeYear = "1980";
                break;
            case "A\u00f1os 90":
                decadeYear = "1990";
                break;
            case "A\u00f1os 2000":
                decadeYear = "2000";
                break;
            case "A\u00f1os 2010":
                decadeYear = "2010";
                break;
            case "A\u00f1os 2020":
                decadeYear = "2020";
                break;
            default:
                decadeYear = "";
                break;
        }

        // Transformar idioma a código ISO
        String selectedLang = (String) languageBox.getSelectedItem();
        String langCode = "";
        switch (selectedLang) {
            case "Ingl\u00e9s":
                langCode = "en";
                break;
            case "Espa\u00f1ol":
                langCode = "es";
                break;
            case "Franc\u00e9s":
                langCode = "fr";
                break;
            case "Japon\u00e9s":
                langCode = "ja";
                break;
            default:
                langCode = "";
                break;
        }

        return String.format(
                "{\"genres\":\"%s\",\"minRating\":\"%.1f\",\"decade\":\"%s\",\"language\":\"%s\"}",
                genres, minRating, decadeYear, langCode);
    }

    // -----------------------------------------------------------------------
    // COMPORTAMIENTO PRINCIPAL
    // -----------------------------------------------------------------------

    /**
     * Comportamiento cíclico que espera a que el usuario pulse "Buscar",
     * envía las preferencias al AgentePercepcion y espera confirmación
     * del AgenteVisualizacion (filtro bloqueante).
     */
    private class WaitForUserBehaviour extends CyclicBehaviour {

        @Override
        public void action() {

            // Si el usuario no ha pulsado el botón aún, bloqueamos el comportamiento
            if (pendingPrefs == null) {
                block();
                return;
            }

            String prefs = pendingPrefs;
            pendingPrefs = null;

            log("Preferencias recogidas: " + prefs);

            // Buscamos el SearchAgent en el DF
            DFAgentDescription[] agents = getAgentsDF(AgentModel.SEARCH);

            if (agents.length > 0) {
                // Envío de REQUEST al SearchAgent
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(agents[0].getName());
                msg.setContent(prefs);
                msg.setOntology("movie-search");
                send(msg);
                log("Preferencias enviadas a SearchAgent.");

                // Filtro de mensajes en modo bloqueante:
                // esperamos CONFIRM de DisplayAgent antes de volver a habilitar el formulario
                ACLMessage confirm = blockingReceive(
                        MessageTemplate.MatchPerformative(ACLMessage.CONFIRM));
                if (confirm != null) {
                    log("Resultados visualizados correctamente.");
                }

            } else {
                loge("No se encontr\u00f3 SearchAgent en el DF. \u00bfEst\u00e1n todos los agentes iniciados?");
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(userFrame,
                        "Error: no se pudo conectar con el sistema de b\u00fasqueda.\n"
                                + "Aseg\u00farate de que todos los agentes est\u00e1n activos.",
                        "Error de conexi\u00f3n", JOptionPane.ERROR_MESSAGE));
            }

            // Re-habilitamos el botón en el EDT
            SwingUtilities.invokeLater(() -> {
                searchBtn.setEnabled(true);
                searchBtn.setText(" Buscar películas");
            });
        }
    }
}
