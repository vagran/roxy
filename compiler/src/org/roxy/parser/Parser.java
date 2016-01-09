package org.roxy.parser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

/** Parses the text into AST using the provided grammar. */
public class Parser {

public
Parser(Grammar.Node grammar, Reader reader)
{
    this.grammar = grammar;
    this.reader = reader;
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
    InitializeState();
    //XXX

}

// /////////////////////////////////////////////////////////////////////////////////////////////////

private class ParserNode {
    /** Parent node in stack/tree. Next free node when in free list. */
    public ParserNode parent;
    /** Previous character node. Used for character nodes only. */
    public ParserNode prev;
    /** Number of next character nodes which reference this node via "prev" member. */
    public int nextNodeCount;
    /** Corresponding grammar node. */
    public Grammar.Node grammarNode;
    /** Assigned AST node if committed and valuable. */
    public Ast.Node astNode;
    /** Number the corresponding grammar node has been repeated before this node (number of spent
     * quantification points so far).
     */
    public int numRepeated;

    public
    ParserNode()
    {
        Initialize();
    }

    public void
    Initialize()
    {
        parent = null;
        grammarNode = null;
        astNode = null;
    }
}

private final Grammar.Node grammar;
private final Reader reader;

/** Free nodes pool. */
private ParserNode freeNodes;
/** Tips of current parsing branches. */
private final LinkedList<ParserNode> curBranches = new LinkedList<>();

private ParserNode
AllocateNode()
{
    if (freeNodes != null) {
        ParserNode node = freeNodes;
        freeNodes = node.parent;
        node.parent = null;
        return node;
    }
    return new ParserNode();
}

private void
FreeNode(ParserNode node)
{
    node.Initialize();
    node.parent = freeNodes;
    freeNodes = node;
}

/** Prepare parser for the first character processing. Creates initial parsing branches. */
private void
InitializeState()
{

}

}
