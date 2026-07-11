public class Ex9 {
    public static class TextDocument {
        private String str;

        public TextDocument() {
            this.str = "";
        }

        public TextDocument(String str) {
            this.str = str;
        }

        public int getNumberOfWord() {
            if (str == null || str.trim().isEmpty()) {
                return 0;
            }
            return str.trim().split("\\s+").length;
        }

        public int getAOccurrences() {
            int count = 0;

            for (char c : str.toCharArray()) {
                if (Character.toUpperCase(c) == 'A') {
                    count++;
                }
            }

            return count;
        }

        public String getNormalizeText() {
            return str.trim().replaceAll("\\s{2,}", " ");
        }
    }

    public static void main(String[] args) {
        TextDocument doc = new TextDocument("  Hello   world! This is  a test.  ");

        System.out.println(doc.getNumberOfWord() + doc.getAOccurrences() + doc.getNormalizeText());
    }
}