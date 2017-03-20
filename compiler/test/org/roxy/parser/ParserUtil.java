package org.roxy.parser;

import java.util.HashSet;
import java.util.Map;

import static utils.Utils.AssertThrows;

public class ParserUtil {

static class Record {
    final int code, line, col;

    Record(int code, int line, int col)
    {
        this.code = code;
        this.line = line;
        this.col = col;
    }

    boolean
    Match(Summary.Record rec)
    {
        if (rec.inputPosition == null && line != -1) {
            return false;
        }
        if (this instanceof Error && rec.type != Summary.RecordType.ERROR) {
            return false;
        }
        if (this instanceof Warning && rec.type != Summary.RecordType.WARNING) {
            return false;
        }
        return rec.code == code && (rec.inputPosition == null ||
            (rec.inputPosition.curLine == line && rec.inputPosition.curCol == col));
    }
}

public static class Error extends Record {
    Error(int code, int line, int col)
    {
        super(code, line, col);
    }

    Error(int code)
    {
        super(code, -1, -1);
    }

    public @Override String
    toString()
    {
        return String.format("Line %d column %d: E%d", line, col, code);
    }
}

public static class Warning extends Record {
    public
    Warning(int code, int line, int col)
    {
        super(code, line, col);
    }

    public
    Warning(int code)
    {
        super(code, -1, -1);
    }

    public @Override String
    toString()
    {
        return String.format("Line %d column %d: W%d", line, col, code);
    }
}

private static void
VerifySummary(Summary summary, Record... expectedRecords)
{
    HashSet<Summary.Record> matchedRecords = new HashSet<>();
    for (Record rec: expectedRecords) {
        Summary.Record _rec = FindRecord(rec, summary);
        if (_rec == null) {
            throw new AssertionError("Expected record not found: " + rec);
        }
        matchedRecords.add(_rec);
    }
    for (Summary.Record rec: summary.records) {
        if (rec.type != Summary.RecordType.ERROR && rec.type != Summary.RecordType.WARNING) {
            continue;
        }
        if (!matchedRecords.contains(rec)) {
            throw new AssertionError("Unexpected record: " + rec);
        }
    }
}

private static Parser
TestParser(Grammar.Node grammar, String file, Class<? extends Throwable> expectedConstructException,
           Class<? extends Throwable> expectedParseException, Record... expectedRecords)
{
    if (expectedConstructException != null) {
        AssertThrows(expectedConstructException, () -> new Parser(grammar, file));
        return null;
    }
    Parser parser = new Parser(grammar, file);
    if (expectedParseException != null) {
        AssertThrows(expectedParseException, parser::Parse);
        return null;
    }
    Summary summary;
    try {
        summary = parser.Parse().GetSummary();
    } catch (Throwable t) {
        throw new RuntimeException(t);
    }
    System.out.println(summary);
    VerifySummary(summary, expectedRecords);
    return parser;
}

static Parser
TestParser(Grammar.Node grammar, String file, Class<? extends Throwable> expectedConstructException)
{
    return TestParser(grammar, file, expectedConstructException, null);
}

static Parser
TestParser(Grammar.Node grammar, String file, Record... expectedRecords)
{
    return TestParser(grammar, file, null, null, expectedRecords);
}

private static Summary.Record
FindRecord(Record rec, Summary summary)
{
    for (Summary.Record _rec: summary.records) {
        if (rec.Match(_rec)) {
            return _rec;
        }
    }
    return null;
}

static void
VerifyResult(Map<String, ?> result, Map<String, ?> expected)
{
    if (result.size() != expected.size()) {
        throw new RuntimeException(String.format("Unexpected result size: %d, expected %d",
                                                 result.size(), expected.size()));
    }
    for (String key: expected.keySet()) {
        if (!result.containsKey(key)) {
            throw new RuntimeException("Expected value not found: " + key);
        }
        if (!expected.get(key).equals(result.get(key))) {
            throw new RuntimeException(String.format("Unexpected value: [%s] = %s, expected %s",
                                                     key, result.get(key), expected.get(key)));
        }
    }
}

}
