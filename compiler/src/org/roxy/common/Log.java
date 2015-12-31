package org.roxy.common;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Logging helpers. */
public class Log {

public static String
GetStackTrace(Throwable t)
{
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    return sw.toString();
}

}
