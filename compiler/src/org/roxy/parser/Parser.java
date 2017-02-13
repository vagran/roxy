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

private class ParserNode implements AutoCloseable {
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
     * quantification points so far). For character node this is number of characters matched so
     * far.
     */
    int numMatched;
    /** Input position for first matched character. */
    InputPosition startPosition = new InputPosition(),
    /** Input position after last matched character. */
                  endPos = null;
    /** Node generation assigned when node created. Current generation incremented with each new
     * input character.
     */
    int generation;

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
        numMatched = 0;
        refCount = 1;
        generation = curPos.curOffset;
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

    @Override public
    void close()
    {
        Release();
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
private ArrayList<ParserNode> curTerm = new ArrayList<>(),
/** Previous list instance for curTerm. Used to swap with curTerm and save some GC. */
                              prevTerm = new ArrayList<>();

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
    ArrayList<ParserNode> swp = prevTerm;
    prevTerm = curTerm;
    curTerm = swp;

    if (!curTerm.isEmpty()) {
        for (ParserNode node: prevTerm) {
            //XXX EOF handling
            Grammar.QuantityStatus qs = node.grammarNode.CheckQuantity(node.numMatched);
            if (qs == Grammar.QuantityStatus.MAX_REACHED) {
                //XXX node closed
            } else {
                //XXX
                if (FindNodeDescending(c, node)) {
                    FindNodeSibling(c, node);
                }
            }
        }
    } else {
        // search from root
        ParserNode root = new ParserNode(grammar);
        FindNodeDescending(c, root);
        root.Release();
    }
    prevTerm.clear();
}

/** Find candidate node by checking next sibling node from the specified one.
 *
 * @param c Character to match. XXX EOF allowed?
 * @param node Node to start siblings finding.
 * @return True if reached end of grammar.
 */
private boolean
FindNodeSibling(int c, ParserNode node)
{
    //XXX
    return false;
}

/** Find candidate node by descending on nodes tree. curTerm is populated with matched nodes.
 *
 * @param c Character to match. Cannot be zero (EOF should be checked before this call).XXX
 * @param node Node to start descending from.
 * @return True if next sibling node also should be checked.
 */
private boolean
FindNodeDescending(int c, ParserNode node)
{
    if (node.grammarNode instanceof Grammar.CharNode) {
        if (((Grammar.CharNode)node.grammarNode).MatchChar(c)) {
            node.AddRef();
            node.numMatched++;
            curTerm.add(node);
            return false;
        }
        return node.numMatched >= node.grammarNode.GetMinQuantity();
    }

    if (node.grammarNode instanceof Grammar.SequenceNode) {
        Grammar.SequenceNode sn = (Grammar.SequenceNode)node.grammarNode;

        for (int i = 0; i < sn.nodes.length; i++) {
            try (ParserNode child = AllocateNode(sn.nodes[i])) {
                if (!FindNodeDescending(c, child)) {
                    return node.numMatched >= node.grammarNode.GetMinQuantity();
                }
            }
        }
        /* None of children matched, and next allowed. */
        return true;
    }

    if (node.grammarNode instanceof Grammar.VariantsNode) {
        Grammar.VariantsNode vn = (Grammar.VariantsNode)node.grammarNode;
        boolean nextWanted = false;
        for (int i = 0; i < vn.nodes.length; i++) {
            try (ParserNode child = AllocateNode(vn.nodes[i])) {
                if (FindNodeDescending(c, child)) {
                    nextWanted = true;
                }
            }
        }
        return nextWanted || node.numMatched >= node.grammarNode.GetMinQuantity();
    }

    throw new IllegalStateException("Invalid node type");
}

}
