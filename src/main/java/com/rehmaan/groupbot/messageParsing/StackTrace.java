package com.rehmaan.groupbot.messageParsing;

import java.util.ArrayList;
import java.util.List;

public class StackTrace {
    private String firstLine;

    private List<StackTraceElement> stackTraceLines;

    public StackTrace(String firstLine, List<StackTraceElement> stackTraceLines) {
        this.firstLine = firstLine;
        this.stackTraceLines = stackTraceLines;
    }


    public String getFirstLine() {
        return this.firstLine;
    }

    public List<StackTraceElement> getStackTraceLines() {
        return this.stackTraceLines;
    }
}