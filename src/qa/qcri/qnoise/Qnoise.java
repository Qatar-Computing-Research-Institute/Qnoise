/*
 * Copyright (c) Qatar Computing Research Institute, 2013.
 * Licensed under the MIT license <http://www.opensource.org/licenses/MIT>.
 */
package qa.qcri.qnoise;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.cli.*;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import qa.qcri.qnoise.util.Tracer;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Qnoise launcher.
 */
public class Qnoise {
    private static Options options;
    private static PrintStream tracer = System.err;

    public static void main(String[] args) {
        options = createQnoiseOption();
        CommandLineParser parser = new GnuParser();
        List<String[]> entries;
        CSVReader reader = null;
        CSVWriter writer = null;
        try {
            CommandLine line = parser.parse(options, args);
            if (line.hasOption("v")) {
                Tracer.setVerbose(true);
            }

            String fileName = line.getOptionValue("f");
            if (Files.notExists(Paths.get(fileName))) {
                throw new FileNotFoundException("Input file " + fileName + " does not exist.");
            }

            Object inputJSON = JSONValue.parse(new FileReader(fileName));
            if (inputJSON == null) {
                throw new IllegalArgumentException("Input file is not a valid JSON file.");
            }

            NoiseSpec spec =
                NoiseSpec.valueOf((JSONObject)inputJSON);
            NoiseReport report = new NoiseReport(spec);
            reader = new CSVReader(new FileReader(spec.getInputFile()));
            reader.readNext(); // skip the header
            entries = reader.readAll();
            report.addMetric(NoiseReport.Metric.InputRow, entries.size());

            switch (spec.getType()) {
                case Missing:
                    new NoiseGenerator().missingInject(spec, entries, report);
                    break;
                case Duplicate:
                    new NoiseGenerator().duplicateInject(spec, entries, report);
                    break;
            }

            fileName = line.getOptionValue("o");
            writer = new CSVWriter(new FileWriter(fileName));
            writer.writeAll(entries);
            writer.flush();
            report.appendMetric(NoiseReport.Metric.OutputFilePath, fileName);
            report.addMetric(NoiseReport.Metric.OutputRow, entries.size());

            report.print();

        } catch (MissingOptionException me) {
            printHelp();
        } catch (ParseException ex) {
            ex.printStackTrace();
            printHelp();
        } catch (Exception ex) {
            tracer.println("Exception : " + ex.getMessage());
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }

                if (writer != null) {
                    writer.close();
                }
            } catch (Exception ex) {}
        }
    }

    private static void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(
            100,
            "qnoise.sh -f <input JSON file> -o <output file>",
            "All the options:",
            options,
            null
        );
    }

    private static Options createQnoiseOption() {
        Options options = new Options();
        options.addOption(OptionBuilder.withDescription("Print this message.").create("help"));
        options.addOption(
            OptionBuilder.
                withArgName("file").
                isRequired().
                hasArg().
                withDescription("Input JSON file path.").
                withType(String.class).
                create("f")
        );

        options.addOption(
            OptionBuilder.
                withDescription("Verbose output.").
                create("v")
        );

        options.addOption(
            OptionBuilder.
                withArgName("output file").
                hasArg().
                withDescription("Output file path.").
                withType(String.class).
                create("o")
        );

        return options;
    }
}
