package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import com.tradej.cli.output.Ansi;
import com.tradej.cli.output.RichTable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Command(name = "api", description = "REST API documentation — introspects Spring MVC controllers and lists all endpoints")
public final class CliApiCommand implements Callable<Integer> {

    @ParentCommand
    TradeCli root;

    @Option(names = "--filter", description = "Filter endpoints by path or controller name")
    String filter;

    @Option(names = "--controller", description = "Show only endpoints for a specific controller")
    String controllerFilter;

    static final Pattern CLASS_MAPPING = Pattern.compile(
            "@RequestMapping\\(\"([^\"]+)\"\\)");
    static final Pattern METHOD_MAPPING = Pattern.compile(
            "@(Get|Post|Put|Delete|Patch)Mapping\\(([^)]*)\\)");
    static final Pattern PATH_VALUE = Pattern.compile(
            "(?:value|path)\\s*=\\s*\"([^\"]+)\"");
    static final Pattern PLAIN_PATH = Pattern.compile(
            "@(?:Get|Post|Put|Delete|Patch)Mapping\\(\"([^\"]+)\"\\)");
    static final Pattern METHOD_NAME = Pattern.compile(
            "(?:public|private|protected)\\s+\\S+\\s+(\\w+)\\s*\\(");
    static final Pattern REQUEST_PARAM = Pattern.compile(
            "@RequestParam(?:\\([^)]*\\))?\\s+\\S+\\s+(\\w+)");
    static final Pattern PATH_VARIABLE = Pattern.compile(
            "@PathVariable(?:\\([^)]*\\))?\\s+\\S+\\s+(\\w+)");
    static final Pattern REQUEST_BODY = Pattern.compile(
            "@RequestBody\\s+(\\S+)");

    @Override
    public Integer call() {
        List<ApiEndpoint> endpoints = scanControllers();

        if (filter != null && !filter.isBlank()) {
            String lf = filter.toLowerCase();
            endpoints = endpoints.stream()
                    .filter(e -> e.path().toLowerCase().contains(lf)
                            || e.controller().toLowerCase().contains(lf)
                            || e.method().toLowerCase().contains(lf))
                    .toList();
        }

        if (controllerFilter != null && !controllerFilter.isBlank()) {
            String cf = controllerFilter.toLowerCase();
            endpoints = endpoints.stream()
                    .filter(e -> e.controller().toLowerCase().contains(cf))
                    .toList();
        }

        if (root.json()) {
            printJson(endpoints);
            return 0;
        }

        printTable(endpoints);
        return 0;
    }

    static List<ApiEndpoint> scanControllers() {
        String workspaceRoot = System.getProperty("trade.workspace.root", ".");
        Path controllersDir = Path.of(workspaceRoot, "app/src/main/java");
        if (!Files.exists(controllersDir)) {
            return List.of();
        }

        List<ApiEndpoint> endpoints = new ArrayList<>();
        try (Stream<Path> files = Files.walk(controllersDir)) {
            files.filter(f -> f.toString().endsWith("Controller.java"))
                    .forEach(f -> endpoints.addAll(parseController(f)));
        } catch (IOException e) {
            return List.of();
        }
        return endpoints;
    }

