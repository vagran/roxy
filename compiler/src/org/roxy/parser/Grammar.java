package org.roxy.parser;

import java.util.*;

/** Language grammar description. */
public class Grammar {

private final String NODE_STR_INDENT = "    ";

/** Node matching count status. */
enum QuantityStatus {
    NOT_ENOUGH,
    ENOUGH,
    MAX_REACHED
}

/** Grammar description node.
 * Iterable interface should provide iteration of child nodes if any.
 */
public abstract class Node implements Iterable<Node> {

    /** Set node name if not yet done. */
    public final Node
    Name(String name)
    {
        if (this.name != null) {
            throw new IllegalStateException("Node name already defined: " + this.name);
        }
        this.name = name;
        if (nodesIndex.containsKey(name)) {
            throw new IllegalArgumentException("Duplicated node name: " + name);
        }
        nodesIndex.put(name, this);
        return this;
    }

    public final Node
    Optional()
    {
        return NoneToOne();
    }

    public final Node
    Quantity(int num)
    {
        return Quantity(num, num);
    }

    /** Limit number of times to match the node. The matching is lazy in scope of parent node.
     * Default quantification is (1;1).
     *
     * @param numMin Minimal number of matches.
     * @param numMax Maximal number of matches. -1 for unlimited.
     */
    public final Node
    Quantity(int numMin, int numMax)
    {
        if (quantityValid) {
            throw new IllegalStateException("Node quantity already defined");
        }
        quantityValid = true;
        this.numMin = numMin;
        this.numMax = numMax;
        return this;
    }

    public final Node
    OneToMany()
    {
        return Quantity(1, -1);
    }

    public final Node
    NoneToOne()
    {
        return Quantity(0, 1);
    }

    public final Node
    NoneToMany()
    {
        return Quantity(0, -1);
    }

    /** Specify node precedence. It is accounted in recursively defined grammar.
     *
     * @param precedenceGroup Object which identifies the group this precedence is defined in.
     *                        Equality checked by reference equality.
     * @param precedence Precedence value. Higher values mean higher precedence.
     * @return This node.
     */
    public final Node
    Precedence(Object precedenceGroup, Comparable<?> precedence)
    {
        this.precedenceGroup = precedenceGroup;
        this.precedence = precedence;
        return this;
    }

    /** Mark the node valuable to have it in the parsed AST.
     *
     * @param valTagFabric Fabric for AST node tag creation.
     * @param wantValString True to save matched string in the AST node.
     * @return This node.
     */
    public final Node
    Val(Ast.TagFabric valTagFabric, boolean wantValString)
    {
        isVal = true;
        this.valTagFabric = valTagFabric;
        this.wantValString = wantValString;
        return this;
    }

    public final Node
    Val(Ast.TagFabric valTagFabric)
    {
        return Val(valTagFabric, false);
    }

    @Override public String
    toString()
    {
        return toString("", new HashSet<>());
    }

    /** Iterate over child nodes. Default implementation returns empty iterator. */
    @Override public Iterator<Node>
    iterator()
    {
        return new Iterator<Node>() {
            @Override public boolean
            hasNext()
            {
                return false;
            }

            @Override public Node
            next()
            {
                throw new NoSuchElementException();
            }
        };
    }

    public final int
    GetMinQuantity()
    {
        return numMin;
    }

    public final int
    GetMaxQuantity()
    {
        return numMax;
    }

    public final QuantityStatus
    CheckQuantity(int count)
    {
        if (count < numMin) {
            return QuantityStatus.NOT_ENOUGH;
        }
        if (numMax == -1 || count < numMax) {
            return QuantityStatus.ENOUGH;
        }
        return QuantityStatus.MAX_REACHED;
    }

    /** Get next sibling node when in sequence. Null for last node. */
    public final Node
    GetNextSibling()
    {
        return next;
    }

    /** Get associated grammar. */
    public Grammar
    GetGrammar()
    {
        return Grammar.this;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    protected String name;
    boolean isVal, wantValString;
    Ast.TagFabric valTagFabric;
    protected int numMin = 1, numMax = 1;
    protected boolean quantityValid = false;
    /** Next sibling node when in a sequence. */
    protected Node next;
    protected Object precedenceGroup;
    protected Comparable<?> precedence;

    protected Node
    CopyTo(Node node)
    {
        node.name = name;
        node.quantityValid = quantityValid;
        node.numMin = numMin;
        node.numMax = numMax;
        return node;
    }

    protected Node
    Compile(HashSet<Node> visitedNodes)
    {
        visitedNodes.add(this);
        return this;
    }

    protected abstract String
    toString(String indent, HashSet<Node> visitedNodes);

    protected String
    GetQuantityString()
    {
        if (!quantityValid || (numMin == 1 && numMax == 1)) {
            return "";
        }
        if (numMin == 0 && numMax == 1) {
            return "?";
        }
        if (numMin == 0 && numMax == -1) {
            return "*";
        }
        if (numMin == 1 && numMax == -1) {
            return "+";
        }
        if (numMin == numMax) {
            return String.format("{%d}", numMin);
        }
        if (numMax == -1) {
            return String.format("{%d,}", numMin);
        }
        return String.format("{%d,%d}", numMin, numMax);
    }

    /** Utility method for creating iterator over nodes array. */
    protected Iterator<Node>
    GetArrayIterator(Node[] nodes)
    {
        return new Iterator<Node>() {
            private int pos = 0;

            @Override public boolean
            hasNext()
            {
                return pos < nodes.length;
            }

            @Override public Node
            next()
            {
                if (pos < nodes.length) {
                    return nodes[pos++];
                } else {
                    throw new NoSuchElementException();
                }
            }
        };
    }
}

public class NodeBuilder {

