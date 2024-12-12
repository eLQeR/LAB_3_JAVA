import java.util.concurrent.*;
import java.util.*;
import java.util.logging.*;

public class MatrixMultiplication {

    private static final Logger logger = Logger.getLogger(MatrixMultiplication.class.getName());

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // User input
        System.out.print("Enter the number of rows for matrix A: ");
        int rowsA = scanner.nextInt();
        System.out.print("Enter the number of columns for matrix A / rows for matrix B: ");
        int colsA = scanner.nextInt();
        System.out.print("Enter the number of columns for matrix B: ");
        int colsB = scanner.nextInt();
        System.out.print("Enter the minimum value for elements: ");
        int minValue = scanner.nextInt();
        System.out.print("Enter the maximum value for elements: ");
        int maxValue = scanner.nextInt();

        // Generate matrices
        int[][] matrixA = generateMatrix(rowsA, colsA, minValue, maxValue);
        int[][] matrixB = generateMatrix(colsA, colsB, minValue, maxValue);

        // Display matrices
        System.out.println("Matrix A:");
        printMatrix(matrixA);
        System.out.println("Matrix B:");
        printMatrix(matrixB);

        // Perform multiplication with Work Stealing
        ForkJoinPool pool = new ForkJoinPool();
        long startTime = System.nanoTime();
        int[][] resultWorkStealing = pool.invoke(new MultiplyTask(matrixA, matrixB, 0, rowsA));
        long endTime = System.nanoTime();

        System.out.println("Result using Work Stealing:");
        printMatrix(resultWorkStealing);
        System.out.println("Time taken: " + (endTime - startTime) / 1_000_000 + " ms");

        // Perform multiplication with Work Dealing (Executor Service)
        startTime = System.nanoTime();
        int[][] resultWorkDealing = multiplyWithExecutor(matrixA, matrixB);
        endTime = System.nanoTime();

        System.out.println("Result using Work Dealing:");
        printMatrix(resultWorkDealing);
        System.out.println("Time taken: " + (endTime - startTime) / 1_000_000 + " ms");

        scanner.close();
    }

    private static int[][] generateMatrix(int rows, int cols, int minValue, int maxValue) {
        Random random = new Random();
        int[][] matrix = new int[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                matrix[i][j] = random.nextInt(maxValue - minValue + 1) + minValue;
            }
        }
        return matrix;
    }

    private static void printMatrix(int[][] matrix) {
        for (int[] row : matrix) {
            System.out.println(Arrays.toString(row));
        }
    }

    private static int[][] multiplyWithExecutor(int[][] matrixA, int[][] matrixB) {
        int rowsA = matrixA.length;
        int colsB = matrixB[0].length;
        int[][] result = new int[rowsA][colsB];

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < rowsA; i++) {
            int row = i;
            futures.add(executor.submit(() -> {
                for (int j = 0; j < colsB; j++) {
                    for (int k = 0; k < matrixB.length; k++) {
                        result[row][j] += matrixA[row][k] * matrixB[k][j];
                    }
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                logger.log(Level.SEVERE, "Error during computation", e);
            }
        }

        executor.shutdown();
        return result;
    }

    static class MultiplyTask extends RecursiveTask<int[][]> {
        private final int[][] matrixA;
        private final int[][] matrixB;
        private final int startRow;
        private final int endRow;

        public MultiplyTask(int[][] matrixA, int[][] matrixB, int startRow, int endRow) {
            this.matrixA = matrixA;
            this.matrixB = matrixB;
            this.startRow = startRow;
            this.endRow = endRow;
        }

        @Override
        protected int[][] compute() {
            int rowsA = endRow - startRow;
            if (rowsA <= 10) { // Threshold for splitting tasks
                return computeDirectly();
            } else {
                int midRow = startRow + rowsA / 2;
                MultiplyTask task1 = new MultiplyTask(matrixA, matrixB, startRow, midRow);
                MultiplyTask task2 = new MultiplyTask(matrixA, matrixB, midRow, endRow);
                invokeAll(task1, task2);

                int[][] result1 = task1.join();
                int[][] result2 = task2.join();

                int[][] result = new int[matrixA.length][matrixB[0].length];
                System.arraycopy(result1, 0, result, 0, result1.length);
                System.arraycopy(result2, 0, result, result1.length, result2.length);
                return result;
            }
        }

        private int[][] computeDirectly() {
            int[][] result = new int[matrixA.length][matrixB[0].length];
            for (int i = startRow; i < endRow; i++) {
                for (int j = 0; j < matrixB[0].length; j++) {
                    for (int k = 0; k < matrixB.length; k++) {
                        result[i][j] += matrixA[i][k] * matrixB[k][j];
                    }
                }
            }
            return result;
        }
    }
}
