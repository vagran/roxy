package org.roxy.parser;

import org.junit.Test;

import java.io.IOException;
import java.util.TreeMap;

public class BasicTest {

static class NodeTag {
    public enum Type {
        STRING_CHAR,
        STRING_ESCAPE,
        STRING_LITERAL,
        NUM_LITERAL,
        IDENTIFIER,
        STATEMENT,
        FILE
    }

    public interface ErrorCode {
        int INVALID_ESCAPE = Parser.ErrorCode.CUSTOM_START;
    }

    final Type type;
    char escapedChar;

    @Override public String
    toString()
    {
        return type.toString();
    }

    public static Ast.TagFabric
    GetFabric(Type type)
    {
        return (Ast.Node node, Parser.Summary summary) -> {
            NodeTag tag = new NodeTag(type);
            tag.Compile(node, summary);
            return tag;
        };
    }

    private
    NodeTag(Type type)
    {
        this.type = type;
    }

    private void
    Compile(Ast.Node node, Parser.Summary summary)
    {
        switch (type) {
        case STRING_ESCAPE:
            CompileEscape(node, summary);
            break;
        case STRING_LITERAL:
            CompileString(node, summary);
            break;
        }
    }

    private void
    CompileEscape(Ast.Node node, Parser.Summary summary)
    {
        switch (node.str.charAt(0)) {
        case '\\':
            escapedChar = '\\';
            break;
        case '"':
            escapedChar = '"';
            break;
        case 'n':
            escapedChar = '\n';
            break;
        case 'r':
            escapedChar = '\r';
            break;
        case 't':
            escapedChar = '\t';
            break;
        default:
            summary.Error(node.startPosition, ErrorCode.INVALID_ESCAPE, "Invalid escape character");
        }
    }

    private void
    CompileString(Ast.Node node, Parser.Summary summary)
    {
        node.str = new StringBuilder();
        for (Ast.Node child: node.children) {
            NodeTag tag = (NodeTag)child.tag;
            if (tag.type == Type.STRING_CHAR) {
                node.str.append(child.str.charAt(0));
            } else if (tag.type == Type.STRING_ESCAPE) {
                node.str.append(tag.escapedChar);
            } else {
                throw new IllegalStateException("Invalid child node type: " + tag.type);
            }
        }
        node.children = null;
    }
}

Grammar grammar = new Grammar() {{

    Node("decimal-digit").Def(CharRange('0', '9'));
    Node("alphabetic").Def(CharRange('a', 'z').IncludeRange('A', 'Z'));
    Node("whitespace").Def(AnyChar(" \t\r\n"));

    Node("multiline-comment").Sequence(
        String("/*"),
        Any(
            AnyChar().Exclude('*'),
            Sequence(Char('*'), AnyChar().Exclude('/'))).NoneToMany(),
        String("*/"));

    Node("gap").Any(
        NodeRef("whitespace"),
        NodeRef("multiline-comment")).OneToMany();

    Node("string-literal").Sequence(
        Char('"'),
        Any(AnyChar().Exclude("\"\\").Val(NodeTag.GetFabric(NodeTag.Type.STRING_CHAR), true),
            Sequence(Char('\\'),
                     AnyChar().Val(NodeTag.GetFabric(NodeTag.Type.STRING_ESCAPE), true))).NoneToMany(),
        Char('"')).
        Val(NodeTag.GetFabric(NodeTag.Type.STRING_LITERAL));

    Node("number-literal").Def(NodeRef("decimal-digit").OneToMany()).
        Val(NodeTag.GetFabric(NodeTag.Type.NUM_LITERAL), true);

    Node("identifier-first-char").Any(NodeRef("alphabetic"), Char('_'));
    Node("identifier-char").Any(NodeRef("identifier-first-char"), NodeRef("decimal-digit"));
    Node("identifier").Sequence(
        NodeRef("identifier-first-char"),
        NodeRef("identifier-char").NoneToMany()).
        Val(NodeTag.GetFabric(NodeTag.Type.IDENTIFIER), true);

    Node("statement").Sequence(
        NodeRef("identifier"),
        NodeRef("gap").NoneToOne(),
        Char('='),
        NodeRef("gap").NoneToOne(),
        Any(
            NodeRef("string-literal"),
            NodeRef("number-literal")),
        NodeRef("gap").NoneToOne(),
        Char(';')).Val(NodeTag.GetFabric(NodeTag.Type.STATEMENT));

    Node("file").Sequence(
        NodeRef("gap").NoneToOne(),
        Sequence(
            NodeRef("statement"),
            NodeRef("gap").NoneToOne()
        ).NoneToMany()).Val(NodeTag.GetFabric(NodeTag.Type.FILE));

    System.out.print(FindNode("file"));
    Compile();
    System.out.print(FindNode("file"));
}};

Grammar.Node fileNode = grammar.FindNode("file");

String testFile1 =
    "someIdent = \"some value \\\\aa\\\"bb\"\n" +
    "\n" +
    "   \t;a=1;b=2;/* Some comment a*b/*/\n" +
    "/* multiline\r\n" +
    "comment*/\r" +
    "  c =   3;";

TreeMap<String, Object> expectedData = new TreeMap<String, Object>() {{
    put("someIdent", "some value \\aa\"bb");
    put("a", 1);
    put("b", 2);
    put("c", 3);
}};

@Test public void
Basic()
    throws IOException
{
    ParserUtil.TestParser(fileNode, testFile1);
}

@Test public void
Spaces()
    throws IOException
{
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
        sb.append(' ');
    }
    ParserUtil.TestParser(fileNode, sb.toString());
}

