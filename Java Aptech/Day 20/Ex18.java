import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class Ex18 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter a int: ");
        int i = Integer.parseInt(br.readLine());

        System.out.print("Enter a double: ");
        double d = Double.parseDouble(br.readLine());

        System.out.print("Enter a bool: ");
        boolean b = Boolean.parseBoolean(br.readLine());

        System.out.print("Enter a char: ");
        char c = br.readLine().charAt(0);

        System.out.print("Enter a string: ");
        String str = br.readLine();

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream("files/data.dat"))) {
            dos.writeInt(i);
            dos.writeDouble(d);
            dos.writeBoolean(b);
            dos.writeChar(c);
            dos.writeUTF(str);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        try (DataInputStream dis = new DataInputStream(new FileInputStream("files/data.dat"))) {
            System.out.println("\nData successfully read:\nInteger: " + dis.readInt() + "\nDouble: " + dis.readDouble()
                    + "\nBoolean: " + dis.readBoolean() + "\nCharacter: " + dis.readChar() + "\nString: "
                    + dis.readUTF());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
