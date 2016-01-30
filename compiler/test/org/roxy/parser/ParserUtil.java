package org.roxy.parser;

import java.util.HashSet;

import static utils.Utils.AssertThrows;

public class ParserUtil {

public static class Record {
    public final int code, line, col;

    public
    Record(int code, int line, int col)
    {
        this.code = code;
        this.line = line;
        this.col = col;
    }

    public boolean
    Match(Parser.Summary.Record rec)
    {
        if (rec.inputPosition == null && line != -1) {
            return false;
        }
        if (this instanceof Error && rec.type != Parser.Summary.RecordType.ERROR) {
            return false;
        }
        if (this instanceof Warning && rec.type != Parser.Summary.RecordType.WARNING) {
            return false;
        }
        return rec.code == code && (rec.inputPosition == null ||
            (rec.inputPosition.curLine == line && rec.inputPosition.curCol == col));
    }
}

public static class Error extends Record {
    public
    Error(int code, int line, int col)
    {
        super(code, line, col);
    }

    public
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

public static void
TestParser(Grammar.Node grammar, String file, Class<? extends Throwable> expectedConstructException,
           Class<? extends Throwable> expectedParseException, Record... expectedRecords)
{
    if (expectedConstructException != null) {
        AssertThrows(expectedConstructException, () -> new Parser(grammar, file));
        return;
    }
    Parser parser = new Parser(grammar, file);
    if (expectedParseException != null) {
        AssertThrows(expectedParseException, parser::Parse);
        return;
    }
    Parser.Summary summary;
    try {
        summary = parser.Parse().GetSummary();
    } catch (Throwable t) {
        throw new RuntimeException(t);
    }
    System.out.println(summary);
    HashSet<Parser.Summary.Record> matchedRecords = new HashSet<>();
    for (Record rec: expectedRecords) {
        Parser.Summary.Record _rec = FindRecord(rec, summary);
        if (_rec == null) {
            throw new AssertionError("Expected record not found: " + rec);
        }
        matchedRecords.add(_rec);
    }
    for (Parser.Summary.Record rec: summary.records) {
        if (!matchedRecords.contains(rec)) {
            throw new AssertionError("Unexpected record: " + rec);
        }
    }
}

public static void
TestParser(Grammar.Node grammar, String file, Class<? extends Throwable> expectedConstructException)
{
    TestParser(grammar, file, expectedConstructException, null);
}

public static void
TestParser(Grammar.Node grammar, String file, Record... expectedRecords)
{
    TestParser(grammar, file, null, null, expectedRecords);
}

private static Parser.Summary.Record
FindRecord(Record rec, Parser.Summary summary)
{
    for (Parser.Summary.Record _rec: summary.records) {
        if (rec.Match(_rec)) {
            return _rec;
        }
    }
    return null;
}

}
