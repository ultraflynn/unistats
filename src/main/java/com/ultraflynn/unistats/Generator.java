package com.ultraflynn.unistats;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Generator {
    private static final DateTimeFormatter REPORT_DATE_INPUT_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy").withLocale(Locale.UK);
    private static final DateTimeFormatter REPORT_DATE_OUTPUT_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy").withLocale(Locale.UK);
    private static final DateTimeFormatter RESULTS_FILE_OUTPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd").withLocale(Locale.UK);
    private static final DateTimeFormatter INTERVIEW_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d',' yyyy").withLocale(Locale.UK);
    private static final DateTimeFormatter INTERVIEW_DAY_OUTPUT_FORMAT = DateTimeFormatter.ofPattern("EEEE").withLocale(Locale.UK);
    private static final DateTimeFormatter ACTION_DAY_OUTPUT_FORMAT = DateTimeFormatter.ofPattern("E").withLocale(Locale.UK);

    private static final int LENGTH_OF_TIME = " HH:MM:SS".length();

    public static void main(String[] args) {
        try {
            List<String> output = Lists.newArrayList();

            List<String> officers = loadOfficers(output);
            List<String> actions = loadActions(output);

            File input = new File("data/E-Uni Tools.html");
            Document doc = Jsoup.parse(input, Charset.defaultCharset().displayName());

            String rawDate = Arrays.stream(doc.select("p").first().text()
                    .split(" "))
                    .skip(3)
                    .collect(Collectors.joining(" "));

            output.add("");
            output.add("=======================FORUM POST===================================");
            output.add("");

            String reportDate = LocalDate.parse(rawDate.replaceFirst("^(\\d+).*? (\\w+ \\d+)", "$1 $2"), REPORT_DATE_INPUT_FORMAT)
                    .plusDays(6)
                    .format(REPORT_DATE_OUTPUT_FORMAT);
            output.add("[size=120]Report as of " + reportDate + "[/size]");

            List<Action> interviews = doc.select("div#actionList").first().select("div.tr").stream()
                    .filter(element -> !element.hasClass("headers"))
                    .map(action -> {
                        String rawInterviewDate = action.select("div.date").text();
                        LocalDate interviewDate = LocalDate.parse(rawInterviewDate.substring(0, rawInterviewDate.length() - LENGTH_OF_TIME), INTERVIEW_DATE_FORMAT);
                        String applicant = action.select("div.vChar").text().trim();
                        String officer = action.select("div.aChar").text().trim();
                        String result = action.select("div.action").text().trim();

                        return new Action(interviewDate, applicant, officer, result);
                    })
                    .filter(action -> officers.contains(action.officer))
                    .filter(action -> actions.contains(action.action))
                    .collect(Collectors.toList());

            output.add("[quote]");
            output.add("[size=120]Daily Summary[/size]");
            output.add("[list]");
            totalPerDay(interviews, output);
            output.add("[/list][/quote]");
            output.add("[quote][size=120][color=#80FF00]" + totalNumberOfInterviews(interviews) + "[/color] interviews completed this week[/size][list]"); // TODO
            totalPerAction(actions, interviews, output);
            output.add("[/list][/quote]");
            output.add("");
            output.add("[log]Summary by Officer");
            summaryPerOfficer(interviews, output);
            output.add("[/log]");
            output.add("");
            output.add("[Spoiler]");
            detailPerOfficer(interviews, actions, output);
            output.add("[/Spoiler]");

            output.add("");
            output.add("==========================ACTIONS=================================");
            output.add("");

            officers.forEach(officer -> {
                Map<String, Long> grouped = interviews.stream().collect(Collectors.groupingBy(o -> o.officer, Collectors.counting()));
                output.add(String.valueOf(Optional.ofNullable(grouped.get(officer)).orElse(0L)));
            });

            output.add("");
            output.add("===========================INTERVIEWS==============================");
            output.add("");

            officers.forEach(officer -> {
                Map<String, Long> grouped = interviews.stream().filter(action -> action.action.equals("Accept")).collect(Collectors.groupingBy(o -> o.officer, Collectors.counting()));
                output.add(String.valueOf(Optional.ofNullable(grouped.get(officer)).orElse(0L)));
            });

            output.add("");
            output.add("=============================YEARLY================================");
            output.add("");

            Map<String, List<Action>> grouped = interviews.stream().collect(Collectors.groupingBy(o -> o.action));
            actions.forEach(action -> {
                int n = Optional.ofNullable(grouped.get(action)).orElse(ImmutableList.of()).size();
                output.add(String.valueOf(n));
            });

            output.forEach(System.out::println);

            String resultFileName = "reports/" + LocalDate.parse(rawDate.replaceFirst("^(\\d+).*? (\\w+ \\d+)", "$1 $2"), REPORT_DATE_INPUT_FORMAT).plusDays(6).format(RESULTS_FILE_OUTPUT_FORMAT) + ".txt";
            Files.write(Paths.get(resultFileName), output.stream().collect(Collectors.joining("\n")).getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<String> loadOfficers(List<String> output) {
        List<String> officers = readFile("config/officers.txt");

        output.add("");
        output.add("======================LOADED OFFICERS================================");
        output.add("");
        output.add(officers.stream().collect(Collectors.joining("\n")));
        return officers;
    }

    private static List<String> loadActions(List<String> output) {
        List<String> actions = readFile("config/actions.txt");

        output.add("");
        output.add("======================LOADED ACTIONS================================");
        output.add("");
        output.add(actions.stream().collect(Collectors.joining("\n")));
        return actions;
    }

    // Load the file into a list and trim any whitespace
    private static List<String> readFile(String fileName) {
        try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
            return stream.map(String::trim).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static void detailPerOfficer(List<Action> interviews, List<String> actions, List<String> output) {
        Map<String, Long> order = interviews.stream().collect(Collectors.groupingBy(o -> o.officer, Collectors.counting()));
        Map<String, List<Action>> grouped = interviews.stream().collect(Collectors.groupingBy(o -> o.officer));

        order.entrySet().stream()
                .sorted((o1, o2) -> o2.getValue().intValue() - o1.getValue().intValue())
                .forEach(e -> detailPerSingleOfficer(e.getKey(), grouped.get(e.getKey()), actions, output));
    }

    private static void detailPerSingleOfficer(String officer, List<Action> interviews, List<String> actions, List<String> output) {
        Map<String, List<Action>> grouped = interviews.stream().collect(Collectors.groupingBy(o -> o.action));

        output.add(officer + " completed " + interviews.size() + " interviews");
        actions.forEach(a -> {
            int n = Optional.ofNullable(grouped.get(a)).orElse(ImmutableList.of()).size();
            if (n > 0) {
                output.add(n + " " + a);
            }
        });
        output.add("");
    }

    private static void summaryPerOfficer(List<Action> interviews, List<String> output) {
        Map<String, Long> grouped = interviews.stream().collect(Collectors.groupingBy(o -> o.officer, Collectors.counting()));
        grouped.entrySet().stream()
                .sorted((o1, o2) -> o2.getValue().intValue() - o1.getValue().intValue())
                .forEach((e) -> output.add(e.getValue() + " " + e.getKey()));
    }

    private static void totalPerDay(List<Action> interviews, List<String> output) {
        Map<LocalDate, List<Action>> grouped = interviews.stream().collect(Collectors.groupingBy(o -> o.date));
        List<LocalDate> dates = new ArrayList<>(grouped.keySet());
        Collections.sort(dates);

        dates.forEach(d -> {
            int n = grouped.get(d).size();
            output.add("[*] " + n + " on " + d.format(INTERVIEW_DAY_OUTPUT_FORMAT));
        });
    }

    private static void totalPerAction(List<String> actions, List<Action> interviews, List<String> output) {
        Map<String, List<Action>> grouped = interviews.stream().collect(Collectors.groupingBy(o -> o.action));

        actions.forEach(a -> {
            List<Action> selected = Optional.ofNullable(grouped.get(a)).orElse(ImmutableList.of());
            int n = selected.size();
            if (n > 0) {
                output.add("[*] " + n + " were " + a + " [" + breakdownByDay(selected) + "]");
            }
        });
    }

    private static String breakdownByDay(List<Action> interviews) {
        Map<LocalDate, List<Action>> grouped = interviews.stream().collect(Collectors.groupingBy(o -> o.date));
        List<LocalDate> dates = new ArrayList<>(grouped.keySet());
        Collections.sort(dates);

        return dates.stream().map(d -> d.format(ACTION_DAY_OUTPUT_FORMAT) + " " + grouped.get(d).size()).collect(Collectors.joining(", "));
    }

    private static String totalNumberOfInterviews(List<Action> interviews) {
        return String.valueOf(interviews.size());
    }
}
