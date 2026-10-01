package com.kaziflow.custom;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * FormulaEngine — calculated fields for admin-defined modules, with NO arbitrary code execution.
 *
 * <p>A formula is parsed by this class's own recursive-descent parser against a fixed whitelist,
 * then evaluated in Java against a {@link Context}. Nothing is passed to a scripting engine and no
 * user text is ever concatenated into SQL (aggregates go through the Context, which uses bound
 * parameters and validated field keys).
 *
 * <pre>
 *   Grammar:  compare  := add [ (&lt; &gt; &lt;= &gt;= = &lt;&gt;) add ]
 *             add      := mul { (+ | -) mul }
 *             mul      := unary { (* | /) unary }
 *             unary    := - unary | primary
 *             primary  := number | {f_12} | FUNCTION(args) | ( compare )
 *   Functions: ROUND(x[,digits]) MIN(a,b,..) MAX(a,b,..) ABS(x) IF(cond,then,else)
 *              SUMOF(e3, f_12)  -- sum of field f_12 over the records of entity 3 that point at THIS record
 *              COUNTOF(e3)      -- how many records of entity 3 point at THIS record
 * </pre>
 * Formulas are STORED with immutable field keys ({f_12}) so renaming a label never breaks them; the
 * builder converts to/from labels with {@link CustomModuleService#labelsToKeys}.
 * A missing value or a division by zero makes the result empty (null) rather than an error.
 */
public final class FormulaEngine {

    public static class FormulaException extends Exception {
        private static final long serialVersionUID = 1L;
        public FormulaException(String m) { super(m); }
    }

    /** What a formula can read. Implemented by the record service. */
    public interface Context {
        /** Numeric value of a field of the current record, or null if empty/non-numeric. */
        Double value(String fieldKey);
        double sumOf(long entityId, String fieldKey);
        double countOf(long entityId);
    }

    public interface Node { Double eval(Context ctx); }

    private static final Set<String> FUNCTIONS = Set.of("ROUND", "MIN", "MAX", "ABS", "IF", "SUMOF", "COUNTOF");

    private final String src;
    private int pos = 0;
    private final Set<String> fieldRefs = new LinkedHashSet<>();

    private FormulaEngine(String src) { this.src = src; }

    /** A parsed formula plus the field keys it reads. */
    public record Parsed(Node root, Set<String> fieldRefs) {
        public Double evaluate(Context ctx) { return root.eval(ctx); }
    }

    public static Parsed parse(String formula) throws FormulaException {
        if (formula == null || formula.isBlank()) throw new FormulaException("The formula is empty.");
        FormulaEngine p = new FormulaEngine(formula);
        Node n = p.compare();
        p.skipWs();
        if (p.pos < p.src.length()) throw p.err("Unexpected '" + p.src.charAt(p.pos) + "'");
        return new Parsed(n, p.fieldRefs);
    }

    // ── parser ────────────────────────────────────────────────────────────

    private Node compare() throws FormulaException {
        Node left = add();
        skipWs();
        String op = null;
        for (String candidate : new String[]{"<=", ">=", "<>", "<", ">", "="}) {
            if (src.startsWith(candidate, pos)) { op = candidate; break; }
        }
        if (op == null) return left;
        pos += op.length();
        Node right = add();
        final String o = op;
        return ctx -> {
            Double a = left.eval(ctx), b = right.eval(ctx);
            if (a == null || b == null) return null;
            boolean r = switch (o) {
                case "<" -> a < b; case ">" -> a > b; case "<=" -> a <= b;
                case ">=" -> a >= b; case "=" -> Math.abs(a - b) < 1e-9; default -> Math.abs(a - b) >= 1e-9;
            };
            return r ? 1.0 : 0.0;
        };
    }

    private Node add() throws FormulaException {
        Node left = mul();
        while (true) {
            skipWs();
            if (peek('+')) { pos++; Node r = mul(); Node l = left; left = ctx -> bin(l.eval(ctx), r.eval(ctx), '+'); }
            else if (peek('-')) { pos++; Node r = mul(); Node l = left; left = ctx -> bin(l.eval(ctx), r.eval(ctx), '-'); }
            else return left;
        }
    }

    private Node mul() throws FormulaException {
        Node left = unary();
        while (true) {
            skipWs();
            if (peek('*')) { pos++; Node r = unary(); Node l = left; left = ctx -> bin(l.eval(ctx), r.eval(ctx), '*'); }
            else if (peek('/')) { pos++; Node r = unary(); Node l = left; left = ctx -> bin(l.eval(ctx), r.eval(ctx), '/'); }
            else return left;
        }
    }

    private Node unary() throws FormulaException {
        skipWs();
        if (peek('-')) { pos++; Node n = unary(); return ctx -> { Double v = n.eval(ctx); return v == null ? null : -v; }; }
        return primary();
    }

    private Node primary() throws FormulaException {
        skipWs();
        if (pos >= src.length()) throw err("The formula ends unexpectedly");
        char c = src.charAt(pos);
        if (c == '(') {
            pos++;
            Node n = compare();
            skipWs();
            if (!peek(')')) throw err("Missing ')'");
            pos++;
            return n;
        }
        if (c == '{') {
            int end = src.indexOf('}', pos);
            if (end < 0) throw err("Missing '}'");
            String key = src.substring(pos + 1, end).trim();
            if (!key.matches("f_\\d+")) throw err("Unknown field reference {" + key + "}");
            pos = end + 1;
            fieldRefs.add(key);
            return ctx -> ctx.value(key);
        }
        if (Character.isDigit(c) || c == '.') {
            int start = pos;
            while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) pos++;
            try { double d = Double.parseDouble(src.substring(start, pos)); return ctx -> d; }
            catch (NumberFormatException e) { throw err("Bad number '" + src.substring(start, pos) + "'"); }
        }
        if (Character.isLetter(c)) {
            int start = pos;
            while (pos < src.length() && Character.isLetterOrDigit(src.charAt(pos))) pos++;
            String name = src.substring(start, pos).toUpperCase(Locale.ROOT);
            if (!FUNCTIONS.contains(name)) throw err("Unknown function " + name);
            skipWs();
            if (!peek('(')) throw err("Expected '(' after " + name);
            pos++;
            return function(name);
        }
        throw err("Unexpected '" + c + "'");
    }

    private Node function(String name) throws FormulaException {
        if (name.equals("SUMOF") || name.equals("COUNTOF")) {
            long entityId = entityRef();
            String fieldKey = null;
            if (name.equals("SUMOF")) {
                skipWs();
                if (!peek(',')) throw err("SUMOF needs two arguments, e.g. SUMOF(e3, f_12)");
                pos++;
                skipWs();
                int start = pos;
                while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) pos++;
                fieldKey = src.substring(start, pos);
                if (!fieldKey.matches("f_\\d+")) throw err("Expected a field key like f_12");
            }
            skipWs();
            if (!peek(')')) throw err("Missing ')'");
            pos++;
            final String fk = fieldKey;
            return name.equals("SUMOF") ? ctx -> ctx.sumOf(entityId, fk) : ctx -> ctx.countOf(entityId);
        }
        List<Node> args = new ArrayList<>();
        skipWs();
        if (!peek(')')) {
            while (true) {
                args.add(compare());
                skipWs();
                if (peek(',')) { pos++; continue; }
                break;
            }
        }
        if (!peek(')')) throw err("Missing ')'");
        pos++;
        int n = args.size();
        switch (name) {
            case "ABS" -> { need(name, n == 1, "1 argument");
                return ctx -> { Double v = args.get(0).eval(ctx); return v == null ? null : Math.abs(v); }; }
            case "ROUND" -> { need(name, n == 1 || n == 2, "1 or 2 arguments");
                return ctx -> {
                    Double v = args.get(0).eval(ctx);
                    if (v == null) return null;
                    Double d = n == 2 ? args.get(1).eval(ctx) : Double.valueOf(0);
                    if (d == null) return null;
                    double f = Math.pow(10, Math.max(0, Math.min(10, d.intValue())));
                    return Math.round(v * f) / f;
                }; }
            case "MIN", "MAX" -> { need(name, n >= 2, "at least 2 arguments");
                final boolean min = name.equals("MIN");
                return ctx -> {
                    Double best = null;
                    for (Node a : args) {
                        Double v = a.eval(ctx);
                        if (v == null) return null;
                        best = best == null ? v : (min ? Math.min(best, v) : Math.max(best, v));
                    }
                    return best;
                }; }
            case "IF" -> { need(name, n == 3, "3 arguments: IF(condition, then, else)");
                return ctx -> {
                    Double cond = args.get(0).eval(ctx);
                    return (cond != null && cond != 0) ? args.get(1).eval(ctx) : args.get(2).eval(ctx);
                }; }
            default -> throw err("Unknown function " + name);
        }
    }

    private long entityRef() throws FormulaException {
        skipWs();
        if (!peek('e')) throw err("Expected an entity reference like e3");
        int start = ++pos;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        if (start == pos) throw err("Expected an entity reference like e3");
        return Long.parseLong(src.substring(start, pos));
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static Double bin(Double a, Double b, char op) {
        if (a == null || b == null) return null;
        return switch (op) {
            case '+' -> a + b; case '-' -> a - b; case '*' -> a * b;
            default -> b == 0 ? null : a / b;
        };
    }

    private void need(String fn, boolean ok, String what) throws FormulaException {
        if (!ok) throw err(fn + " takes " + what);
    }
    private boolean peek(char c) { return pos < src.length() && src.charAt(pos) == c; }
    private void skipWs() { while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++; }
    private FormulaException err(String m) { return new FormulaException(m + " (at position " + (pos + 1) + ")"); }
}
