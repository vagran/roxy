package org.roxy.parser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;

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
    FindRecursions(grammar, new ArrayDeque<>());
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
/** Tips of current parsing branches. */
private ArrayList<ParserNode> curBranches = new ArrayList<>(),
/** Newly created branches for next character matching. */
    nextBranches = new ArrayList<>();
private ArrayDeque<Grammar.Node> branchesStack = new ArrayDeque<>();
private InputPosition curPos = new InputPosition(),
/** Previous character position, immutable. */
    prevPos;
private Ast ast = new Ast();
private Ast.Node lastAstNode;
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
    if (CreateBranches(AllocateNode(grammar), null, branchesStack)) {
        /* Create also EOF node if allowed. */
        nextBranches.add(new ParserNode(null));
    }
    SwapBranches();
}

/** Create branches for the specified node (traversing its children if necessary). Branches are
 * populated in "nextBranches" member.
 *
 * @param node Node to create branches for.
 * @param prevNode Previous matched character node, null if none.
 * @param grammarStack Current stack of grammar nodes. Used to prevent from recursion. The recursion
 *                     can be used in grammar definition but not all recursion case are acceptable.
 * @return True to add also next sibling node (propagated to parent if last child node).
 */
private boolean
CreateBranches(ParserNode node, ParserNode prevNode, ArrayDeque<Grammar.Node> grammarStack)
{
    if (grammarStack.contains(node.grammarNode)) {
        throw new IllegalStateException("Invalid grammar recursion detected " +
            "(instant recursive match)\n" + node.grammarNode.toString());
    }
    grammarStack.push(node.grammarNode);
    boolean addNext = false;

    if (node.grammarNode instanceof Grammar.SequenceNode) {
        boolean pendingAdd = false;
        for (Grammar.Node childGrammarNode: node.grammarNode) {
            ParserNode childNode = AllocateNode(childGrammarNode);
            childNode.SetParent(node);
            pendingAdd = CreateBranches(childNode, prevNode, grammarStack);
            if (!pendingAdd) {
                break;
            }
        }
        if (pendingAdd) {
            addNext = true;
        }

    } else if (node.grammarNode instanceof Grammar.VariantsNode) {
        for (Grammar.Node childGrammarNode: node.grammarNode) {
            ParserNode childNode = AllocateNode(childGrammarNode);
            childNode.SetParent(node);
            if (CreateBranches(childNode, prevNode, grammarStack)) {
                addNext = true;
            }
        }

    } else if (node.grammarNode instanceof Grammar.CharNode) {
        nextBranches.add(node);
        node.SetPrev(prevNode);
    }

    if (node.grammarNode.CheckQuantity(node.numRepeated) != Grammar.QuantityStatus.NOT_ENOUGH) {
        addNext = true;
    }

    grammarStack.pop();
    return addNext;
}

/** Called when input text is fully processed. */
private void
Finalize()
{
    /* Check if we have end-of-file node in current branches list. If there are several ones then
     * there is an ambiguity. If there is no end-of-file node then there is incomplete node(s).
     */
    int numEof = 0;
    ParserNode eofBranch = null;
    HashSet<ParserNode> namedNodes = new HashSet<>();
    for (ParserNode branch: curBranches) {
        if (branch.grammarNode == null) {
            numEof++;
            eofBranch = branch;
        } else {
            namedNodes.add(branch.FindNamedNode());
        }
    }

    if (numEof == 0) {
        if (namedNodes.size() > 1) {
            summary.Error(curPos, ErrorCode.INCOMPLETE_NODE,
                          "Incomplete syntax (unterminated elements follow):");
            for (ParserNode node: namedNodes) {
                summary.Info(node.inputPosition, InfoCode.INCOMPLETE_NODE_CANDIDATE,
                             "Incomplete element candidate: %s",
                             node.grammarNode.name);
            }
        } else {
            ParserNode node = namedNodes.iterator().next();
            summary.Error(node.inputPosition, ErrorCode.INCOMPLETE_NODE,
                          "Incomplete %s", node.grammarNode.name);
        }

    } else if (numEof > 1) {
        summary.Error(curPos, ErrorCode.AMBIGUOUS_SYNTAX, "Ambiguous syntax");

    } else {
        CommitBranch(eofBranch.prev);
    }

    HashSet<Ast.Node> astNodes = new HashSet<>();
    for (ParserNode branch: curBranches) {
        if (branch.grammarNode == null) {
            if (numEof > 1) {
                Ast.Node astNode = branch.FindAstNode();
                if (astNodes.add(astNode)) {
                    summary.Info(astNode.startPosition,
                                 InfoCode.AMBIGUOUS_SYNTAX_CANDIDATE,
                                 "Ambiguous syntax candidate: %s",
                                 astNode.Describe());
                }
            }
        }
        branch.Release();
    }
    curBranches.clear();

    /* Commit all uncommitted AST nodes. */
    CommitAstNodes(null);
}

