package com.rehmaan.groupbot.messageParsing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StackTraceCompare {
    private static final int lineThreshold = 2;
    private static final int refactorFunctionCountThreshold = 2;
    private static final int completeMatchAfterRefactorThreshold = 2;
    public static List<StackTraceElement> getImportantStacktraceElements(String stackTrace) throws Exception  {

        boolean flag = stackTrace.contains("Caused by");
        if(!flag) {
            List<StackTraceElement> allStackTraceElements  = StackTraceParser.parse(stackTrace).getStackTraceLines();
            List<StackTraceElement> importantElements = new ArrayList<>();

            int sizeAllStackTraceElements = allStackTraceElements.size();
            for(int i= 0; i < sizeAllStackTraceElements && importantElements.size() < 4; i++) {
                StackTraceElement element = allStackTraceElements.get(i);
                if(element.getClassName().startsWith("com.spr")) {
                    importantElements.add(element);
                }
            }
            if(importantElements.size() > 0) return importantElements;
        }

        else {

            int firstIndexOfCausedBy = stackTrace.indexOf("Caused by");
            stackTrace = stackTrace.substring(firstIndexOfCausedBy);

            String[] stackTraces = stackTrace.split("Caused by");
            int stackTracesLength = stackTraces.length;

            int i=1;
            while(i < stackTracesLength) {
                String s = stackTraces[i];
                List<StackTraceElement> allStackTraceElements  = StackTraceParser.parse(s).getStackTraceLines();
                List<StackTraceElement> importantLines = new ArrayList<>();

                int sizeAllStackTraceElements = allStackTraceElements.size();
                for(int j= 0; j < sizeAllStackTraceElements; j++) {
                    StackTraceElement element = allStackTraceElements.get(j);
                    if(element.getClassName().startsWith("com.spr")) {
                        importantLines.add(element);
                    }
                }
                i++;
                if(importantLines.size() > 0) return importantLines;
            }
        }


        List<StackTraceElement> important = new ArrayList<>();
        List<StackTraceElement> allElements = StackTraceParser.parse(stackTrace).getStackTraceLines();
        int allElementsSize = allElements.size();
        for(int i= 0; i < allElementsSize  && important.size() < 4; i++) {
            important.add(allElements.get(i));
        }
        return important;
    }

    private static String getExceptionType(String stackTrace) {
        int index = stackTrace.indexOf("Caused by");
        String str = stackTrace.substring(index);
        index = str.indexOf("Exception");
        if(index == -1) {
            return "";
        }
        // else start going forward and backward to this index and we will have exception type
        int end = index;
        while(end < stackTrace.length()) {
            if(stackTrace.charAt(end) != ' ' && stackTrace.charAt(end) != '\n') {
                end++;
            }
            else {
                break;
            }
        }
        end--;

        int start = index;
        while(start >=0) {
            if(stackTrace.charAt(start) != ' '  && stackTrace.charAt(start) != '\n') {
                start--;
            }
            else {
                break;
            }
        }
        return str.substring(start, end+1);
    }

    private static Boolean equalExceptionType(String stacktrace1, String stacktrace2) {
        String exception1= getExceptionType(stacktrace1);
        String exception2= getExceptionType(stacktrace2);
        return exception1.equals(exception2);
    }

    public static Boolean isEqual(String stackTrace1, String stackTrace2) throws Exception {
        if(stackTrace1.contains("Caused") && stackTrace2.contains("Caused")) {
            // compare the two exception types that were thrown
            if(!equalExceptionType(stackTrace1, stackTrace2)) {
                return false;
            }
        }

        // get the exceptions as well and compare that first
        List<StackTraceElement> elements1  = getImportantStacktraceElements(stackTrace1);
        List<StackTraceElement> elements2 = getImportantStacktraceElements(stackTrace2);

        int refactoredFunctionCount = 0;
        int completeMatch = 0;
        int completeMatchAfterRefactor = 0;
        int i=0, j=0;
        while(i < elements1.size() && j < elements2.size()){
            StackTraceElement e1 = elements1.get(i);
            StackTraceElement e2 = elements2.get(j);
            if(equalLines(e1, e2)) {
                if(refactoredFunctionCount == 0)  completeMatch++;
                else completeMatchAfterRefactor++;
            }
            else if(refactoredFunctionCount < refactorFunctionCountThreshold && inGreyArea(e1, e2)) {
                refactoredFunctionCount++;
            }
            else {
                break;
            }
            i++; j++;
        }

        if(completeMatch > 0) return true;
        if(completeMatchAfterRefactor > completeMatchAfterRefactorThreshold) {
            return true;
        }
        return false;
    }

    public static Boolean equalLines(StackTraceElement line1, StackTraceElement line2) {
        String className1 = line1.getClassName();
        String methodName1 = line1.getMethodName();
        String fileName1 =  line1.getFileName();
        int lineNumber1 = line1.getLineNumber();

        String className2 = line2.getClassName();
        String methodName2 = line2.getMethodName();
        String fileName2 =  line2.getFileName();
        int lineNumber2 = line2.getLineNumber();

        int delta = lineNumber2 >= lineNumber1 ? lineNumber2-lineNumber1 : lineNumber1-lineNumber2;
        return className1.equals(className2) && methodName1.equals(methodName2) && fileName1.equals(fileName2) && delta <= lineThreshold;
    }

    public static Boolean inGreyArea(StackTraceElement line1, StackTraceElement line2) {
        String className1 = line1.getClassName();
        String methodName1 = line1.getMethodName();
        String fileName1 =  line1.getFileName();

        String className2 = line2.getClassName();
        String methodName2 = line2.getMethodName();
        String fileName2 =  line2.getFileName();

        return className1.equals(className2) && methodName1.equals(methodName2) && fileName1.equals(fileName2);
    }
}