    private
    NodeBuilder(String name)
    {
        this.name = name;
    }

    public Node
    Def(Node node)
    {
        if (this.node != null) {
            throw new IllegalStateException("Node already defined");
        }
        this.node = node;
        node.Name(name);
        return node;
    }

    public Node
    Any(Node... nodes)
    {
        return Def(Grammar.this.Any(nodes));
    }

    public Node
    Sequence(Node... nodes)
    {
        return Def(Grammar.this.Sequence(nodes));
    }

    private final String name;
    private Node node;
}

/** Transformed into sequence with one node. */
public class NodeRef extends Node {

    @Override public Iterator<Node>
    iterator()
    {
        return GetArrayIterator(new Node[]{Resolve()});
    }

    private
    NodeRef(String refName)
    {
        this.refName = refName;
    }

    private Node
    Resolve()
    {
        Node node;
        if ((node = nodesIndex.get(refName)) != null) {
            return node;
        }
        throw new IllegalStateException("Unresolved node reference: " + refName);
    }

    @Override protected Node
    Compile(HashSet<Node> visitedNodes)
    {
        if (compiledNode != null) {
            return compiledNode;
        }
        visitedNodes.add(this);
        compiledNode = new SequenceNode(new Node[]{null});
        CopyTo(compiledNode);
        compiledNode.nodes[0] = Resolve().Compile(visitedNodes);
        compiledNode.Compile(visitedNodes);
        return compiledNode;
    }

    @Override protected String
    toString(String indent, HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(this)) {
            return String.format("%s`%s`", indent, name);
        }
        visitedNodes.add(this);
        return String.format("%s`%s`: NodeRef%s\n%s\n", indent, name == null ? "" : name,
                             GetQuantityString(),
                             Resolve().toString(indent + NODE_STR_INDENT, visitedNodes));
    }

    private final String refName;
    private SequenceNode compiledNode;
}

/** Excluded and included characters and ranges are applied sequentially (so order matters). */
public class CharNode extends Node {

    /**
     *
     * @param matchAny Initialize with wildcard if true, empty set if false.
     */
    private
    CharNode(boolean matchAny)
    {
        this.matchAny = matchAny;
    }

    public CharNode
    Include(int c)
    {
        return Range(c, c, false);
    }

    public CharNode
    Include(String chars)
    {
        chars.codePoints().forEach(c -> Range(c, c, false));
        return this;
    }

    /** Add matched range. */
    public CharNode
    IncludeRange(int cMin, int cMax)
    {
        return Range(cMin, cMax, false);
    }

    public CharNode
    Exclude(int c)
    {
        return Range(c, c, true);
    }

    public CharNode
    Exclude(String chars)
    {
        chars.codePoints().forEach(c -> Range(c, c, true));
        return this;
    }

    public CharNode
    ExcludeRange(int cMin, int cMax)
    {
        return Range(cMin, cMax, true);
    }

    /** Match the character against this node.
     * @return True if the character matched, false otherwise.
     */
    public boolean
    MatchChar(int c)
    {
        boolean match = matchAny;
        for (RangeEntry re: ranges) {
            if (re.MatchChar(c)) {
                match = !re.exclude;
            }
        }
        return match;
    }

    /**
     * @return Character code if this is single character node, zero if it is not.
     */
    public int
    IsSingleChar()
    {
        if (matchAny) {
            return 0;
        }
        if (ranges.size() != 1) {
            return 0;
        }
        RangeEntry re = ranges.get(0);
        if (re.cMin != re.cMax) {
            return 0;
        }
        return re.cMin;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    private class RangeEntry {
        public int cMin, cMax;
        public boolean exclude;

        public
        RangeEntry(int cMin, int cMax, boolean exclude)
        {
            this.cMin = cMin;
            this.cMax = cMax;
            this.exclude = exclude;
        }

        public boolean
        MatchChar(int c)
        {
            return c >= cMin && c <= cMax;
        }
    }

    private final boolean matchAny;
    private final ArrayList<RangeEntry> ranges = new ArrayList<>();

    private CharNode
    Range(int cMin, int cMax, boolean exclude)
    {
        ranges.add(new RangeEntry(cMin, cMax, exclude));
        return this;
    }

    @Override protected String
    toString(String indent, HashSet<Node> visitedNodes)
    {
        visitedNodes.add(this);
        StringBuilder sb = new StringBuilder();
        if (matchAny) {
            sb.append('.');
        }
        for (RangeEntry re: ranges) {
            if (re.cMin == re.cMax) {
                sb.append(String.format("[%s%c]", re.exclude ? "^" : "", re.cMin));
            } else {
                sb.append(String.format("[%s%c-%c]", re.exclude ? "^" : "", re.cMin, re.cMax));
            }
        }
        return String.format("%s`%s`: %s%s", indent, name == null ? "" : name, sb.toString(),
                             GetQuantityString());
    }

    @Override protected Node
    Compile(HashSet<Node> visitedNodes)
    {
        visitedNodes.add(this);
        int c = IsSingleChar();
        if (c == 0) {
            return this;
        }
        if (singleCharNodes.containsKey(c)) {
            return singleCharNodes.get(c);
        }
        singleCharNodes.put(c, this);
        return this;
    }
}

public class GroupNode extends Node {

