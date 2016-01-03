package org.roxy.parser;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/** Language grammar description. */
public class Grammar {

public class Node {

    /** Set node name if not yet done. */
    public Node
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

    public Node
    Optional()
    {
        return NoneOrOne();
    }

    public Node
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
    public Node
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

    public Node
    OneOrMany()
    {
        return Quantity(1, -1);
    }

    public Node
    NoneOrOne()
    {
        return Quantity(0, 1);
    }

    public Node
    NoneOrMany()
    {
        return Quantity(0, -1);
    }

    protected String name;
    protected int numMin, numMax;
    protected boolean quantityValid = false;
    /** Node is cloned into compiled tree. */
    protected boolean isCloned = false;

    protected Node
    CopyTo(Map<String, Node> localIndex, Node node)
    {
        assert !isCloned;
        node.name = name;
        node.quantityValid = quantityValid;
        node.numMin = numMin;
        node.numMax = numMax;
        node.isCloned = true;
        if (name != null) {
            assert !localIndex.containsKey(name);
            localIndex.put(name, node);
        }
        return node;
    }

    protected Node
    Clone(Map<String, Node> localIndex)
    {
        if (isCloned) {
            return this;
        }
        return CopyTo(localIndex, new Node());
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
        node.Name(name);
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

    private final String name;
}

/** Transformed into sequence with one node. */
public class NodeRef extends Node {

    private
    NodeRef(String refName)
    {
        this.refName = refName;
    }

    private Node
    Resolve(Map<String, Node> localIndex)
    {
        Node node;
        if ((node = localIndex.get(refName)) != null) {
            return node;
        }
        if ((node = nodesIndex.get(refName)) != null) {
            return node;
        }
        throw new IllegalStateException("Unresolved node reference: " + refName);
    }

    @Override protected Node
    Clone(Map<String, Node> localIndex)
    {
        if (isCloned) {
            return this;
        }
        return CopyTo(localIndex, new SequenceNode(Resolve(localIndex).Clone(localIndex)));
    }

    private final String refName;
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

    @Override protected Node
    Clone(Map<String, Node> localIndex)
    {
        /* Character node is immutable so this instance can be returned. */
        return this;
    }
}

public class SequenceNode extends Node {

    private
    SequenceNode(Node... nodes)
    {
        this.nodes = nodes;
    }

    private final Node[] nodes;

    @Override protected Node
    Clone(Map<String, Node> localIndex)
    {
        if (isCloned) {
            return this;
        }
        Node[] newNodes = new Node[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            newNodes[i] = nodes[i].Clone(localIndex);
        }
        return CopyTo(localIndex, new SequenceNode(newNodes));
    }
}

public class VariantsNode extends Node {

    private
    VariantsNode(Node... nodes)
    {
        this.nodes = nodes;
    }

    private final Node[] nodes;

    @Override protected Node
    Clone(Map<String, Node> localIndex)
    {
        if (isCloned) {
            return this;
        }
        Node[] newNodes = new Node[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            newNodes[i] = nodes[i].Clone(localIndex);
        }
        return CopyTo(localIndex, new VariantsNode(newNodes));
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

/** Compile grammar into nodes tree.
 *
 * @param rootNodeName Name of the grammar root node.
 * @return Root node of the compiled tree.
 */
public Node
Compile(String rootNodeName)
{
    TreeMap<String, Node> localIndex = new TreeMap<>();
    Node root = nodesIndex.get(rootNodeName);
    if (root == null) {
        throw new IllegalArgumentException("Specified root node not found: " + rootNodeName);
    }
    return root.Clone(localIndex);
}

private final TreeMap<String, Node> nodesIndex = new TreeMap<>();

}
