package com.rehmaan.groupbot.messageParsing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StackTraceParser {
    // foo.bar.baz(1234.java:5)
    private static String STACK_TRACE_LINE_REGEX = "^\\tat ((?:(?:[\\d\\w]*\\.)*[\\d\\w]*))\\.([\\d\\w\\$]*)\\.([\\d\\w\\$]*)\\((?:(?:([\\d\\w]*\\.java):(\\d*))|([\\d\\w\\s]*))\\)$";
    private static Pattern STACK_TRACE_LINE_PATTERN = Pattern.compile(STACK_TRACE_LINE_REGEX);

    public static StackTrace parse(List<String> stackTraceLines) throws Exception {
        StringBuilder builder = new StringBuilder();

        for (String line : stackTraceLines) {
            builder.append(line).append("\n");
        }

        return parse(builder.substring(0, builder.length() - 1));
    }

    public static StackTrace parse(String stackTraceString) throws Exception {
        String[] lines = stackTraceString.split("\n");

        String firstLine = lines[0];
        List<StackTraceElement> stackTraceLines = new ArrayList<StackTraceElement>();

        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = STACK_TRACE_LINE_PATTERN.matcher(lines[i]);

            if (matcher.matches()) {
                String packageName = matcher.group(1);
                String className = matcher.group(2);
                String methodName = matcher.group(3);

                // pass null if no file information is available
                String fileName = null;
                if (matcher.group(4) != null) {
                    fileName = matcher.group(4);
                }

                // pass -1 if no line number information is available
                int lineNumber = -1;
                if (matcher.group(5) != null) {
                    lineNumber = Integer.valueOf(matcher.group(5));
                }

                // pass -2 as if the method containing the execution point is a native method
                if (matcher.group(6) != null && matcher.group(6).equals("Native Method")) {
                    lineNumber = -2;
                }

                StackTraceElement element = new StackTraceElement(
                        packageName + "." + className,
                        methodName,
                        fileName,
                        lineNumber
                );

                // check whether the parsed stack trace element corresponds to the original one
                if (!("\tat " + element.toString()).equals(lines[i])) {
                    throw new Exception("ERROR: Stack trace line could not be parsed to StackTraceElement:\n" +
                            "\tOriginal stack trace line:\t" + lines[i] + "\n" +
                            "\tParsed StackTraceElement:\t" + "\tat " + element.toString());
                }

                stackTraceLines.add(element);
            }
        }

        return new StackTrace(firstLine, stackTraceLines);
    }
}