    static List<ApiEndpoint> parseController(Path file) {
        List<ApiEndpoint> endpoints = new ArrayList<>();
        try {
            String content = Files.readString(file);
            String controllerName = file.getFileName().toString().replace(".java", "");

            String basePath = "";
            Matcher classMatcher = CLASS_MAPPING.matcher(content);
            if (classMatcher.find()) {
                basePath = classMatcher.group(1);
            }

            String[] lines = content.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();

                Matcher methodMatcher = METHOD_MAPPING.matcher(line);
                Matcher plainMatcher = PLAIN_PATH.matcher(line);

                String httpMethod = null;
                String endpointPath = "";

                if (methodMatcher.find()) {
                    httpMethod = methodMatcher.group(1).toUpperCase();
                    String args = methodMatcher.group(2);
                    Matcher pathMatch = PATH_VALUE.matcher(args);
                    Matcher plainMatch = PLAIN_PATH.matcher(line);
                    if (pathMatch.find()) {
                        endpointPath = pathMatch.group(1);
                    } else if (plainMatch.find()) {
                        endpointPath = plainMatch.group(1);
                    }
                } else if (plainMatcher.find()) {
                    String annotation = plainMatcher.group(0);
                    if (annotation.contains("GetMapping")) httpMethod = "GET";
                    else if (annotation.contains("PostMapping")) httpMethod = "POST";
                    else if (annotation.contains("PutMapping")) httpMethod = "PUT";
                    else if (annotation.contains("DeleteMapping")) httpMethod = "DELETE";
                    else if (annotation.contains("PatchMapping")) httpMethod = "PATCH";
                    endpointPath = plainMatcher.group(1);
                }

                if (httpMethod != null) {
                    String methodName = "";
                    for (int j = i + 1; j < Math.min(i + 5, lines.length); j++) {
                        Matcher nameMatcher = METHOD_NAME.matcher(lines[j]);
                        if (nameMatcher.find()) {
                            methodName = nameMatcher.group(1);
                            break;
                        }
                    }

                    List<String> params = new ArrayList<>();
                    for (int j = i + 1; j < Math.min(i + 3, lines.length); j++) {
                        Matcher rpMatcher = REQUEST_PARAM.matcher(lines[j]);
                        while (rpMatcher.find()) params.add("@" + rpMatcher.group(1));
                        Matcher pvMatcher = PATH_VARIABLE.matcher(lines[j]);
                        while (pvMatcher.find()) params.add(":" + pvMatcher.group(1));
                        Matcher rbMatcher = REQUEST_BODY.matcher(lines[j]);
                        if (rbMatcher.find()) params.add("body:" + rbMatcher.group(1));
                    }

                    String fullPath = basePath + (endpointPath.startsWith("/") ? endpointPath : "/" + endpointPath);
                    fullPath = fullPath.replace("//", "/");

                    String category = categorize(controllerName);

                    endpoints.add(new ApiEndpoint(
                            httpMethod, fullPath, controllerName, methodName,
                            String.join(", ", params), category));
                }
            }
        } catch (IOException e) {
            // skip file
        }
        return endpoints;
    }

    static String categorize(String controllerName) {
        if (controllerName.contains("Order")) return "orders";
        if (controllerName.contains("Market") || controllerName.contains("Symbol")) return "market-data";
        if (controllerName.contains("Option") || controllerName.contains("Expired")) return "options";
        if (controllerName.contains("Analytic") || controllerName.contains("Depth")
                || controllerName.contains("Portfolio")) return "analytics";
        if (controllerName.contains("Scan")) return "scanner";
        if (controllerName.contains("News")) return "services";
        if (controllerName.contains("Backtest")) return "backtest";
        if (controllerName.contains("Replay")) return "replay";
        if (controllerName.contains("Pipeline") || controllerName.contains("Studio")
                || controllerName.contains("ReadModel")) return "pipeline";
        if (controllerName.contains("Admin") || controllerName.contains("Reconciliation")
                || controllerName.contains("Dashboard") || controllerName.contains("Historical")) {
            return "admin";
        }
        return "other";
    }

    private void printTable(List<ApiEndpoint> endpoints) {
        System.out.println(Ansi.bold("\n  REST API Endpoints\n"));

        RichTable table = RichTable.of("Method", "Path", "Controller", "Handler", "Parameters", "Category")
                .rowFormatter(row -> {
                    String method = row[0];
                    String colored = switch (method) {
                        case "GET" -> Ansi.green(method);
                        case "POST" -> Ansi.yellow(method);
                        case "PUT" -> Ansi.blue(method);
                        case "DELETE" -> Ansi.red(method);
                        default -> method;
                    };
                    return new String[]{colored, row[1], row[2], row[3], row[4], row[5]};
                });

        for (ApiEndpoint e : endpoints) {
            table.addRow(e.httpMethod(), e.path(), e.controller(), e.method(), e.params(), e.category());
        }
        table.print();

        var byCategory = endpoints.stream()
                .collect(java.util.stream.Collectors.groupingBy(ApiEndpoint::category,
                        java.util.stream.Collectors.counting()));
        System.out.println(Ansi.dim("  Total: " + endpoints.size() + " endpoints across "
                + endpoints.stream().map(ApiEndpoint::controller).distinct().count() + " controllers"));
        System.out.println(Ansi.dim("  Categories: " + byCategory.entrySet().stream()
                .map(e -> e.getKey() + "(" + e.getValue() + ")")
                .reduce((a, b) -> a + ", " + b).orElse("")));
    }

    private void printJson(List<ApiEndpoint> endpoints) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < endpoints.size(); i++) {
            if (i > 0) json.append(",");
            var e = endpoints.get(i);
            json.append(String.format(
                    "{\"method\":\"%s\",\"path\":\"%s\",\"controller\":\"%s\",\"handler\":\"%s\",\"params\":\"%s\",\"category\":\"%s\"}",
                    e.httpMethod(), e.path(), e.controller(), e.method(),
                    e.params().replace("\"", "\\\""), e.category()));
        }
        json.append("]");
        System.out.println(json);
    }

    record ApiEndpoint(String httpMethod, String path, String controller, String method, String params, String category) {}
}
