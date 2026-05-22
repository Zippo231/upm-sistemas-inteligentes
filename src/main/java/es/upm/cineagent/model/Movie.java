package es.upm.cineagent.model;

import java.util.List;

/**
 * Clase modelo que representa una película del sistema CineAgent.
 * Los campos reflejan la estructura de respuesta de la API de TMDB.
 */
public class Movie {

    private int id;
    private String title;
    private String overview;
    private String releaseDate;
    private double voteAverage;
    private double popularity;
    private List<Integer> genreIds;
    private String posterPath;
    private double cineAgentScore; // Puntuación calculada por el AgenteProcesamiento

    public Movie() {}

    public Movie(int id, String title, String overview, String releaseDate,
                 double voteAverage, double popularity,
                 List<Integer> genreIds, String posterPath) {
        this.id = id;
        this.title = title;
        this.overview = overview;
        this.releaseDate = releaseDate;
        this.voteAverage = voteAverage;
        this.popularity = popularity;
        this.genreIds = genreIds;
        this.posterPath = posterPath;
        this.cineAgentScore = 0.0;
    }

    // --- Getters y Setters ---

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getOverview() { return overview; }
    public void setOverview(String overview) { this.overview = overview; }

    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }

    public double getVoteAverage() { return voteAverage; }
    public void setVoteAverage(double voteAverage) { this.voteAverage = voteAverage; }

    public double getPopularity() { return popularity; }
    public void setPopularity(double popularity) { this.popularity = popularity; }

    public List<Integer> getGenreIds() { return genreIds; }
    public void setGenreIds(List<Integer> genreIds) { this.genreIds = genreIds; }

    public String getPosterPath() { return posterPath; }
    public void setPosterPath(String posterPath) { this.posterPath = posterPath; }

    public double getCineAgentScore() { return cineAgentScore; }
    public void setCineAgentScore(double cineAgentScore) { this.cineAgentScore = cineAgentScore; }

    /**
     * Devuelve el año de estreno a partir de la fecha completa (YYYY-MM-DD).
     */
    public String getYear() {
        if (releaseDate != null && releaseDate.length() >= 4) {
            return releaseDate.substring(0, 4);
        }
        return "Desconocido";
    }

    @Override
    public String toString() {
        return "Movie{title='" + title + "', year=" + getYear()
                + ", rating=" + voteAverage + ", score=" + cineAgentScore + "}";
    }
}
