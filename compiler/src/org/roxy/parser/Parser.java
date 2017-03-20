package org.roxy.parser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
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
    public int curOffset = 0, curLine = 1, curCol = 1;

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
            curCol = 1;
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
    /** Previous character node. Used only in character nodes. */
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
    int numMatched;
    /** Input position for the matched character. */
    InputPosition inputPosition;
    /** Node generation assigned when node created. Current generation incremented with each new
     * input character.
     */
    int generation;
    /** The matched character code. */
    int matchedChar;

    ParserNode(Grammar.Node grammarNode, ParserNode parent)
    {
        Initialize(grammarNode, parent);
    }

    void
    Initialize(Grammar.Node grammarNode, ParserNode parent)
    {
        this.parent = parent;
        if (parent != null) {
            parent.AddRef();
        }
        prev = null;
        this.grammarNode = grammarNode;
        astNode = null;
        numMatched = 0;
        refCount = 1;
        generation = curPos.curOffset;
        inputPosition = null;
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
    SetPrev(ParserNode prev)
    {
        assert this.prev == null;
        if (prev != null) {
            this.prev = prev;
            prev.AddRef();
        }
    }

    String
    GetHierarchyStr()
    {
        StringBuilder sb = new StringBuilder();
        ParserNode node = this;
        while (node != null) {
            if (node.grammarNode.name != null) {
                if (sb.length() != 0) {
                    sb.append(" <= ");
                }
                sb.append(node.grammarNode.name);
            }
            node = node.parent;
        }
        return sb.toString();
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
private final Ast ast = new Ast();
private Summary summary;
/** Current terminal nodes in different contexts. */
private ArrayList<ParserNode> curTerm = new ArrayList<>(),
/** Previous list instance for curTerm. Used to swap with curTerm and save some GC. */
                              prevTerm = new ArrayList<>();
/** Stores nodes being committed in current round. */
private final ArrayDeque<ParserNode> committedNodes = new ArrayDeque<>();
/** Previously created AST leaf node. */
private Ast.Node lastAstNode;

private ParserNode
AllocateNode(Grammar.Node grammarNode, ParserNode parent)
{
    if (freeNodes != null) {
        ParserNode node = freeNodes;
        freeNodes = node.prev;
        node.Initialize(grammarNode, parent);
        return node;
    }
    return new ParserNode(grammarNode, parent);
}

private void
FreeNode(ParserNode node)
{
    assert node.refCount == 0;
    node.prev = freeNodes;
    freeNodes = node;
}

/** Called when input text is fully processed. */
private void
Finalize()
{
    FindNextNode(0);
    if (lastAstNode != null) {
        CommitAstNode(lastAstNode, null);
    }
}

private void
ProcessChar(int c)
{
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

    if (!prevTerm.isEmpty()) {
        ParserNode eofNode = null;
        for (ParserNode node: prevTerm) {
            boolean eofExpected = FindNodeSibling(c, node);
            if (c == 0) {
                if (!eofExpected) {
                    continue;
                }
                if (eofNode == null) {
                    eofNode = node;
                } else {
                    //XXX should be eliminated by grammar verification?
                    throw new RuntimeException("Ambiguous EOF context");
                }
            }
        }

        System.out.println("Char: " + (char)c);
        for (ParserNode node: curTerm) {
            System.out.println(node.GetHierarchyStr());
        }

        if (c != 0 && curTerm.isEmpty()) {
            throw new RuntimeException(String.format("Unexpected character: %c", c));
        }
        if (c == 0 && eofNode == null) {
            throw new RuntimeException("Unexpected EOF");
        }
    } else {
        try (ParserNode root = AllocateNode(grammar, null)) {
            boolean eofExpected = FindNodeDescending(c, root, null);
            if (c == 0 && !eofExpected) {
                throw new RuntimeException("Empty file not allowed by grammar");
            }
        }
    }
    if (prevTerm.size() == 1) {
        ParserNode node = prevTerm.get(0);
        if (node.prev != null) {
            CommitSequence(node.prev);
            node.prev.Release();
            node.prev = null;
        }
        if (c == 0) {
            CommitSequence(node);
        }
    }
    for (ParserNode node: prevTerm) {
        node.Release();
    }
    prevTerm.clear();
}

/** Find candidate node by checking next sibling node from the specified one.
 *
 * @param c Character to match. Can be zero for EOF indication.
 * @param node Character node to start siblings finding.
 * @return True if reached end of grammar without match.
 */
//XXX prev node
private boolean
FindNodeSibling(int c, ParserNode node)
{
    ParserNode prevCharNode = node;
    Grammar.QuantityStatus qs = node.grammarNode.CheckQuantity(node.numMatched + 1);
    if (qs != Grammar.QuantityStatus.MAX_REACHED &&
        ((Grammar.CharNode)node.grammarNode).MatchChar(c)) {

        ParserNode newNode = AllocateNode(node.grammarNode, node.parent);
        node.numMatched = node.numMatched + 1;
        newNode.SetPrev(prevCharNode);
        curTerm.add(newNode);
    }
    if (qs == Grammar.QuantityStatus.NOT_ENOUGH) {
        return false;
    }

    node.AddRef();
    while (true) {
        if (node.grammarNode.next != null) {
            ParserNode newNode = AllocateNode(node.grammarNode.next, node.parent);
            node.Release();
            node = newNode;
            if (!FindNodeDescending(c, node, prevCharNode)) {
                node.Release();
                return false;
            }
            continue;
        }

        ParserNode newNode = node.parent;
        if (newNode != null) {
            newNode.AddRef();
        }
        node.Release();
        node = newNode;
        if (node == null) {
            return true;
        }

        /* Going up so this node is already matched once more. */
        int numMatched = node.numMatched + 1;
        qs = node.grammarNode.CheckQuantity(numMatched);
        if (qs != Grammar.QuantityStatus.MAX_REACHED) {
            try (ParserNode nextNode = AllocateNode(node.grammarNode, node.parent)) {
                nextNode.numMatched = numMatched;
                FindNodeDescending(c, nextNode, prevCharNode);
            }
        }
        if (qs == Grammar.QuantityStatus.NOT_ENOUGH) {
            node.Release();
            return false;
        }
    }
}

/** Find candidate node by descending on nodes tree. curTerm is populated with matched nodes.
 *
 * @param c Character to match. Can be zero for EOF indication.
 * @param node Node to start descending from.
 * @param prevCharNode Previous character node. Null for the first character.
 * @return True if next sibling node also should be checked.
 */
private boolean
FindNodeDescending(int c, ParserNode node, ParserNode prevCharNode)
{
    if (node.grammarNode instanceof Grammar.CharNode) {
        if (((Grammar.CharNode)node.grammarNode).MatchChar(c)) {
            node.inputPosition = curPos;
            node.matchedChar = c;
            node.SetPrev(prevCharNode);
            node.AddRef();
            curTerm.add(node);
        }
        return node.numMatched >= node.grammarNode.GetMinQuantity();
    }

    if (!CheckRecursion(node)) {
        return node.numMatched >= node.grammarNode.GetMinQuantity();
    }

    if (node.grammarNode instanceof Grammar.SequenceNode) {
        Grammar.SequenceNode sn = (Grammar.SequenceNode)node.grammarNode;

        for (int i = 0; i < sn.nodes.length; i++) {
            try (ParserNode child = AllocateNode(sn.nodes[i], node)) {
                if (!FindNodeDescending(c, child, prevCharNode)) {
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
            try (ParserNode child = AllocateNode(vn.nodes[i], node)) {
                if (FindNodeDescending(c, child, prevCharNode)) {
                    nextWanted = true;
                }
            }
        }
        return nextWanted || node.numMatched >= node.grammarNode.GetMinQuantity();
    }

    throw new IllegalStateException("Invalid node type");
}

/**
 * Check if the provided node is allowed to be instantiated based on recursion constraints.
 * @param node Node to check.
 * @return True if allowed, false if not.
 */
private boolean
CheckRecursion(ParserNode node)
{
    if (node.grammarNode.precedenceGroup == null) {
        return true;
    }
    ParserNode parent = node.parent;
    while (parent != null) {
        if (parent.generation != node.generation) {
            break;
        }
        if (parent.grammarNode.precedenceGroup == node.grammarNode.precedenceGroup) {
            //noinspection unchecked
            return ((Comparable<Object>)node.grammarNode.precedence)
                .compareTo(parent.grammarNode.precedence) > 0;
        }
        parent = parent.parent;
    }
    return true;
}

/** Commit nodes which are completely parsed so far.
 *
 * @param lastNode Last character node in character nodes sequence linked via "prev" field.
 */
private void
CommitSequence(ParserNode lastNode)
{
    while (lastNode != null) {
        committedNodes.addFirst(lastNode);
        lastNode = lastNode.prev;
    }
    committedNodes.forEach(this::CommitNode);
    committedNodes.clear();
}

private void
CommitNode(ParserNode node)
{
    Ast.Node newLeaf = null;
    int matchedChar = node.matchedChar;
    InputPosition inputPosition = node.inputPosition;
    Ast.Node curAstNode = null;

    while (node != null) {
        if (node.grammarNode.isVal) {
            boolean isNew = node.astNode == null;
            if (isNew) {
                node.astNode = ast.CreateNode();
                node.astNode.grammarNode = node.grammarNode;
                node.astNode.startPosition = inputPosition;
                if (newLeaf == null) {
                    newLeaf = node.astNode;
                }
            }
            if (node.grammarNode.wantValString) {
                node.astNode.AppendChar(matchedChar);
            }
            if (curAstNode != null) {
                node.astNode.AppendChild(curAstNode);
            }
            if (!isNew) {
//                curAstNode = null;
                break;
            }
            curAstNode = node.astNode;
        }
        node = node.parent;
    }

    //XXX root is currently assigned in Ast.Node.Commit()
//    if (curAstNode != null) {
//        assert ast.root == null;
//        ast.root = curAstNode;
//    }

    if (newLeaf != null && newLeaf != lastAstNode) {
        CommitAstNode(lastAstNode, newLeaf);
        lastAstNode = newLeaf;
    }
}

/** Commit fully parsed AST node.
 *
 * @param node Node to commit.
 * @param nextNode Next AST node. Can be null if EOF.
 */
private void
CommitAstNode(Ast.Node node, Ast.Node nextNode)
{
    while (node != null) {
        if (nextNode != null && nextNode.IsAncestor(node)) {
            break;
        }
        node.Commit(curPos, summary);
        node = node.parent;
    }
}

}
