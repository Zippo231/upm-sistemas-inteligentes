package es.upm.cineagent.agents;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import es.upm.cineagent.launcher.AgentBase;
import es.upm.cineagent.launcher.AgentModel;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

/**
 * AgenteVisualizacion: recibe el ranking de películas del AgenteProcesamiento,
 * lo muestra en una ventana Swing con pósters, puntuaciones y descripciones,
 * y envía una confirmación al AgenteUsuario para que pueda hacer otra búsqueda.
 */
public class DisplayAgent extends AgentBase {

    public static final String NICKNAME = "DisplayAgent";

    // URL base para las imágenes de TMDB (tamaño w92)
    private static final String TMDB_IMAGE_URL = "https://image.tmdb.org/t/p/w92";

    // Componentes de la ventana de resultados
    private JFrame displayFrame;
    private JPanel moviesPanel;
    private JLabel statusLabel;

    @Override
    protected void setup() {
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.setup();
        this.type = AgentModel.DISPLAY;
        registerAgentDF();

        // Construimos la GUI de resultados en el EDT
        SwingUtilities.invokeLater(this::buildGUI);

        addBehaviour(new DisplayBehaviour());
        log("AgenteVisualizacion iniciado y registrado en el DF.");
    }

    /**
     * Construye la ventana de resultados (inicialmente vacía).
     */
    private void buildGUI() {
        displayFrame = new JFrame("CineAgent \u2014 Resultados");
        displayFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        displayFrame.setSize(620, 700);

        try {
            java.awt.Image taskbarIcon = javax.imageio.ImageIO.read(getClass().getResource("/logo.png"));
            displayFrame.setIconImage(taskbarIcon);
        } catch (Exception ex) { 
            System.out.println("Error loading taskbar logo: " + ex.getMessage());
        }

        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));

        // Cabecera
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(20, 20, 40));
        header.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JLabel headerTitle = new JLabel(" CineAgent \u2014 Recomendaciones");
        try {
            java.awt.Image img = javax.imageio.ImageIO.read(getClass().getResource("/logo.png"));
            // Riduciamo l'immagine a 48x48 pixel per bilanciarla col font grandezza 18
            java.awt.Image scaledImg = img.getScaledInstance(48, 48, java.awt.Image.SCALE_SMOOTH);
            headerTitle.setIcon(new ImageIcon(scaledImg));
        } catch (Exception ex) {
        }
        headerTitle.setFont(new Font("Arial", Font.BOLD, 18));
        headerTitle.setForeground(Color.WHITE);
        header.add(headerTitle, BorderLayout.WEST);

        statusLabel = new JLabel("Esperando b\u00fasqueda...");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(180, 180, 200));
        header.add(statusLabel, BorderLayout.EAST);

        mainPanel.add(header, BorderLayout.NORTH);

        // Panel de películas (scrollable)
        moviesPanel = new JPanel();
        moviesPanel.setLayout(new BoxLayout(moviesPanel, BoxLayout.Y_AXIS));
        moviesPanel.setBackground(new Color(245, 245, 250));

        JScrollPane scrollPane = new JScrollPane(moviesPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        // I added these 4 lines to make the scroll bar better, both in X and Y
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getVerticalScrollBar().setBlockIncrement(80);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setBlockIncrement(80);
        // - - -
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        displayFrame.setContentPane(mainPanel);
        displayFrame.setLocationRelativeTo(null);
        displayFrame.setVisible(true);
    }

    /**
     * Actualiza el panel de resultados con las películas recibidas.
     * Debe llamarse siempre desde el EDT (SwingUtilities.invokeLater).
     */
    private void displayMovies(JsonArray movies) {
        moviesPanel.removeAll();

        if (movies.size() == 0) {
            JLabel noResults = new JLabel(
                    "<html><center>No se encontraron pel\u00edculas con esos criterios.<br>"
                            + "Prueba a cambiar los filtros.</center></html>",
                    SwingConstants.CENTER);
            noResults.setFont(new Font("Arial", Font.ITALIC, 14));
            noResults.setForeground(Color.GRAY);
            noResults.setAlignmentX(Component.CENTER_ALIGNMENT);
            moviesPanel.add(Box.createRigidArea(new Dimension(0, 40)));
            moviesPanel.add(noResults);
        } else {
            statusLabel.setText("Mostrando " + movies.size() + " recomendaciones");
            int rank = 1;
            for (JsonElement el : movies) {
                moviesPanel.add(buildMovieCard(el.getAsJsonObject(), rank++));
                moviesPanel.add(Box.createRigidArea(new Dimension(0, 5)));
            }
        }

        moviesPanel.revalidate();
        moviesPanel.repaint();
        // Volvemos al inicio del scroll
        SwingUtilities.invokeLater(() -> ((JScrollPane) moviesPanel.getParent().getParent())
                .getVerticalScrollBar().setValue(0));
    }

    /**
     * Construye la "tarjeta" visual de una película.
     */
    private JPanel buildMovieCard(JsonObject m, int rank) {
        JPanel card = new JPanel(new BorderLayout(12, 8));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(5, 10, 0, 10),
                BorderFactory.createLineBorder(new Color(220, 220, 230), 1, true)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));

        // --- Póster ---
        JLabel posterLabel = new JLabel();
        posterLabel.setPreferredSize(new Dimension(62, 92));
        posterLabel.setHorizontalAlignment(SwingConstants.CENTER);
        posterLabel.setFont(new Font("Arial", Font.PLAIN, 28));
        posterLabel.setText("\u25B6"); // icono por defecto mientras carga

        String posterPath = m.has("poster_path") && !m.get("poster_path").isJsonNull()
                ? m.get("poster_path").getAsString()
                : "";
        if (!posterPath.isEmpty()) {
            loadPosterAsync(posterLabel, posterPath);
        }
        card.add(posterLabel, BorderLayout.WEST);

        // --- Panel de información ---
        JPanel infoPanel = new JPanel();
        infoPanel.setOpaque(false);
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 10));

        // Posición en el ranking + título + año
        String title = m.has("title") ? m.get("title").getAsString() : "Sin t\u00edtulo";
        String year = "";
        if (m.has("release_date") && !m.get("release_date").isJsonNull()) {
            String rd = m.get("release_date").getAsString();
            if (rd.length() >= 4)
                year = " (" + rd.substring(0, 4) + ")";
        }
        JLabel titleLabel = new JLabel("#" + rank + "  " + title + year);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15)); // Font più moderno
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT); // <--- FORZA L'ALLINEAMENTO A SINISTRA

        // Puntuación TMDB y CineAgent
        double tmdbRating = m.has("vote_average") ? m.get("vote_average").getAsDouble() : 0;
        double cineScore = m.has("cineAgentScore") ? m.get("cineAgentScore").getAsDouble() : 0;

        JPanel ratingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        ratingPanel.setOpaque(false);
        ratingPanel.setAlignmentX(Component.LEFT_ALIGNMENT); // <--- FORZA L'ALLINEAMENTO A SINISTRA

        JLabel tmdbLabel = new JLabel(String.format("%.1f / 10", tmdbRating));
        try {
            ImageIcon starIcon = new ImageIcon(
                    new ImageIcon(getClass().getResource("/star.png"))
                            .getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH));
            tmdbLabel.setIcon(starIcon);
            tmdbLabel.setIconTextGap(6);
        } catch (Exception ex) {
        }
        tmdbLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tmdbLabel.setForeground(new Color(60, 100, 180));

        JLabel cineLabel = new JLabel(String.format("%.0f%%", cineScore * 100));
        try {
            ImageIcon filmIcon = new ImageIcon(
                    new ImageIcon(getClass().getResource("/film-slate.png"))
                            .getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH));
            cineLabel.setIcon(filmIcon);
            cineLabel.setIconTextGap(6);
        } catch (Exception ex) {
        }
        cineLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        cineLabel.setForeground(new Color(60, 100, 180));

        ratingPanel.add(tmdbLabel);
        ratingPanel.add(cineLabel);

        // Barra visual de puntuación CineAgent
        JProgressBar scoreBar = new JProgressBar(0, 100);
        scoreBar.setValue((int) (cineScore * 100));

        // --- THE FIX: Forces a flat modern UI instead of the glitchy Windows 3D bar
        // ---
        scoreBar.setUI(new javax.swing.plaf.basic.BasicProgressBarUI());

        Dimension barSize = new Dimension(400, 6);
        scoreBar.setPreferredSize(barSize);
        scoreBar.setMaximumSize(barSize);
        scoreBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        scoreBar.setForeground(getScoreColor(cineScore)); // The green/yellow/red fill
        scoreBar.setBackground(new Color(235, 235, 245)); // Light gray background track
        scoreBar.setBorderPainted(false);

        // Sinopsis (truncada)
        String overview = m.has("overview") ? m.get("overview").getAsString() : "";
        if (overview.length() > 130)
            overview = overview.substring(0, 130) + "...";
        JLabel overviewLabel = new JLabel("<html><body style='width:380px'>" + overview + "</body></html>");
        overviewLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        overviewLabel.setForeground(Color.DARK_GRAY);
        overviewLabel.setAlignmentX(Component.LEFT_ALIGNMENT); // <--- FORZA L'ALLINEAMENTO A SINISTRA

        // Aggiungiamo i componenti al pannello informazioni
        infoPanel.add(titleLabel);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 3)));
        infoPanel.add(ratingPanel);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 3)));
        infoPanel.add(scoreBar);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        infoPanel.add(overviewLabel);

        card.add(infoPanel, BorderLayout.CENTER);
        return card;
    }

    /**
     * Carga el póster de TMDB de forma asíncrona para no bloquear la GUI.
     */
    private void loadPosterAsync(JLabel label, String posterPath) {
        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() {
                try {
                    URL imageUrl = new URL(TMDB_IMAGE_URL + posterPath);
                    ImageIcon icon = new ImageIcon(imageUrl);
                    Image img = icon.getImage().getScaledInstance(62, 92, Image.SCALE_SMOOTH);
                    return new ImageIcon(img);
                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    ImageIcon icon = get();
                    if (icon != null) {
                        label.setIcon(icon);
                        label.setText("");
                    }
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    /**
     * Devuelve un color según la puntuación (rojo → amarillo → verde).
     */
    private Color getScoreColor(double score) {
        if (score >= 0.75)
            return new Color(50, 180, 80);
        if (score >= 0.50)
            return new Color(220, 180, 30);
        return new Color(220, 80, 60);
    }

    // -----------------------------------------------------------------------
    // COMPORTAMIENTO PRINCIPAL
    // -----------------------------------------------------------------------

    /**
     * Comportamiento cíclico que espera (modo bloqueante) el ranking de películas
     * del AgenteProcesamiento, actualiza la GUI y envía CONFIRM al AgenteUsuario.
     */
    private class DisplayBehaviour extends CyclicBehaviour {

        @Override
        public void action() {

            // Filtro bloqueante: esperamos INFORM con ontología "movie-results"
            ACLMessage msg = blockingReceive(
                    MessageTemplate.and(
                            MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                            MessageTemplate.MatchOntology("movie-results")));

            if (msg != null) {
                log("Ranking de películas recibido. Actualizando interfaz...");
                JsonArray rankedMovies = JsonParser.parseString(msg.getContent()).getAsJsonArray();

                // Actualizamos la GUI en el hilo de eventos de Swing
                SwingUtilities.invokeLater(() -> displayMovies(rankedMovies));

                // Enviamos CONFIRM al AgenteUsuario para que reactive el formulario
                DFAgentDescription[] agents = getAgentsDF(AgentModel.USER);
                if (agents.length > 0) {
                    ACLMessage confirm = new ACLMessage(ACLMessage.CONFIRM);
                    confirm.addReceiver(agents[0].getName());
                    confirm.setContent("done");
                    send(confirm);
                    log("Confirmaci\u00f3n enviada al AgenteUsuario.");
                } else {
                    loge("No se encontr\u00f3 UserAgent en el DF.");
                }
            }
        }
    }
}
