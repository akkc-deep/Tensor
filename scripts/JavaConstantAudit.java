import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

/** Audit inline strings and duplicate scalar values. Run: java scripts/JavaConstantAudit.java [data-plane]. */
public final class JavaConstantAudit {
    private record Value(Tree.Kind kind, Object value) {}
    private record Occurrence(String location, boolean constant) {}

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "data-plane" : args[0]).toAbsolutePath();
        List<Path> files;
        try (var paths = Files.walk(root)) {
            files = paths.filter(p -> p.toString().contains("/src/main/java/")
                    && p.toString().endsWith(".java")).sorted().toList();
        }
        if (files.isEmpty()) throw new IllegalArgumentException("No production Java files found: " + root);
        var values = new LinkedHashMap<Value, List<Occurrence>>();
        var exclusions = new TreeMap<String, Integer>();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null,
                    manager.getJavaFileObjectsFromPaths(files));
            var positions = Trees.instance(task).getSourcePositions();
            for (var unit : task.parse()) {
                Path file = Path.of(unit.getSourceFile().toUri());
                var seen = new HashSet<Long>(); // Record annotations also appear on the generated constructor.
                new TreePathScanner<Void, Void>() {
                    @Override public Void visitLiteral(LiteralTree literal, Void unused) {
                        long start = positions.getStartPosition(unit, literal);
                        if (!seen.add(start)) return null;
                        String exclusion = exclusion(literal, getCurrentPath(), file.getFileName().toString(),
                                unit, positions);
                        if (exclusion != null) {
                            exclusions.merge(exclusion, 1, Integer::sum);
                        }
                        if (exclusion == null || exclusion.equals("constant definition")) {
                            values.computeIfAbsent(new Value(literal.getKind(), literal.getValue()), k -> new ArrayList<>())
                                    .add(new Occurrence(root.relativize(file) + ":" + unit.getLineMap().getLineNumber(start),
                                            exclusion != null));
                        }
                        return null;
                    }
                }.scan(unit, null);
            }
        }
        if (diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR)) {
            diagnostics.getDiagnostics().forEach(System.err::println);
            System.exit(2);
        }
        int duplicates = 0;
        int inlineStrings = 0;
        for (var entry : values.entrySet()) {
            boolean string = entry.getKey().kind() == Tree.Kind.STRING_LITERAL;
            if (entry.getValue().stream().allMatch(Occurrence::constant)
                    || !string && entry.getValue().size() < 2) continue;
            if (string) inlineStrings += (int) entry.getValue().stream().filter(o -> !o.constant()).count();
            else duplicates++;
            System.out.println(entry.getKey().kind() + " " + String.valueOf(entry.getKey().value())
                    .replace("\n", "\\n").replace("\r", "\\r") + " (" + entry.getValue().size() + ")");
            entry.getValue().forEach(occurrence -> System.out.println("  " + occurrence.location()
                    + (occurrence.constant() ? " [constant]" : "")));
        }
        System.out.println("Scanned " + files.size() + " production Java files; inline strings: " + inlineStrings
                + "; duplicate non-string values: " + duplicates);
        System.out.println("Excluded literals: " + exclusions);
        if (inlineStrings != 0 || duplicates != 0) System.exit(1);
    }

    private static String exclusion(LiteralTree literal, TreePath path, String file,
                                    CompilationUnitTree unit, SourcePositions positions) {
        Object value = literal.getValue();
        if (value == null || value instanceof Boolean
                || value instanceof Number number && (number.doubleValue() == -1
                || number.doubleValue() == 0 || number.doubleValue() == 1)) return "basic";
        for (TreePath current = path.getParentPath(); current != null; current = current.getParentPath()) {
            Tree tree = current.getLeaf();
            if (tree instanceof AnnotationTree) return "annotation";
            if (tree instanceof VariableTree variable && current.getParentPath().getLeaf().getKind() == Tree.Kind.ENUM
                    && variable.getInitializer() instanceof NewClassTree call
                    && variable.getModifiers().getFlags().containsAll(
                    Set.of(javax.lang.model.element.Modifier.STATIC, javax.lang.model.element.Modifier.FINAL))) {
                int argument = argument(call.getArguments(), literal, unit, positions);
                if (argument >= 0 && isLiteralValue(call.getArguments().get(argument))) return "constant definition";
            }
            if (tree instanceof VariableTree variable && isScalar(variable.getType())
                    && isLiteralValue(variable.getInitializer())
                    && argument(List.of(variable.getInitializer()), literal, unit, positions) == 0
                    && variable.getModifiers().getFlags().containsAll(
                    Set.of(javax.lang.model.element.Modifier.STATIC, javax.lang.model.element.Modifier.FINAL))) {
                return "constant definition";
            }
            if (!(value instanceof String)) continue;
            if (tree instanceof ThrowTree) return "message";
            if (tree instanceof NewClassTree call) {
                String type = call.getIdentifier().toString();
                int argument = argument(call.getArguments(), literal, unit, positions);
                if (type.endsWith("Exception") || type.equals("Diagnostic")
                        && file.equals("DatasetDefinitionLoader.java") || argument == 1
                        && Set.of("FieldError", "FieldErrorResponse").contains(type)) return "message";
            }
            if (tree instanceof MethodInvocationTree call) {
                String selector = call.getMethodSelect().toString();
                String name = selector.substring(selector.lastIndexOf('.') + 1);
                int argument = argument(call.getArguments(), literal, unit, positions);
                if (selector.matches("(?i).*(logger|log)\\.(log|trace|debug|info|warn|error)")
                        || selector.equals("MDC.put")
                        || Set.of("super", "this").contains(selector) && inExceptionClass(current)
                        || name.equals("requireNonNull") && argument == 1
                        || Set.of("requireName", "requireNonBlank", "requireIdentifier", "requireText").contains(name)
                            && argument == 1
                        || file.equals("SchemaInspector.java") && name.equals("copy") && argument == 1
                        || file.equals("DatasetDefinition.java")
                            && Set.of("rejectDuplicates", "requireReferences").contains(name) && argument == 2
                        || file.equals("OperationLogger.java") && name.equals("observationFailed")) return "message";
            }
            if (tree instanceof MethodTree method) {
                String name = method.getName().toString();
                if (name.equals("message")
                        || Set.of("TushareBatchPolicies.java", "TushareErrorClassifier.java").contains(file)
                            && name.equals("failure")
                        || file.equals("DownloadTaskOperationLogger.java") && name.equals("error")
                        || file.equals("DatasetDefinitionLoader.java")
                            && Set.of("readReason", "schemaReason", "safeReason", "misconfigured").contains(name)) {
                    return "message";
                }
            }
        }
        return null;
    }

    private static boolean isScalar(Tree type) {
        return type instanceof PrimitiveTypeTree || type != null
                && Set.of("String", "java.lang.String", "Byte", "Short", "Integer", "Long", "Float", "Double",
                        "Character", "Boolean").contains(type.toString());
    }

    private static boolean isLiteralValue(ExpressionTree expression) {
        if (expression instanceof ParenthesizedTree parens) return isLiteralValue(parens.getExpression());
        if (expression instanceof UnaryTree unary) return isLiteralValue(unary.getExpression());
        if (expression instanceof BinaryTree binary && binary.getKind() == Tree.Kind.MULTIPLY) {
            return isLiteralValue(binary.getLeftOperand()) && isLiteralValue(binary.getRightOperand());
        }
        return expression instanceof LiteralTree;
    }

    private static boolean inExceptionClass(TreePath path) {
        for (TreePath current = path; current != null; current = current.getParentPath()) {
            if (current.getLeaf() instanceof ClassTree type) {
                String parent = String.valueOf(type.getExtendsClause());
                return type.getSimpleName().toString().endsWith("Exception")
                        || parent.endsWith("Exception") || parent.endsWith("Error") || parent.endsWith("Throwable");
            }
        }
        return false;
    }

    private static int argument(List<? extends ExpressionTree> arguments, Tree literal,
                                CompilationUnitTree unit, SourcePositions positions) {
        long at = positions.getStartPosition(unit, literal);
        for (int i = 0; i < arguments.size(); i++) {
            Tree argument = arguments.get(i);
            if (at >= positions.getStartPosition(unit, argument)
                    && at < positions.getEndPosition(unit, argument)) return i;
        }
        return -1;
    }
}