private void
ProcessChar(int c)
{
    int numBranchesMatched = 0;
    ParserNode matchedBranch = null;
    InputPosition _curPos = new InputPosition(curPos);
    for (ParserNode node: curBranches) {
        if (node.grammarNode == null || !((Grammar.CharNode)node.grammarNode).MatchChar(c)) {
            node.Release();
            continue;
        }
        node.matchedChar = c;
        node.inputPosition = _curPos;
        numBranchesMatched++;
        matchedBranch = node;

        /* Find candidates for next character matching. */
        FindNextCharNodes(node);
        node.Release();
    }

    if (numBranchesMatched == 0) {
        throw new ParseException(curPos, "Invalid syntax");
    }

    SwapBranches();

    System.out.format("'%c' at %s, %d branches\n", c, curPos, curBranches.size());//XXX

    if (numBranchesMatched == 1) {
        CommitBranch(matchedBranch);
    }
    prevPos = _curPos;

    curPos.FeedChar(c);
}

/** Make branches in "nextBranches" member be current branches ("curBranches" member). Reference to
 * nodes in curBranches should already be released.
 */
private void
SwapBranches()
{
    curBranches.clear();
    ArrayList<ParserNode> swap = curBranches;
    curBranches = nextBranches;
    nextBranches = swap;
}

/** Find candidates for matching next character after the just matched node. Candidates are stored
 * in "nextBranches" list.
 * @param matchedNode Matched character node.
 */
private void
FindNextCharNodes(ParserNode matchedNode)
{
    branchesStack.clear();
    ParserNode node = matchedNode;
matchedNodeLoop:
    while (node != null) {
        int numMatches = node.numRepeated + 1;
        if (node.grammarNode.CheckQuantity(numMatches) != Grammar.QuantityStatus.MAX_REACHED) {
            /* Create new instance for the same node. */
            ParserNode newNode = AllocateNode(node.grammarNode);
            newNode.numRepeated = numMatches;
            newNode.SetParent(node.parent);
            if (!CreateBranches(newNode, matchedNode, branchesStack)) {
                break;
            }
        }
        /* Create next sibling node. */
        while (true) {
            Grammar.Node nextGrammarNode = node.grammarNode.GetNextSibling();
            if (nextGrammarNode == null) {
                node = node.parent;
                continue matchedNodeLoop;
            }
            ParserNode newNode = AllocateNode(nextGrammarNode);
            newNode.SetParent(node.parent);
            if (!CreateBranches(newNode, matchedNode, branchesStack)) {
                break matchedNodeLoop;
            }
            node = newNode;
        }
    }
    if (node == null) {
        /* End-of-file node if reached root. */
        ParserNode eof = new ParserNode(null);
        eof.SetPrev(matchedNode);
        nextBranches.add(eof);
    }
}

private void
FindRecursions(Grammar.Node node, ArrayDeque<Grammar.Node> parentNodes)
{
    if (parentNodes.contains(node)) {
        ValidateRecursion(node);
        return;
    }
    parentNodes.push(node);
    for (Grammar.Node child: node) {
        FindRecursions(child, parentNodes);
    }
    parentNodes.pop();
}

private void
ValidateRecursion(Grammar.Node node)
{
    ValidateRecursion(node, node, 0, 1, true);
}

private static class VldRecResult {
    int charsAccumulated;
    boolean dropAccumulated;

    VldRecResult(int charsAccumulated, boolean dropAccumulated)
    {
        this.charsAccumulated = charsAccumulated;
        this.dropAccumulated = dropAccumulated;
    }

    static VldRecResult
    TargetFound()
    {
        return new VldRecResult(0, true);
    }
}

/**
 *
 * @param node Currently traversed node.
 * @param targetNode Node recursion is being validated for.
 * @param charsBefore Total minimal number of characters to be matched before the target node.
 * @param maxMultiplier Accumulated multiplier of maximal matching quantity for all parent node.
 *                      -1 for infinity.
 * @return Accumulated characters information.
 */
