package org.roxy.parser;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Parses the text into AST using the provided grammar. */
public class Parser {

public interface ErrorCode {
    int INCOMPLETE_NODE = 0,
        AMBIGUOUS_SYNTAX = 1,
        PARSING_FAILED = 2,

        CUSTOM_START = 1000;
}

//XXX do not need warnings, only errors
public interface WarnCode {
    int CUSTOM_START = 1000;
}

public interface InfoCode {
    int INCOMPLETE_NODE_CANDIDATE = 0,
        AMBIGUOUS_SYNTAX_CANDIDATE = 1,

        CUSTOM_START = 1000;
}

/** Current position in input text. */
public static class InputPosition {
    public int curOffset = 0, curLine = 1, curCol = 0;

    public
    InputPosition()
    {}

    public
    InputPosition(InputPosition ip)
    {
        curOffset = ip.curOffset;
        curLine = ip.curLine;
        curCol = ip.curCol;
    }

    public void
    FeedChar(int c)
    {
        curOffset++;
        if (c == '\n' || wasCr) {
            curLine++;
            curCol = 0;
        } else if (c != '\r') {
            curCol++;
        }
        wasCr = c == '\r';
    }

    @Override public String
    toString()
    {
        return String.format("Line %d column %d (offset %d)", curLine, curCol, curOffset);
    }

    /** CR character was the previous one. */
    private boolean wasCr = false;
}

public
Parser(Grammar.Node grammar, Reader reader)
{
    if (!grammar.isVal) {
        throw new IllegalArgumentException("Grammar root node should have value");
    }
    this.grammar = grammar;
    this.reader = reader;
    InitializeState();
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

public Parser
Parse(Summary summary)
    throws IOException
{
    this.summary = summary;
    try {
        while (true) {
            int c = reader.read();
            if (c == -1) {
                Finalize();
                return this;
            }
            ProcessChar(c);
        }
    } catch (ParseException e) {
        summary.Error(e.inputPosition, ErrorCode.PARSING_FAILED, e.getMessage());
        return this;
    }
}

public Parser
Parse()
    throws IOException
{
    return Parse(new Summary());
}

public Summary
GetSummary()
{
    return summary;
}

public Ast
GetResult()
{
    return ast;
}

// /////////////////////////////////////////////////////////////////////////////////////////////////

private class ParserNode {
    /** Parent node in stack/tree. Next free node when in free list. */
    public ParserNode parent;
    /** Previous character node. Used for character nodes only. */
    public ParserNode prev;
    /** Number of references from both next character nodes (which reference this node via "prev"
     * member) and child nodes (via "parent" member).
     */
    public int refCount;
    /** Corresponding grammar node. Null for end-of-file node. */
    public Grammar.Node grammarNode;
    /** Assigned AST node if committed and valuable. */
    public Ast.Node astNode;
    /** Number the corresponding grammar node has been repeated before this node (number of spent
     * quantification points so far).
     */
    public int numRepeated;
    /** Character matched. */
    public int matchedChar = -1;
    /** Input position for matched character. */
    public InputPosition inputPosition = null;

    public
    ParserNode(Grammar.Node grammarNode)
    {
        Initialize(grammarNode);
    }

    public void
    Initialize(Grammar.Node grammarNode)
    {
        parent = null;
        prev = null;
        this.grammarNode = grammarNode;
        astNode = null;
        numRepeated = 0;
        refCount = 1;
    }

    public void
    AddRef()
    {
        refCount++;
    }

    public void
    Release()
    {
        assert refCount > 0;
        refCount--;
        if (refCount == 0) {
            if (parent != null) {
                parent.Release();
            }
            if (prev != null) {
                prev.Release();
            }
            FreeNode(this);
        }
    }

    public void
    SetParent(ParserNode parent)
    {
        assert this.parent == null;
        if (parent != null) {
            this.parent = parent;
            parent.AddRef();
        }
    }

    public void
    SetPrev(ParserNode prev)
    {
        assert this.prev == null;
        if (prev != null) {
            this.prev = prev;
            prev.AddRef();
        }
    }

    /** Find nearest AST node in parents chain. */
    public Ast.Node
    FindAstNode()
    {
        ParserNode node = this;
        while (node != null) {
            if (node.astNode != null) {
                return node.astNode;
            }
            node = node.parent;
        }
        return null;
    }

    /** Find nearest node with named grammar node in parents chain. */
    public ParserNode
    FindNamedNode()
    {
        ParserNode node = this;
        while (node != null) {
            if (node.grammarNode.name != null) {
                return node;
            }
            node = node.parent;
        }
        return null;
    }
}

private class ParseException extends RuntimeException {

    public
    ParseException(InputPosition inputPosition, String message)
    {
        super(message);
        this.inputPosition = new InputPosition(inputPosition);
    }

    private InputPosition inputPosition;
}

private final Grammar.Node grammar;
private final Reader reader;

/** Free nodes pool. */
private ParserNode freeNodes;
private InputPosition curPos = new InputPosition();
private Ast ast = new Ast();
private Summary summary;

private ParserNode
AllocateNode(Grammar.Node grammarNode)
{
    if (freeNodes != null) {
        ParserNode node = freeNodes;
        freeNodes = node.parent;
        node.Initialize(grammarNode);
        return node;
    }
    return new ParserNode(grammarNode);
}

private void
FreeNode(ParserNode node)
{
    assert node.refCount == 0;
    node.parent = freeNodes;
    freeNodes = node;
}

/** Prepare parser for the first character processing. Creates initial parsing branches. */
private void
InitializeState()
{
    //XXX
}

/** Called when input text is fully processed. */
private void
Finalize()
{
    //XXX
}

private void
ProcessChar(int c)
{
    //XXX
    curPos.FeedChar(c);
}

}
