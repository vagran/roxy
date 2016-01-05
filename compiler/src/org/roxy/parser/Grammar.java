package org.roxy.parser;

import java.util.ArrayList;
import java.util.HashSet;
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
    Resolve(HashSet<Node> visitedNodes, TreeMap<String, Node> nodeRefs)
    {
        visitedNodes.add(this);
        return this;
    }

    protected Node
    Compile()
    {
        return this;
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
    Resolve()
    {
        Node node;
        if ((node = nodesIndex.get(refName)) != null) {
            return node;
        }
        throw new IllegalStateException("Unresolved node reference: " + refName);
    }

    protected Node
    Resolve(HashSet<Node> visitedNodes, TreeMap<Node, Node> nodeRefs)
    {
        if (nodeRefs.containsKey(refName)) {
            return nodeRefs.get(refName);
        }
        Node target = Resolve();
        if (target instanceof NodeRef) {
            target = new SequenceNode(new Node[]{null});
            nodeRefs.put(refName, target);
        } else if (!visitedNodes.contains(target)) {
            nodeRefs.put(refName, target);
            target.Resolve(visitedNodes, nodeRefs);
        }
        visitedNodes.add(this);
        return target;
    }

    @Override protected Node
    Compile()
    {
        return CopyTo(new SequenceNode(Resolve().Compile()));
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
}

public class GroupNode extends Node {

    private
    GroupNode(Node... nodes)
    {
        this.nodes = nodes;
    }

    @Override protected Node
    Resolve(HashSet<Node> visitedNodes, TreeMap<String, Node> nodeRefs)
    {
        visitedNodes.add(this);
        for (Node node: nodes) {
            if (!visitedNodes.contains(node)) {
                node.Resolve(visitedNodes, nodeRefs);
            }
        }
        return this;
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
    /* Resolved node references. Reference name mapped to node. Reference to NodeRef is transformed
     * to either SequenceNode with one element (if this reference is named) or to resolved
     * referenced node.
     */
    TreeMap<Node, Node> nodeRefs = new TreeMap<>();

    for (Map.Entry<String, Node> kv: nodesIndex.entrySet()) {
        kv.getValue().Resolve(visitedNodes, nodeRefs);
    }

    for (Map.Entry<String, Node> kv: nodesIndex.entrySet()) {
        String name = kv.getKey();
        Node node = kv.getValue();
        if (node instanceof NodeRef) {
            kv.setValue(nodeRefs.get(name));
        }
    }
}

private final TreeMap<String, Node> nodesIndex = new TreeMap<>();

}
