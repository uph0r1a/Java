import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ex18 {
    static class Question {
        private String question, answer1, answer2, answer3, answer4;
        private int correct;

        public Question(String question, String answer1, String answer2, String answer3, String answer4, int correct) {
            this.question = question;
            this.answer1 = answer1;
            this.answer2 = answer2;
            this.answer3 = answer3;
            this.answer4 = answer4;
            this.correct = correct;
        }

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public String getAnswer1() {
            return answer1;
        }

        public void setAnswer1(String answer1) {
            this.answer1 = answer1;
        }

        public String getAnswer2() {
            return answer2;
        }

        public void setAnswer2(String answer2) {
            this.answer2 = answer2;
        }

        public String getAnswer3() {
            return answer3;
        }

        public void setAnswer3(String answer3) {
            this.answer3 = answer3;
        }

        public String getAnswer4() {
            return answer4;
        }

        public void setAnswer4(String answer4) {
            this.answer4 = answer4;
        }

        public int getCorrect() {
            return correct;
        }

        public void setCorrect(int correct) {
            this.correct = correct;
        }

    }

    public static void main(String[] args) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Question[] questions = {
                new Question("What is the largest planet in our solar system?", "Saturn", "Earth", "Jupiter", "Mars",
                        3),
                new Question("Which country is home to the Great Pyramid of Giza?", "Greece", "Mexico", "Sudan",
                        "Egypt", 4),
                new Question("Who wrote Romeo and Juliet?", "Charles Dickens", "Mark Twain", "William Shakespeare",
                        "Jane Austen", 3),
                new Question("What is the chemical symbol for gold?", "Ag", "Gd", "Go", "Au", 4),
                new Question("How many continents are there on Earth?", "Five", "Six", "Eight", "Seven", 4),
                new Question("What is the capital city of Australia?", "Sydney", "Melbourne", "Perth", "Canberra", 4),
                new Question("Which ocean is the largest?", "Atlantic Ocean", "Indian Ocean", "Arctic Ocean",
                        "Pacific Ocean", 4),
                new Question("What is the fastest land animal?", "Lion", "Horse", "Leopard", "Cheetah", 4),
                new Question("Which organ in the human body pumps blood?", "Brain", "Lungs", "Liver", "Heart", 4),
                new Question("In what year did humans first land on the Moon?", "1965", "1967", "1971", "1969", 4)
        };
        int player1 = 0, player2 = 0;

        for (int i = 0; i < questions.length; i++) {
            System.out.print(questions[i].getQuestion() + "\n1) " + questions[i].getAnswer1() + "\n2) "
                    + questions[i].getAnswer2() + "\n3) " + questions[i].getAnswer3() + "\n4) "
                    + questions[i].getAnswer4() + "\nEnter your choice: ");
            int choice;
            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice >= 1 && choice <= 4) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            if (i % 2 != 0) {
                if (choice == questions[i].getCorrect()) {
                    player1++;
                }
            } else {
                if (choice == questions[i].getCorrect()) {
                    player2++;
                }
            }
        }
        System.out.println("Player 1: " + player1 + "\nPlayer 2: " + player2 + "\nWinner: "
                + (player1 > player2 ? "Player 1" : "Player 2"));
    }
}
