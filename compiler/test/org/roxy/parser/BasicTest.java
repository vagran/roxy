package org.roxy.parser;

import org.junit.Test;

import java.io.IOException;
import java.util.TreeMap;

public class BasicTest {

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
        Any(AnyChar().Exclude("\"\\"),
            Sequence(Char('\\'), AnyChar())).NoneToMany(),
        Char('"')).Val();

    Node("number-literal").Def(NodeRef("decimal-digit").OneToMany()).Val();

    Node("identifier-first-char").Any(NodeRef("alphabetic"), Char('_'));
    Node("identifier-char").Any(NodeRef("identifier-first-char"), NodeRef("decimal-digit"));
    Node("identifier").Sequence(
        NodeRef("identifier-first-char"),
        NodeRef("identifier-char").NoneToMany()).Val();

    Node("statement").Sequence(
        NodeRef("identifier"),
        NodeRef("gap").NoneToOne(),
        Char('='),
        NodeRef("gap").NoneToOne(),
        Any(
            NodeRef("string-literal"),
            NodeRef("number-literal")),
        NodeRef("gap").NoneToOne(),
        Char(';')).Val();

    Node("file").Sequence(
        NodeRef("gap").NoneToOne(),
        Sequence(
            NodeRef("statement"),
            NodeRef("gap").NoneToOne()
        ).NoneToMany()).Val();

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
            NodeRef("gap").NoneToMany()).Val();

        Compile();
        System.out.print(FindNode("gap"));
    }};
    ParserUtil.TestParser(invGrammar.FindNode("gap"), "some string", IllegalStateException.class);
}

@Test public void
UnclosedStringLiteral()
{
    ParserUtil.TestParser(fileNode, "a = \"some value",
                          new ParserUtil.Error(Parser.ErrorCode.INCOMPLETE_NODE.ordinal(), 1, 4));
}

@Test public void
Finalization()
    throws IOException
{
    Grammar grammar = new Grammar() {{
        Any(
            Char('a').Quantity(3),
            Char('a').Quantity(5)
        ).Name("file").Val();
        Compile();
        System.out.print(FindNode("file"));
    }};

    ParserUtil.TestParser(grammar.FindNode("file"), "aa",
                          new ParserUtil.Error(Parser.ErrorCode.INCOMPLETE_NODE.ordinal()));

    ParserUtil.TestParser(grammar.FindNode("file"), "aaa");

    ParserUtil.TestParser(grammar.FindNode("file"), "aaaa",
                          new ParserUtil.Error(Parser.ErrorCode.INCOMPLETE_NODE.ordinal(), 1, 0));

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
        ).Name("file").Val();
        Compile();
        System.out.print(FindNode("file"));
    }};

    ParserUtil.TestParser(grammar.FindNode("file"), "aa",
                          new ParserUtil.Error(Parser.ErrorCode.INCOMPLETE_NODE.ordinal()));

    ParserUtil.TestParser(grammar.FindNode("file"), "aaa");
    ParserUtil.TestParser(grammar.FindNode("file"), "aaaa");
    ParserUtil.TestParser(grammar.FindNode("file"), "aaaaa");

    ParserUtil.TestParser(grammar.FindNode("file"), "aaaaaa",
                          new ParserUtil.Error(Parser.ErrorCode.INCOMPLETE_NODE.ordinal(), 1, 0));

    ParserUtil.TestParser(grammar.FindNode("file"), "aaaaaaa");

    //XXX change error reporting
    ParserUtil.TestParser(grammar.FindNode("file"), "aaaaaaaa", null, RuntimeException.class);
}

}
