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

    public InputPosition
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
        return this;
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
    public ParserNode parents;
    /** XXX */
    public ParserNode prev;
    /** Number of references from both next character nodes (which reference this node via "prev"
     * member) and child nodes (via "parents" member).
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
        parents = null;
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
            if (parents != null) {
                parents.Release();
            }
            if (prev != null) {
                prev.Release();
            }
            FreeNode(this);
        }
    }

    public void
    AddParent(ParserNode parent)
    {
        if (parents != null) {
            parent.prev = parents;
            parents.AddRef();
        }
        parents = parent;
        parent.AddRef();
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

/** Grammar tree root. */
private final Grammar.Node grammar;
/** Input characters stream provider. */
private final Reader reader;

/** Free nodes pool. */
private ParserNode freeNodes;
private InputPosition curPos = new InputPosition();
private Ast ast = new Ast();
private Summary summary;
private ParserNode lastNode;
/** Current correspondence of grammar nodes to parser nodes. Indexed by grammar nodes indices.
 * Element can be null if no mapping currently exists.
 */
private ParserNode[] grammarMap;

private ParserNode
AllocateNode(Grammar.Node grammarNode)
{
    if (freeNodes != null) {
        ParserNode node = freeNodes;
        freeNodes = node.prev;
        node.Initialize(grammarNode);
        return node;
    }
    return new ParserNode(grammarNode);
}

private void
FreeNode(ParserNode node)
{
    assert node.refCount == 0;
    node.prev = freeNodes;
    freeNodes = node;
}

/** Prepare parser for the first character processing. Creates initial parsing branches. */
private void
InitializeState()
{
    //XXX
    grammarMap = new ParserNode[grammar.GetGrammar().GetNodesCount()];
}

/** Called when input text is fully processed. */
private void
Finalize()
{
    FindNextNode(0);
    //XXX
}

private void
ProcessChar(int c)
{
    //XXX


    FindNextNode(c);

    curPos = new InputPosition(curPos).FeedChar(c);
}

/** Find next node to match new input character. Result stored in lastNode.
 *
 * @param c New input character, zero for end of file.
 */
private void
FindNextNode(int c)
{
    if (lastNode != null) {
        Grammar.QuantityStatus qs = lastNode.grammarNode.CheckQuantity(lastNode.numRepeated);
        if (qs == Grammar.QuantityStatus.MAX_REACHED) {
            //get next
        } else if (qs == Grammar.QuantityStatus.NOT_ENOUGH) {
            //must match
        } else if (qs == Grammar.QuantityStatus.ENOUGH) {
            //try this and next
        }
    } else {
        // search from root
    }
}

}
