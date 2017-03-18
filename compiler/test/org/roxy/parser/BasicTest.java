package org.roxy.parser;

import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

public class BasicTest {

Map<String, Object>
Compile(Ast ast, Summary summary)
{
    TreeMap<String, Object> result = new TreeMap<>();
    for (Ast.Node stmtNode: ast.root.children) {
        assert stmtNode.children.size() == 2;
        Ast.Node identNode = stmtNode.children.get(0);
        Ast.Node valueNode = stmtNode.children.get(1);
        assert ((TestNodeTag)identNode.tag).type == TestNodeTag.Type.IDENTIFIER;
        if (result.containsKey(identNode.str)) {
            summary.Error(identNode.startPosition, TestNodeTag.ErrorCode.DUP_IDENTIFIER,
                          "Duplicated identifier: %s", identNode.str);
            continue;
        }
        Object value;
        TestNodeTag valueTag = (TestNodeTag)valueNode.tag;
        if (valueTag.type == TestNodeTag.Type.STRING_LITERAL) {
            value = valueNode.str;
        } else if (valueTag.type == TestNodeTag.Type.NUM_LITERAL) {
            value = valueTag.intValue;
        } else {
            throw new IllegalStateException("Invalid node in statement: " + valueTag.type);
        }
        result.put(identNode.str, value);
    }
    return result;
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
        Any(AnyChar().Exclude("\"\\").Val(TestNodeTag.GetFabric(TestNodeTag.Type.STRING_CHAR), true),
            Sequence(Char('\\'),
                     AnyChar().Val(TestNodeTag.GetFabric(TestNodeTag.Type.STRING_ESCAPE), true)))
                     .NoneToMany(),
        Char('"')).Val(TestNodeTag.GetFabric(TestNodeTag.Type.STRING_LITERAL));

    Node("number-literal").Sequence(Char('-').NoneToOne(), NodeRef("decimal-digit").OneToMany()).
        Val(TestNodeTag.GetFabric(TestNodeTag.Type.NUM_LITERAL), true);

    Node("identifier-first-char").Any(NodeRef("alphabetic"), Char('_'));
    Node("identifier-char").Any(NodeRef("identifier-first-char"), NodeRef("decimal-digit"));
    Node("identifier").Sequence(
        NodeRef("identifier-first-char"),
        NodeRef("identifier-char").NoneToMany())
        .Val(TestNodeTag.GetFabric(TestNodeTag.Type.IDENTIFIER), true);

    Node("statement").Sequence(
        NodeRef("identifier"),
        NodeRef("gap").NoneToOne(),
        Char('='),
        NodeRef("gap").NoneToOne(),
        Any(
            NodeRef("string-literal"),
            NodeRef("number-literal")),
        NodeRef("gap").NoneToOne(),
        Char(';')).Val(TestNodeTag.GetFabric(TestNodeTag.Type.STATEMENT));

    Node("file").Sequence(
        NodeRef("gap").NoneToOne(),
        Sequence(
            NodeRef("statement"),
            NodeRef("gap").NoneToOne()
        ).NoneToMany()).Val(TestNodeTag.GetFabric(TestNodeTag.Type.FILE));

    //System.out.print(FindNode("file"));
    Compile();
    //System.out.print(FindNode("file"));
}};

Grammar.Node fileNode = grammar.FindNode("file");

String testFile1 =
    "some0Ident = \"some value \\\\aa\\\"bb\"\n" +
    "\n" +
    "   \t;a=1;b=-2;/* Some comment a*b/*/\n" +
    "/* multiline\r\n" +
    "comment*/\r" +
    "  c =   3;";

TreeMap<String, Object> expectedData = new TreeMap<String, Object>() {{
    put("some0Ident", "some value \\aa\"bb");
    put("a", 1);
    put("b", -2);
    put("c", 3);
}};

void
VerifyResult(Map<String, Object> result, Map<String, Object> expected)
{
    if (result.size() != expected.size()) {
        throw new RuntimeException(String.format("Unexpected result size: %d, expected %d",
                                                 result.size(), expected.size()));
    }
    for (String key: expected.keySet()) {
        if (!result.containsKey(key)) {
            throw new RuntimeException("Expected value not found: " + key);
        }
        if (!expected.get(key).equals(result.get(key))) {
            throw new RuntimeException(String.format("Unexpected value: [%s] = %s, expected %s",
                                                     key, result.get(key), expected.get(key)));
        }
    }
}

@Test public void
Basic()
{
    Parser parser = ParserUtil.TestParser(fileNode, testFile1);
    Map<String, Object> result = Compile(parser.GetResult(), parser.GetSummary());
    VerifyResult(result, expectedData);
}

@Test public void
Spaces()
{
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
        sb.append(' ');
    }
    ParserUtil.TestParser(fileNode, sb.toString());
}

@Test public void
Empty()
{
    ParserUtil.TestParser(fileNode, "");
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
UnterminatedStatement()
{
    //XXX
    //make it identify unterminated statement only
    ParserUtil.TestParser(fileNode, "a = 1",
                          new ParserUtil.Error(Parser.ErrorCode.INCOMPLETE_NODE, 1, 5));
}

@Test public void
InvalidEscape()
{
    ParserUtil.TestParser(fileNode, "a = \"some \\w value\";",
                          new ParserUtil.Error(TestNodeTag.ErrorCode.INVALID_ESCAPE, 1, 11));
}

@Test public void
InvalidNumber()
{
    ParserUtil.TestParser(fileNode, "a = 9999999999999999999;",
                          new ParserUtil.Error(TestNodeTag.ErrorCode.INVALID_NUMBER, 1, 4));
}

@Test public void
InvalidSyntaxRecovery()
{
    ParserUtil.TestParser(fileNode, "a = 1 b = 2;",
                          new ParserUtil.Error(Parser.ErrorCode.PARSING_FAILED, 1, 6));
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
                          new ParserUtil.Error(Parser.ErrorCode.PARSING_FAILED, 1, 5));
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

    ParserUtil.TestParser(grammar.FindNode("file"), "",
                          new ParserUtil.Error(Parser.ErrorCode.INCOMPLETE_NODE));

    ParserUtil.TestParser(grammar.FindNode("file"), "aa",
                          new ParserUtil.Error(Parser.ErrorCode.INCOMPLETE_NODE));

    ParserUtil.TestParser(grammar.FindNode("file"), "aaa");
    ParserUtil.TestParser(grammar.FindNode("file"), "aaaa");
    ParserUtil.TestParser(grammar.FindNode("file"), "aaaaa");

    ParserUtil.TestParser(grammar.FindNode("file"), "aaaaaa",
                          new ParserUtil.Error(Parser.ErrorCode.INCOMPLETE_NODE, 1, 0));

    ParserUtil.TestParser(grammar.FindNode("file"), "aaaaaaa");

    ParserUtil.TestParser(grammar.FindNode("file"), "aaaaaaaa",
                          new ParserUtil.Error(Parser.ErrorCode.PARSING_FAILED, 1, 7));
}

}
