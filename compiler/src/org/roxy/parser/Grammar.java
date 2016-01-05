package org.roxy.parser;

import java.util.*;

/** Language grammar description. */
public class Grammar {

private final String NODE_STR_INDENT = "    ";

public abstract class Node {

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

    @Override public String
    toString()
    {
        return toString("", new HashSet<>());
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

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

    protected void
    Compile(int pass, HashSet<Node> visitedNodes, HashMap<NodeRef, Node> nodeRefs)
    {
        visitedNodes.add(this);
    }

    protected abstract String
    toString(String indent, HashSet<Node> visitedNodes);

    protected String
    GetQuantityString()
    {
        if (!quantityValid) {
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

    protected void
    Compile(int pass, HashSet<Node> visitedNodes, HashMap<NodeRef, Node> nodeRefs)
    {
        visitedNodes.add(this);
        if (pass == 0) {
            Node replacement;
            if (name != null) {
                replacement = new SequenceNode(new Node[]{null});
            } else {
                replacement = null;
                Node target = Resolve();
                if (!visitedNodes.contains(target)) {
                    target.Compile(0, visitedNodes, nodeRefs);
                }
            }
            nodeRefs.put(this, replacement);
        } else {
            throw new InternalError("Should not reach NodeRef in the second pass");
        }
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

    private
    GroupNode(Node... nodes)
    {
        this.nodes = nodes;
    }

    @Override protected void
    Compile(int pass, HashSet<Node> visitedNodes, HashMap<NodeRef, Node> nodeRefs)
    {
        visitedNodes.add(this);
        for (int i = 0; i < nodes.length; i++) {
            Node node = nodes[i];
            if (!visitedNodes.contains(node)) {
                if (pass == 1 && node instanceof NodeRef) {
                    node = nodeRefs.get(node);
                    nodes[i] = node;
                }
                node.Compile(pass, visitedNodes, nodeRefs);
            }
        }
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
    /* Resolved NodeRef nodes. NodeRef is transformed to either SequenceNode with one element (if
     * this NodeRef is named) or to resolved referenced node.
     */
    HashMap<NodeRef, Node> nodeRefs = new HashMap<>();

    for (Map.Entry<String, Node> kv: nodesIndex.entrySet()) {
        kv.getValue().Compile(0, visitedNodes, nodeRefs);
    }

    /* nodeRefs now contains all NodeRef instances with either null for not yet resolved targets or
     * SequenceNode for created replacements. Resolve unresolved targets now.
     */
    for (Map.Entry<NodeRef, Node> kv: nodeRefs.entrySet()) {
        Node node = kv.getValue();
        Node target = nodesIndex.get(kv.getKey().refName);
        if (target instanceof NodeRef) {
            /* Named node always should have replacement available from the previous stage. */
            target = nodeRefs.get(target);
            assert target != null;
        }
        if (node == null) {
            kv.setValue(target);
        } else {
            /* Fill sequence first (and only) member. */
            ((SequenceNode)node).nodes[0] = target;
        }
    }

    /* Now replace all NodeRef with their targets. */
    visitedNodes.clear();
    for (Map.Entry<String, Node> kv: nodesIndex.entrySet()) {
        Node node = kv.getValue();
        if (node instanceof NodeRef) {
            node = nodeRefs.get(node);
            kv.setValue(node);
        }
        node.Compile(1, visitedNodes, nodeRefs);
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