@Test public void
InvalidGrammar()
{
    Grammar invGrammar = new Grammar() {{
        // Recursive definition with minimal size of zero characters.
        Node("gap").Sequence(
            Any(Char('q').NoneToMany(),
                Char('w').NoneToMany()),
            NodeRef("gap").NoneToMany()).Val(null);

        Compile();
        System.out.print(FindNode("gap"));
    }};
    ParserUtil.TestParser(invGrammar.FindNode("gap"), "some string", IllegalStateException.class);
}

@Test public void
UnclosedStringLiteral()
{
    ParserUtil.TestParser(fileNode, "a = \"some value",
                          new ParserUtil.Error(Parser.ErrorCode.INCOMPLETE_NODE, 1, 4));
}

@Test public void
Finalization()
    throws IOException
{
    Grammar grammar = new Grammar() {{
        Any(
            Char('a').Quantity(3),
            Char('a').Quantity(5)
        ).Name("file").Val(null);
        Compile();
        System.out.print(FindNode("file"));
    }};

    ParserUtil.TestParser(grammar.FindNode("file"), "aa",
                          new ParserUtil.Error(Parser.ErrorCode.INCOMPLETE_NODE));

    ParserUtil.TestParser(grammar.FindNode("file"), "aaa");

    ParserUtil.TestParser(grammar.FindNode("file"), "aaaa",
                          new ParserUtil.Error(Parser.ErrorCode.INCOMPLETE_NODE, 1, 0));

    ParserUtil.TestParser(grammar.FindNode("file"), "aaaaa");

    ParserUtil.TestParser(grammar.FindNode("file"), "aaaaaa",
                          null, RuntimeException.class);
}

@Test public void
Finalization2()
    throws IOException
{
    Grammar grammar = new Grammar() {{
        Any(
            Char('a').Quantity(3, 5),
            Char('a').Quantity(7)
        ).Name("file").Val(null);
        Compile();
        System.out.print(FindNode("file"));
    }};

    ParserUtil.TestParser(grammar.FindNode("file"), "aa",
                          new ParserUtil.Error(Parser.ErrorCode.INCOMPLETE_NODE));

    ParserUtil.TestParser(grammar.FindNode("file"), "aaa");
    ParserUtil.TestParser(grammar.FindNode("file"), "aaaa");
    ParserUtil.TestParser(grammar.FindNode("file"), "aaaaa");

    ParserUtil.TestParser(grammar.FindNode("file"), "aaaaaa",
                          new ParserUtil.Error(Parser.ErrorCode.INCOMPLETE_NODE, 1, 0));

    ParserUtil.TestParser(grammar.FindNode("file"), "aaaaaaa");

    //XXX change error reporting
    ParserUtil.TestParser(grammar.FindNode("file"), "aaaaaaaa", null, RuntimeException.class);
}

}
