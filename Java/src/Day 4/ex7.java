import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex7 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter number of floor: ");
        int floor;
        while (true) {
            floor = Integer.parseInt(br.readLine());

            if (floor >= 1) {
                break;
            }
            System.out.print("Floor cant be less than 1\nRe-enter number of floor: ");
        }

        int totalRoom = 0, totalRoomOccupied = 0;
        for (int i = 0; i < floor; i++) {
            System.out.print("Floor " + (i + 1) + "\nEnter number of room: ");
            int room;
            while (true) {
                room = Integer.parseInt(br.readLine());

                if (room >= 10) {
                    break;
                }
                System.out.print("Room cant be less than 10\nRe-enter number of room: ");
            }

            System.out.print("Enter number of room occupied: ");
            int roomOccupied = Integer.parseInt(br.readLine());

            totalRoom += room;
            totalRoomOccupied += roomOccupied;
        }

        System.out.println("Total number of room: " + totalRoom + "\nTotal number of room occupied: "
                + totalRoomOccupied + "\nTotal number of room vacant: " + (totalRoom - totalRoomOccupied)
                + "\nOccupancy rate: " + (double) totalRoomOccupied / totalRoom);
    }
}
