package org.roxy.parser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

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
    Set(InputPosition ip)
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
    ParserNode parent;
    /** XXX */
    ParserNode prev;
    /** Number of references from both next character nodes (which reference this node via "prev"
     * member) and child nodes (via "parents" member).
     */
    int refCount;
    /** Corresponding grammar node. Null for end-of-file node. */
    Grammar.Node grammarNode;
    /** Assigned AST node if committed and valuable. */
    Ast.Node astNode;
    /** Number the corresponding grammar node has been repeated before this node (number of spent
     * quantification points so far).
     */
    int numRepeated;
    /** Input position for first matched character. */
    public InputPosition startPosition = new InputPosition(),
    /** Input position after last matched character. */
                         endPos = null;

    ParserNode(Grammar.Node grammarNode)
    {
        Initialize(grammarNode);
    }

    void
    Initialize(Grammar.Node grammarNode)
    {
        parent = null;
        prev = null;
        this.grammarNode = grammarNode;
        astNode = null;
        numRepeated = 0;
        refCount = 1;
    }

    void
    AddRef()
    {
        refCount++;
    }

    void
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

    void
    SetParent(ParserNode parent)
    {
        assert this.parent == null;
        parent.AddRef();
        this.parent = parent;
    }

    void
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
/** Current terminal nodes in different contexts. */
private final ArrayList<ParserNode> curTerm = new ArrayList<>();

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

/** Prepare parser for the first character processing. */
private void
InitializeState()
{
    //XXX
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
//    if (lastNode != null) {
//        Grammar.QuantityStatus qs = lastNode.grammarNode.CheckQuantity(lastNode.numRepeated);
//        if (qs == Grammar.QuantityStatus.MAX_REACHED) {
//            //get next
//        } else if (qs == Grammar.QuantityStatus.NOT_ENOUGH) {
//            //must match
//        } else if (qs == Grammar.QuantityStatus.ENOUGH) {
//            //try this and next
//        }
//    } else {
//        // search from root
//        ParserNode root = new ParserNode(grammar);
//        FindNodeDescending(c, root);
//        root.Release();
//    }
}

/** Find candidate node by descending on nodes tree.
 *
 * @param c Character to match.
 * @param start Node to start descending from.
 */
private void
FindNodeDescending(int c, ParserNode start)
{

}

}
