package org.roxy.parser;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Parses the text into AST using the provided grammar. */
public class Parser {

public
Parser(Grammar.Node grammar, Reader reader)
{
    //XXX
}

public
Parser(Grammar.Node grammar, InputStream stream)
{
    this(grammar, new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
}

public
Parser(Grammar.Node grammar, String str)
{
    this(grammar, new StringReader(str));
}

public void/*XXX*/
Parse()
{
    //XXX
}

}
