import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) throws IOException {
        // verify command line arguments
        String[] verifiedArgs;
        try {
            verifiedArgs = verifyArgs(args);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return;
        }

        // process args 0-3 (table and csv options)
        int[][] table;
        String tablePath;
        try {
            String tablePathOrString = verifiedArgs[0];
            boolean encoded = verifiedArgs[1] != null;
            char delimiter = verifiedArgs[2] != null && !verifiedArgs[2].isEmpty() ? verifiedArgs[2].charAt(0) : ',';
            boolean header = verifiedArgs[3] != null;
            table = encoded ? getTable(tablePathOrString) : readCsv(tablePathOrString, delimiter, header);
            tablePath = encoded ? null : tablePathOrString;
        } catch (FileNotFoundException | IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return;
        }

        // process args 5+ (create and configure computation objects, including fds and options)
        Computation computation;
        try {
            boolean showProcess = verifiedArgs[5] != null;
            boolean identifyOnes = verifiedArgs[6] != null;
            boolean considerSubtables = verifiedArgs[7] != null;
            int randomisation = verifiedArgs[8] != null ? Integer.parseInt(verifiedArgs[8]) : 0;
            boolean closure = verifiedArgs[9] != null;
            String[] fds = Arrays.copyOfRange(verifiedArgs, 10, verifiedArgs.length);
            computation = createComputationObject(table, showProcess, identifyOnes, considerSubtables, randomisation, closure, fds);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return;
        }

        // compute information contents and measure runtime
        long start = System.currentTimeMillis();
        double[][] infContMat = computation.getInformationContentMatrix();
        long end = System.currentTimeMillis();
        double runtime = (end - start) / 1000.0;

        // process arg 4 (write information contents to output file)
        try {
            writeResultToOutputFile(verifiedArgs[4], infContMat);
        } catch (FileAlreadyExistsException e) {
            System.out.println(e.getMessage());
            return;
        }

        System.out.println(getOutputString(tablePath, computation.getFdsString(), infContMat, runtime));
    }

    private static String[] verifyArgs(String[] args) {
        int numOptions = 9;
        String[] verifiedOptions = new String[numOptions + 1];

        if (args[0].startsWith("-") || hasFdFormat(args[0])) {
            throw new IllegalArgumentException("parameter \"table_file\" or \"table_encoded\" missing");
        }

        verifiedOptions[0] = args[0];
        int i = 1;

        while (i < args.length && !hasFdFormat(args[i])) {
            switch (args[i++]) {
                case "-e" -> verifiedOptions[1] = "X";
                case "-d" -> {
                    if (i >= args.length || args[i].startsWith("-") || hasFdFormat(args[i])) {
                        throw new IllegalArgumentException("parameter for option -d missing");
                    }
                    if (args[i].length() != 1) {
                        throw new IllegalArgumentException("delimiter must be a single character");
                    }
                    verifiedOptions[2] = args[i++];
                }
                case "--header" -> verifiedOptions[3] = "X";
                case "--name" -> {
                    if (i >= args.length || args[i].startsWith("-") || hasFdFormat(args[i])) {
                        throw new IllegalArgumentException("parameter for option --name missing");
                    }
                    verifiedOptions[4] = args[i++];
                }

                case "--show-process" -> verifiedOptions[5] = "X";
                case "-i" -> verifiedOptions[6] = "X";
                case "-s" -> verifiedOptions[7] = "X";
                case "-r" -> {
                    if (i >= args.length || args[i].startsWith("-") || hasFdFormat(args[i])) {
                        throw new IllegalArgumentException("parameter for option -r missing");
                    }
                    try {
                        Integer.parseInt(args[i]);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("number of iterations must be an integer");
                    }
                    verifiedOptions[8] = args[i++];
                }
                case "--closure" -> verifiedOptions[9] = "X";

                default ->
                        throw new IllegalArgumentException(String.format("unexpected parameter \"%s\"", args[i - 1]));
            }
        }

        String[] fds = Arrays.copyOfRange(args, i, args.length);

        for (String fd : fds) {
            if (!hasFdFormat(fd)) {
                throw new IllegalArgumentException(String.format("fd \"%s\" incorrectly formatted%n" +
                        "correct format: l->r with l positive integers separated by commas and r a single integer%n" +
                        "example: 1,2,3->4", fd));
            }
        }

        String[] verifiedArgs = Arrays.copyOf(verifiedOptions, verifiedOptions.length + fds.length);
        System.arraycopy(fds, 0, verifiedArgs, verifiedOptions.length, fds.length);
        return verifiedArgs;
    }

    private static int[][] getTable(String tableStr) {
        String[] lines = tableStr.split(";");
        List<String[]> cells = new ArrayList<>();
        int cols = 0;

        for (String lineStr : lines) {
            String[] line = lineStr.split(",");
            cells.add(line);
            int c = line.length;

            if (c == 0 || line[0].isEmpty()) {
                continue;
            }

            if (cols == 0) {
                cols = c;
            } else if (cols != c) {
                throw new IllegalArgumentException("lines must have same number of cells");
            }
        }

        return stringArrListToIntMatrix(cells);
    }

    private static int[][] readCsv(String fileName, char delimiter, boolean header) throws IOException {
        try (FileReader fileReader = new FileReader(fileName);
             CSVReader csvReader = new CSVReaderBuilder(fileReader).withSkipLines(header ? 1 : 0)
                     .withCSVParser(new CSVParserBuilder().withSeparator(delimiter).build()).build()) {
            List<String[]> cells = csvReader.readAll();
            return stringArrListToIntMatrix(cells);
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("source file not found: " + fileName);
        } catch (IOException e) {
            throw new IOException("error reading file: " + fileName);
        } catch (CsvException e) {
            throw new RuntimeException("error reading file: " + fileName);
        }
    }

    private static Computation createComputationObject(int[][] table, boolean showProcess, boolean identifyOnes, boolean considerSubtables, int randomisation, boolean closure, String... fds) {
        Computation computation = new Computation(table, identifyOnes, considerSubtables, randomisation);

        if (showProcess) {
            computation.enableProcessedCount();
        }

        // add functional dependencies with check if fulfilled and indices inside bounds
        for (String fd : fds) {
            String[] leftRight = fd.split("->");
            int[] left = Stream.of(leftRight[0].split(",")).mapToInt(Integer::parseInt).map(x -> x - 1).toArray();
            int right = Integer.parseInt(leftRight[1]) - 1;
            computation.addFuncDepWithCheck(new FunctionalDependency(Arrays.stream(left).boxed().collect(Collectors.toSet()), Set.of(right)));
        }

        // add transitive closure
        if (closure) {
            long start = System.nanoTime();
            computation.addTransitiveClosure();
            long end = System.nanoTime();
            System.out.printf("%d ms for computing the transitive closure%n", (end - start) / 1000000);
        }

        return computation;
    }

    private static void writeResultToOutputFile(String outputPath, double[][] infContMat) throws IOException {
        if (outputPath != null) {
            writeMatrixToCsv(determineFilename(outputPath), infContMat);
        }
    }

    private static String getOutputString(String tablePath, String fdsString, double[][] infContMat, double runtime) {
        StringBuilder builder = new StringBuilder();

        if (tablePath != null) {
            builder.append("Source: ").append(tablePath).append("\n");
        }

        builder.append("FDs: ").append(fdsString).append("\n")
                .append(matrixToString(infContMat, "\t")).append("\n")
                .append("Runtime: ").append(runtime).append(" seconds");
        return builder.toString();
    }

    private static boolean hasFdFormat(String str) {
        return str.matches("[1-9][0-9]*(,[1-9][0-9]*)*->[1-9][0-9]*");
    }

    private static int[][] stringArrListToIntMatrix(List<String[]> cells) {
        int[][] table = new int[cells.size()][cells.get(0).length];
        int j = 0;

        for (String[] row : cells) {
            if (row.length > 0 && !row[0].isEmpty()) {
                for (int k = 0; k < row.length; k++) {
                    try {
                        table[j][k] = Integer.parseInt(row[k]);

                        if (table[j][k] <= 0) {
                            return encodeCells(cells);
                        }
                    } catch (NumberFormatException e) {
                        return encodeCells(cells);
                    }
                }
                j++;
            }
        }

        return table;
    }

    private static String determineFilename(String filename) {
        String[] nameExt = filename.split("\\.", 2);
        int i = 1;

        while (new File(filename).exists()) {
            filename = nameExt.length > 1 ? String.format("%s(%d).%s", nameExt[0], ++i, nameExt[1]) : String.format("%s(%d)", nameExt[0], ++i);
        }

        return filename;
    }

    private static void writeMatrixToCsv(String filepath, double[][] matrix) throws IOException {
        File parentFile = new File(filepath).getParentFile();
        File parentFileIt = parentFile;

        while (parentFileIt != null) {
            if (parentFileIt.isFile()) {
                throw new FileAlreadyExistsException(String.format("cannot create directory '%s': file exists", parentFileIt));
            }

            parentFileIt = parentFileIt.getParentFile();
        }

        if (parentFile != null) {
            String parentPath = parentFile.getAbsolutePath();

            if (!new File(parentPath).exists()) {
                Files.createDirectories(Paths.get(parentPath));
            }
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filepath))) {
            writer.write(matrixToString(matrix, ","));
            writer.write("\n");
        }
    }

    private static String matrixToString(double[][] matrix, String delimiter) {
        Function<double[], String> rowToString = row -> String.join(delimiter, Arrays.stream(row)
                .mapToObj(cell -> cell == 1 ? "1" : String.valueOf(cell)).toArray(String[]::new));
        String[] matrixConverted = Arrays.stream(matrix).map(rowToString).toArray(String[]::new);
        return String.join("\n", matrixConverted);
    }

    private static int[][] encodeCells(List<String[]> cells) {
        Map<String, Integer> stringToInt = new HashMap<>();
        int[][] table = new int[cells.size()][cells.get(0).length];
        int nextNumber = 1;

        for (int i = 0; i < cells.size(); i++) {
            for (int j = 0; j < cells.get(0).length; j++) {
                if (stringToInt.containsKey(cells.get(i)[j])) {
                    table[i][j] = stringToInt.get(cells.get(i)[j]);
                } else {
                    table[i][j] = nextNumber;
                    stringToInt.put(cells.get(i)[j], nextNumber++);
                }
            }
        }

        return table;
    }

}
