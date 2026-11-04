
import java.io.*;
import java.util.*;
import java.sql.*;

public class Ex13 {

    static class Movie implements Serializable {
        private String movieID;
        private String title;
        private String director;
        private double rating;

        public Movie() {
        }

        public Movie(String movieID, String title,
                     String director, double rating) {
            this.movieID = movieID;
            this.title = title;
            this.director = director;
            this.rating = rating;
        }

        public String getMovieID() {
            return movieID;
        }

        public void setMovieID(String movieID) {
            this.movieID = movieID;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDirector() {
            return director;
        }

        public void setDirector(String director) {
            this.director = director;
        }

        public double getRating() {
            return rating;
        }

        public void setRating(double rating) {
            this.rating = rating;
        }

        @Override
        public String toString() {
            return "Movie{" +
                    "movieID='" + movieID + '\'' +
                    ", title='" + title + '\'' +
                    ", director='" + director + '\'' +
                    ", rating=" + rating +
                    '}';
        }
    }

    static class MovieTest {

        private static final BufferedReader br =
            new BufferedReader(new InputStreamReader(System.in));

        private static final String FILE_NAME = "movies.dat";

        public static void main(String[] args) {
            try {
                List<Movie> movies = inputMovies();

                if (!saveMovies(movies)) {
                    return;
                }

                List<Movie> loadedMovies = readMovies();

                if (loadedMovies == null) {
                    return;
                }

                System.out.println("\n========== ALL MOVIES ==========");
                displayMovies(loadedMovies);

                System.out.println("\n========== MOVIES WITH RATING > 4 ==========");
                displayMoviesAbove4(loadedMovies);

                // Optional MySQL bonus
                System.out.print(
                    "\nDo you want to insert movies into MySQL? (y/n): "
                );

                String choice = br.readLine();

                if (choice != null &&
                    choice.trim().equalsIgnoreCase("y")) {
                    insertMoviesIntoDatabase(loadedMovies);
                }

            } catch (IOException e) {
                System.out.println(
                    "Error reading keyboard input: " + e.getMessage()
                );
            }
        }

        // Input exactly 5 movies
        public static List<Movie> inputMovies() throws IOException {
            List<Movie> movies = new ArrayList<>();

            System.out.println("Enter information for 5 movies:");

            for (int i = 0; i < 5; i++) {
                System.out.println("\nMovie " + (i + 1));

                String movieID;

                // Validate movie ID
                while (true) {
                    System.out.print("Movie ID: ");
                    movieID = br.readLine();

                    if (movieID == null) {
                        throw new EOFException("No more keyboard input.");
                    }

                    movieID = movieID.trim();

                    if (!movieID.matches("^[A-Z]{2}\\d{4}$")) {
                        System.out.println(
                            "Invalid movie ID. Example: AB1234"
                        );
                    } else {
                        break;
                    }
                }

                // Validate title
                String title;

                while (true) {
                    System.out.print("Title: ");
                    title = br.readLine();

                    if (title == null) {
                        throw new EOFException("No more keyboard input.");
                    }

                    title = title.trim();

                    if (title.length() < 5) {
                        System.out.println(
                            "Title must contain at least 5 characters."
                        );
                    } else {
                        break;
                    }
                }

                // Read director
                System.out.print("Director: ");
                String director = br.readLine();

                if (director == null) {
                    throw new EOFException("No more keyboard input.");
                }

                director = director.trim();

                // Validate rating
                double rating;

                while (true) {
                    System.out.print("Rating (1-5): ");
                    String input = br.readLine();

                    if (input == null) {
                        throw new EOFException("No more keyboard input.");
                    }

                    try {
                        rating = Double.parseDouble(input.trim());

                        if (!Double.isFinite(rating) ||
                            rating < 1 || rating > 5) {
                            System.out.println(
                                "Rating must be between 1 and 5."
                            );
                        } else {
                            break;
                        }

                    } catch (NumberFormatException e) {
                        System.out.println("Invalid rating. Try again.");
                    }
                }

                movies.add(
                    new Movie(movieID, title, director, rating)
                );

                System.out.println("Movie added successfully.");
            }

            return movies;
        }

        // Serialize the movie list
        public static boolean saveMovies(List<Movie> movies) {
            try (ObjectOutputStream oos =
                     new ObjectOutputStream(
                         new FileOutputStream(FILE_NAME))) {

                oos.writeObject(movies);

                System.out.println(
                    "\nMovies saved successfully to " + FILE_NAME
                );

                return true;

            } catch (IOException e) {
                System.out.println(
                    "Error saving movies: " + e.getMessage()
                );

                return false;
            }
        }

        // Deserialize the movie list
        @SuppressWarnings("unchecked")
        public static List<Movie> readMovies() {
            try (ObjectInputStream ois =
                     new ObjectInputStream(
                         new FileInputStream(FILE_NAME))) {

                List<Movie> movies = (List<Movie>) ois.readObject();

                System.out.println("Movies loaded successfully.");

                return movies;

            } catch (IOException e) {
                System.out.println(
                    "Error reading movies: " + e.getMessage()
                );

            } catch (ClassNotFoundException e) {
                System.out.println(
                    "Error: Movie class could not be found."
                );
            }

            return null;
        }

        // Display all movies
        public static void displayMovies(List<Movie> movies) {
            for (Movie movie : movies) {
                System.out.println(movie);
            }
        }

        // Display movies with rating greater than 4
        public static void displayMoviesAbove4(List<Movie> movies) {
            boolean found = false;

            for (Movie movie : movies) {
                if (movie.getRating() > 4) {
                    System.out.println(movie);
                    found = true;
                }
            }

            if (!found) {
                System.out.println("No movies found with rating above 4.");
            }
        }

        // Bonus: Insert all movies into MySQL
        public static void insertMoviesIntoDatabase(List<Movie> movies) {

            String serverURL = "jdbc:mysql://localhost:3306/";
            String databaseURL =
                "jdbc:mysql://localhost:3306/moviemanage";

            String username = "root";
            String password = "your_password";

            String createTableSQL =
                "CREATE TABLE IF NOT EXISTS movies (" +
                "movieID VARCHAR(6) PRIMARY KEY, " +
                "title VARCHAR(255), " +
                "director VARCHAR(255), " +
                "rating DOUBLE)";

            String insertSQL =
                "INSERT INTO movies (movieID, title, director, rating) " +
                "VALUES (?, ?, ?, ?)";

            try {
                // Create database if it does not exist
                try (Connection connection =
                         DriverManager.getConnection(
                             serverURL, username, password);
                     Statement statement = connection.createStatement()) {

                    statement.executeUpdate(
                        "CREATE DATABASE IF NOT EXISTS moviemanage"
                    );
                }

                // Connect to moviemanage database
                try (Connection connection =
                         DriverManager.getConnection(
                             databaseURL, username, password);
                     Statement statement = connection.createStatement()) {

                    statement.executeUpdate(createTableSQL);

                    // Insert all movies using PreparedStatement
                    try (PreparedStatement ps =
                             connection.prepareStatement(insertSQL)) {

                        for (Movie movie : movies) {
                            ps.setString(1, movie.getMovieID());
                            ps.setString(2, movie.getTitle());
                            ps.setString(3, movie.getDirector());
                            ps.setDouble(4, movie.getRating());

                            ps.addBatch();
                        }

                        ps.executeBatch();

                        System.out.println(
                            "Movies inserted into MySQL successfully."
                        );
                    }
                }

            } catch (SQLException e) {
                System.out.println(
                    "Database error: " + e.getMessage()
                );
            }
        }
    }
}