    @Override public Iterator<Node>
    iterator()
    {
        return GetArrayIterator(nodes);
    }

    private
    GroupNode(Node... nodes)
    {
        if (nodes.length == 0) {
            throw new IllegalArgumentException("Group node should have non-empty content");
        }
        this.nodes = nodes;
    }

    @Override protected Node
    Compile(HashSet<Node> visitedNodes)
    {
        if (!visitedNodes.add(this)) {
            return this;
        }
        Node prev = null;
        for (int i = 0; i < nodes.length; i++) {
            Node node = nodes[i];
            if (node instanceof NodeRef) {
                node = node.Compile(visitedNodes);
                nodes[i] = node;
            } else if (!visitedNodes.contains(node)) {
                node.Compile(visitedNodes);
            }
            if (this instanceof SequenceNode) {
                if (prev != null) {
                    prev.next = node;
                }
                prev = node;
            }
        }
        return this;
    }

    @Override protected String
    toString(String indent, HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(this)) {
            return String.format("%s`%s`", indent, name);
        }
        visitedNodes.add(this);
        StringBuilder sb = new StringBuilder();
        for (Node node: nodes) {
            sb.append(node.toString(indent + NODE_STR_INDENT, visitedNodes));
            sb.append('\n');
        }
        String type;
        if (this instanceof SequenceNode) {
            type = "Sequence";
        } else if (this instanceof VariantsNode) {
            type = "Variants";
        } else {
            throw new InternalError("Unhandled node type: " + getClass().getName());
        }
        return String.format("%s`%s`: %s%s\n%s", indent, name == null ? "" : name, type,
                             GetQuantityString(), sb.toString());
    }

    protected final Node[] nodes;
}

public class SequenceNode extends GroupNode {

    private
    SequenceNode(Node... nodes)
    {
        super(nodes);
    }
}

public class VariantsNode extends GroupNode {

    private
    VariantsNode(Node... nodes)
    {
        super(nodes);
    }
}

public NodeBuilder
Node(String name)
{
    return new NodeBuilder(name);
}

/** Refer existing grammar node by name. */
public NodeRef
NodeRef(String name)
{
    return new NodeRef(name);
}

public CharNode
Char(int c)
{
    return new CharNode(false).Include(c);
}

public CharNode
CharRange(int cMin, int cMax)
{
    return new CharNode(false).IncludeRange(cMin, cMax);
}

/** Matches any character. Exclusions cah be added in the returned node. */
public CharNode
AnyChar()
{
    return new CharNode(true);
}

/** Matches any character from the provided characters set. */
public CharNode
AnyChar(String chars)
{
    return new CharNode(false).Include(chars);
}

/** Characters sequence. */
public SequenceNode
String(String string)
{
    return String(string, true);
}

/** Characters sequence.
 * @param caseSensitive Case sensitive match if true, case insensitive match otherwise.
 */
public SequenceNode
String(String string, boolean caseSensitive)
{
    ArrayList<Node> nodes = new ArrayList<>();
    string.codePoints().forEach(c -> {
        if (caseSensitive) {
            nodes.add(Char(c));
        } else {
            nodes.add(Char(Character.toLowerCase(c)).Include(Character.toUpperCase(c)));
        }
    });
    return Sequence(nodes.toArray(new Node[nodes.size()]));
}

public SequenceNode
Sequence(Node... nodes)
{
    return new SequenceNode(nodes);
}

public VariantsNode
Any(Node... nodes)
{
    return new VariantsNode(nodes);
}

/** Compile grammar into nodes tree. This resolves all node references. */
public void
Compile()
{
    HashSet<Node> visitedNodes = new HashSet<>();
    for (Map.Entry<String, Node> kv: nodesIndex.entrySet()) {
        Node node = kv.getValue();
        Node compiledNode = node.Compile(visitedNodes);
        if (compiledNode != node) {
            kv.setValue(compiledNode);
        }
    }
    singleCharNodes = null;
}

/** Get node by name. */
public Node
FindNode(String name)
{
    return nodesIndex.get(name);
}

/** Nodes indexed by name. */
private final TreeMap<String, Node> nodesIndex = new TreeMap<>();
/** Single character nodes. */
private TreeMap<Integer, Node> singleCharNodes = new TreeMap<>();

}
