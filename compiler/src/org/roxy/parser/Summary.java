package org.roxy.parser;

import java.util.ArrayList;

/** Compilation summary result is reported into this class. Compilation progress can be monitored in
 * real time if this class is subclassed.
 */
public class Summary {

public enum RecordType {
    ERROR,
    WARNING,
    INFO,
    VERBOSE
}

public static class Record {
    public final RecordType type;
    /* -1 if not specified. */
    public final int code;
    /* May be null if not position-bound. */
    public final Parser.InputPosition inputPosition;
    public final String message;

    public
    Record(RecordType type, int code, Parser.InputPosition inputPosition,
           String message, Object... fmtArgs)
    {
        this.type = type;
        this.code = code;
        this.inputPosition = inputPosition;
        this.message = String.format(message, fmtArgs);
    }

    @Override public String
    toString() {
        StringBuilder sb = new StringBuilder();
        if (inputPosition != null) {
            sb.append(inputPosition.toString());
            sb.append(": ");
        }
        if (type == RecordType.ERROR) {
            sb.append("Error");
        } else if (type == RecordType.WARNING) {
            sb.append("Warning");
        }
        if (code != -1) {
            switch (type) {
            case ERROR:
                sb.append(" E");
                break;
            case WARNING:
                sb.append(" W");
                break;
            case INFO:
                sb.append('I');
                break;
            case VERBOSE:
                sb.append('V');
                break;
            default:
                throw new RuntimeException("Unhandled record type: " + type);
            }
            sb.append(code);
            sb.append(": ");
        } else if (type == RecordType.ERROR || type == RecordType.WARNING) {
            sb.append(": ");
        }
        sb.append(message);
        return sb.toString();
    }
}

public ArrayList<Record> records = new ArrayList<>();

public void
Error(Parser.InputPosition inputPosition, int code, String message, Object... fmtArgs)
{
    records.add(new Record(RecordType.ERROR, code, inputPosition, message, fmtArgs));
    numErrors++;
}

public void
Error(int code, String message, Object... fmtArgs)
{
    Error(null, code, message, fmtArgs);
}

public void
Error(Parser.InputPosition inputPosition, String message, Object... fmtArgs)
{
    Error(inputPosition, -1, message, fmtArgs);
}

public void
Error(String message, Object... fmtArgs)
{
    Error(null, -1, message, fmtArgs);
}

public void
Warning(Parser.InputPosition inputPosition, int code, String message, Object... fmtArgs)
{
    records.add(new Record(RecordType.WARNING, code, inputPosition, message, fmtArgs));
    numWarnings++;
}

public void
Warning(int code, String message, Object... fmtArgs)
{
    Warning(null, code, message, fmtArgs);
}

public void
Warning(Parser.InputPosition inputPosition, String message, Object... fmtArgs)
{
    Warning(inputPosition, -1, message, fmtArgs);
}

public void
Warning(String message, Object... fmtArgs)
{
    Warning(null, -1, message, fmtArgs);
}

public void
Info(Parser.InputPosition inputPosition, int code, String message, Object... fmtArgs)
{
    records.add(new Record(RecordType.INFO, code, inputPosition, message, fmtArgs));
}

public void
Info(Parser.InputPosition inputPosition, String message, Object... fmtArgs)
{
    Info(inputPosition, -1, message, fmtArgs);
}

public void
Info(int code, String message, Object... fmtArgs)
{
    Info(null, code, message, fmtArgs);
}

public void
Info(String message, Object... fmtArgs)
{
    Info(null, -1, message, fmtArgs);
}

public void
Verbose(Parser.InputPosition inputPosition, String message, Object... fmtArgs)
{
    records.add(new Record(RecordType.VERBOSE, -1, inputPosition, message, fmtArgs));
}

public void
Verbose(String message, Object... fmtArgs)
{
    Verbose(null, message, fmtArgs);
}

@Override public String
toString()
{
    StringBuilder sb = new StringBuilder();
    for (Record rec: records) {
        sb.append(rec.toString());
        sb.append('\n');
    }
    sb.append("===========================================================\n");
    sb.append(String.format("%d errors, %d warnings", numErrors, numWarnings));
    return sb.toString();
}

public final int
GetErrorsCount()
{
    return numErrors;
}

public final int
GetWarningsCount()
{
    return numWarnings;
}

private int numErrors, numWarnings;
}
