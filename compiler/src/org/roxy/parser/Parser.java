package org.roxy.parser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;

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
Parse() throws IOException {
    InitializeState();
    while (true) {
        int c = reader.read();
        if (c == -1) {
            Finalize();
            return;
        }
        ProcessChar(c);
    }
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
    /** Corresponding grammar node. */
    public Grammar.Node grammarNode;
    /** Assigned AST node if committed and valuable. */
    public Ast.Node astNode;
    /** Number the corresponding grammar node has been repeated before this node (number of spent
     * quantification points so far).
     */
    public int numRepeated;

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
}

/** Current position in input text. */
private class InputPosition {
    public int curOffset = 0, curLine = 1, curCol = 0;

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
        return String.format("line %d column %d (offset %d)", curLine, curCol, curOffset);
    }

    /** CR character was the previous one. */
    private boolean wasCr = false;
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
private InputPosition curPos = new InputPosition();

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
    CreateBranches(AllocateNode(grammar), null, branchesStack);
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
            "(empty element recursion)");
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

    if (node.grammarNode.CheckQuantity(node.numRepeated) != Grammar.QuantityRange.NOT_ENOUGH) {
        addNext = true;
    }

    grammarStack.pop();
    return addNext;
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
    int numBranchesMatched = 0;
    for (ParserNode node: curBranches) {
        if (!((Grammar.CharNode)node.grammarNode).MatchChar(c)) {
            node.Release();
            continue;
        }
        numBranchesMatched++;
        //XXX handle lazy matching, remove branches in the same sequence

        /* Find candidates for next character matching. */
        FindNextCharNodes(node);
        node.Release();
    }

    if (numBranchesMatched == 0) {
        //XXX try to parse the rest, find some candidates in grammar for continuing
        throw new RuntimeException(String.format("Invalid syntax at %s, '%c'", curPos, c));//XXX temporal
    }

    SwapBranches();

    System.out.format("'%c' at %s, %d branches\n", c, curPos, curBranches.size());//XXX

    if (curBranches.size() == 1) {
        //XXX commit current branch
    }

    curPos.FeedChar(c);
}

/** Make branches in "nextBranches" member be current branches ("curBranches" member). */
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
        if (node.grammarNode.CheckQuantity(numMatches) != Grammar.QuantityRange.MAX_REACHED) {
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
}

}
