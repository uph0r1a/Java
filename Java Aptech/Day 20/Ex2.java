import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Ex2 {
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static class Member implements Serializable {
        private String memberID, memberName, address;

        public Member() {
        }

        public Member(String memberID, String memberName, String address) {
            this.memberID = memberID;
            this.memberName = memberName;
            this.address = address;
        }

        public String getMemberID() {
            return memberID;
        }

        public void setMemberID(String memberID) {
            this.memberID = memberID;
        }

        public String getMemberName() {
            return memberName;
        }

        public void setMemberName(String memberName) {
            this.memberName = memberName;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        @Override
        public String toString() {
            return "\nMember ID: " + memberID + "\nMember Name: " + memberName + "\nAddress: " + address;
        }
    }

    public static class ClubManager {
        private List<Member> members;

        public ClubManager() {
            this.members = new ArrayList<>();
        }

        public void input() throws IOException {
            System.out.println("Enter number of member to enter: ");
            int n;
            while (true) {
                try {
                    n = Integer.parseInt(br.readLine());
                    if (n >= 0) {
                        break;
                    }
                    System.out.println("Invalid number of member\nRe-enter number of member to enter: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
            for (int i = 0; i < n; i++) {
                System.out.println("\nMember " + (i + 1) + ": \nEnter member ID: ");
                String id;
                while (true) {
                    try {
                        id = br.readLine();
                        if (id.matches("^[TVA](MB|MT|MN)\\d{5}$")) {
                            break;
                        }
                        System.out.println("Invalid member ID\nRe-enter member ID: ");
                    } catch (Exception e) {
                        System.out.println("Error: " + e.getMessage());
                    }
                }

                System.out.println("Enter member name: ");
                String name = br.readLine();

                System.out.println("Enter address: ");
                String address = br.readLine();

                members.add(new Member(id, name, address));
            }
        }

        public void serialize() {
            try (OutputStream fOut = Files.newOutputStream(Path.of("/files/member_of_club.txt"))) {
                ObjectOutputStream out = new ObjectOutputStream(fOut);
                out.writeObject(members);
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        public List<Member> deserialize() {
            try (InputStream fIn = Files.newInputStream(Path.of("/files/member_of_club.txt"))) {
                ObjectInputStream in = new ObjectInputStream(fIn);
                @SuppressWarnings("unchecked")
                List<Member> deserializedList = (List<Member>) in.readObject();

                return deserializedList;
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
            return null;
        }

        public void display() {
            if (members.isEmpty()) {
                System.out.println("No members yet");
            } else {
                for (Member member : members) {
                    member.toString();
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        ClubManager clubManager = new ClubManager();

        clubManager.input();
        clubManager.serialize();
        clubManager.deserialize();
        clubManager.display();
    }
}
