import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Ex10 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        Map<String, String> studentGrade = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            System.out.print("Enter student name: ");
            String name = br.readLine().strip();

            System.out.print("Enter grade (e.g. A, B+, C-): ");
            String grade = br.readLine().strip().toUpperCase();

            studentGrade.put(name, grade);
        }

        System.out.print("Enter student name: ");
        String name = br.readLine().strip();
        if (studentGrade.containsKey(name)) {
            System.out.println("Student name: " + name + "\nGrade: " + studentGrade.get(name));
        } else {
            System.out.println("Not found");
        }
    }
}