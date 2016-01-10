package org.roxy.parser;

import java.util.*;

/** Language grammar description. */
public class Grammar {

private final String NODE_STR_INDENT = "    ";

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

    /** Mark the node valuable to have in the parsed AST. */
    public final Node
    Val()
    {
        isVal = true;
        return this;
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

    // /////////////////////////////////////////////////////////////////////////////////////////////

    protected String name;
    protected boolean isVal;
    protected int numMin = 1, numMax = 1;
    protected boolean quantityValid = false;

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

    public NodeBuilder
    Def(Node node)
    {
        if (this.node != null) {
            throw new IllegalStateException("Node already defined");
        }
        this.node = node;
        node.Name(name);
        if (isVal) {
            node.Val();
        }
        return this;
    }

    public NodeBuilder
    Any(Node... nodes)
    {
        return Def(Grammar.this.Any(nodes));
    }

    public NodeBuilder
    Sequence(Node... nodes)
    {
        return Def(Grammar.this.Sequence(nodes));
    }

    public NodeBuilder
    Val()
    {
        isVal = true;
        if (node != null) {
            node.Val();
        }
        return this;
    }

    private final String name;
    private Node node;
    private boolean isVal = false;
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
        for (int i = 0; i < nodes.length; i++) {
            Node node = nodes[i];
            if (node instanceof NodeRef) {
                nodes[i] = node.Compile(visitedNodes);
            } else if (!visitedNodes.contains(node)) {
                node.Compile(visitedNodes);
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
}

/** Get node bye name. */
public Node
FindNode(String name)
{
    return nodesIndex.get(name);
}

private final TreeMap<String, Node> nodesIndex = new TreeMap<>();

}
