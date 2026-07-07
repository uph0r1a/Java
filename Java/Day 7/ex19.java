public class ex19 {
    static boolean isMagicSquare(int[][] mat) {
        int n = mat.length, target = n * (n * n + 1) / 2, d1 = 0, d2 = 0;
        boolean[] visited = new boolean[n * n + 1];

        for (int i = 0; i < n; i++) {
            int rowSum = 0, colSum = 0;

            for (int j = 0; j < n; j++) {
                int valRow = mat[i][j], valCol = mat[j][i];

                if (valRow < 1 || valRow > n * n || visited[valRow])
                    return false;
                visited[valRow] = true;

                rowSum += valRow;
                colSum += valCol;

                if (i == j)
                    d1 += valRow;
                if (i + j == n - 1)
                    d2 += valRow;
            }

            if (rowSum != target || colSum != target)
                return false;
        }
        return d1 == target && d2 == target;
    }

    public static void main(String[] args) {
        int[][] mat = {
                { 2, 7, 6 },
                { 9, 5, 1 },
                { 4, 3, 8 }
        };

        System.out.println(isMagicSquare(mat) ? "Magic Square" : "Not a Magic Square");
    }
}