private VldRecResult
ValidateRecursion(Grammar.Node node, Grammar.Node targetNode, int charsBefore, int maxMultiplier,
                  boolean firstCall)
{
    if (!firstCall && node == targetNode) {
        if (charsBefore == 0) {
            throw new IllegalStateException("Invalid grammar recursion detected " +
                                            "(instant recursive match)\n" + node.toString());
        }
        int _maxMultiplier = MaxMultiplier(maxMultiplier, node.GetMaxQuantity());
        if (_maxMultiplier == -1 || _maxMultiplier > 1) {
            throw new IllegalStateException("Exponentially growing recursion\n" + node.toString());
        }
        return VldRecResult.TargetFound();
    }

    int accumulatedChars = 0;
    boolean dropAccumulated = false;

    if (node instanceof Grammar.SequenceNode) {
        for (Grammar.Node child: node) {
            VldRecResult res = ValidateRecursion(child, targetNode, charsBefore + accumulatedChars,
                                                 MaxMultiplier(maxMultiplier, child.GetMaxQuantity()),
                                                 false);
            if (res.dropAccumulated) {
                dropAccumulated = true;
                accumulatedChars = 0;
                charsBefore = 0;
            }
            accumulatedChars += res.charsAccumulated;
        }

    } else if (node instanceof Grammar.VariantsNode) {
        int minChars = -1, minCharsWithDrop = -1;
        for (Grammar.Node child: node) {
            VldRecResult res = ValidateRecursion(child, targetNode, charsBefore,
                                                 MaxMultiplier(maxMultiplier, child.GetMaxQuantity()),
                                                 false);
            if (res.dropAccumulated) {
                if (minCharsWithDrop == -1 || res.charsAccumulated < minCharsWithDrop) {
                    minCharsWithDrop = res.charsAccumulated;
                }
            } else if (minChars == -1 || res.charsAccumulated < minChars) {
                minChars = res.charsAccumulated;
            }
        }
        int totalMinChars = charsBefore;
        if (minChars != -1) {
            totalMinChars += minChars;
        }
        if (minChars == -1 || (minCharsWithDrop != -1 && totalMinChars > minCharsWithDrop)) {
            accumulatedChars = minCharsWithDrop;
            dropAccumulated = true;
        } else {
            accumulatedChars = minChars;
        }

    } else if (node instanceof Grammar.CharNode) {
        accumulatedChars = 1;

    } else {
        throw new IllegalStateException("Unhandled node type " + node.getClass().getSimpleName());
    }

    return new VldRecResult(accumulatedChars * node.GetMinQuantity(), dropAccumulated);
}

private int
MaxMultiplier(int x, int y)
{
    if (x == -1 || y == -1) {
        return -1;
    }
    return x * y;
}

private void
CommitBranch(ParserNode branch)
{
    ArrayDeque<ParserNode> nodes = new ArrayDeque<>();
    ParserNode node = branch;
    while (node != null) {
        nodes.addFirst(node);
        node = node.prev;
    }

    for (ParserNode charNode: nodes) {
        node = charNode;
        Ast.Node astNode = null, firstAstNode = null;
        boolean astCreated = false;
        while (node != null) {
            if (node.inputPosition == null) {
                node.inputPosition = charNode.inputPosition;
            }
            if (node.astNode == null && node.grammarNode.isVal) {
                node.astNode = ast.CreateNode();
                node.astNode.grammarNode = node.grammarNode;
                node.astNode.startPosition = charNode.inputPosition;
                astCreated = true;
                if (firstAstNode == null) {
                    firstAstNode = node.astNode;
                }
            } else if (node.astNode != null) {
                astCreated = false;
            }
            if (node.astNode != null) {
                if (astNode == null && node.grammarNode.wantValString) {
                    node.astNode.AppendChar(charNode.matchedChar);
                } else if (astNode != null) {
                    node.astNode.AppendChild(astNode);
                }
                astNode = node.astNode;
            }
            if (astNode != null && !astCreated) {
                break;
            }
            node = node.parent;
        }
        if (firstAstNode != null) {
            CommitAstNodes(firstAstNode);
        }
    }

    for (ParserNode curBranch: curBranches) {
        if (curBranch.prev != null) {
            curBranch.prev.Release();
            curBranch.prev = null;
        }
    }
}

/** Commit all AST nodes which precede the specified new node.
 *
 * @param newNode Newly create node. Can be null to commit all uncommitted nodes (on finalization).
 */
private void
CommitAstNodes(Ast.Node newNode)
{
    Ast.Node node = lastAstNode;
    while (node != null && (newNode == null || !newNode.IsAncestor(node))) {
        node.Commit(prevPos, summary);
        node = node.parent;
    }
    lastAstNode = newNode;
}

}
