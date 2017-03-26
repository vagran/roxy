package org.roxy.parser;

import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import static org.roxy.parser.ParserUtil.VerifyResult;

public class ExpressionsTest {

enum PrecedenceGroup {
    EXPRESSION
}

enum Precedence {
    ADD,
    SUB,
    MUL,
    DIV
}

Grammar grammar = new Grammar() {{

    Node("decimal-digit").Def(CharRange('0', '9'));
    Node("alphabetic").Def(CharRange('a', 'z').IncludeRange('A', 'Z'));
    Node("whitespace").Def(AnyChar(" \t\r\n"));

    Node("gap").Any(
        NodeRef("whitespace")).OneToMany();

    Node("number-literal").Sequence(Char('-').NoneToOne(), NodeRef("decimal-digit").OneToMany()).
        Val(TestNodeTag.GetFabric(TestNodeTag.Type.NUM_LITERAL), true);

    Node("identifier-first-char").Any(NodeRef("alphabetic"), Char('_'));
    Node("identifier-char").Any(NodeRef("identifier-first-char"), NodeRef("decimal-digit"));
    Node("identifier").Sequence(
        NodeRef("identifier-first-char"),
        NodeRef("identifier-char").NoneToMany())
        .Val(TestNodeTag.GetFabric(TestNodeTag.Type.IDENTIFIER), true);

    Node("expression").Any(
        NodeRef("identifier"),
        NodeRef("number-literal"),

        Sequence(NodeRef("expression"), NodeRef("gap").NoneToOne(), Char('+'),
                 NodeRef("gap").NoneToOne(), NodeRef("expression"),
                 Sequence(NodeRef("gap").NoneToOne(), Char('+'),
                          NodeRef("gap").NoneToOne(), NodeRef("expression")).NoneToMany())
            .Name("add")
            .Precedence(PrecedenceGroup.EXPRESSION, Precedence.ADD)
            .Val(TestNodeTag.GetOpFabric('+')),

        Sequence(NodeRef("expression"), NodeRef("gap").NoneToOne(), Char('-'),
                 NodeRef("gap").NoneToOne(), NodeRef("expression"),
                 Sequence(NodeRef("gap").NoneToOne(), Char('-'),
                          NodeRef("gap").NoneToOne(), NodeRef("expression")).NoneToMany())
            .Name("sub")
            .Precedence(PrecedenceGroup.EXPRESSION, Precedence.SUB)
            .Val(TestNodeTag.GetOpFabric('-')),

        Sequence(NodeRef("expression"), NodeRef("gap").NoneToOne(), Char('*'),
                 NodeRef("gap").NoneToOne(), NodeRef("expression"),
                 Sequence(NodeRef("gap").NoneToOne(), Char('*'),
                          NodeRef("gap").NoneToOne(), NodeRef("expression")).NoneToMany())
            .Name("mul")
            .Precedence(PrecedenceGroup.EXPRESSION, Precedence.MUL)
            .Val(TestNodeTag.GetOpFabric('*')),

        Sequence(NodeRef("expression"), NodeRef("gap").NoneToOne(), Char('/'),
                 NodeRef("gap").NoneToOne(), NodeRef("expression"),
                Sequence(NodeRef("gap").NoneToOne(), Char('/'),
                         NodeRef("gap").NoneToOne(), NodeRef("expression")).NoneToMany())
            .Name("div")
            .Precedence(PrecedenceGroup.EXPRESSION, Precedence.DIV)
            .Val(TestNodeTag.GetOpFabric('/')),

        Sequence(Char('('), NodeRef("gap").NoneToOne(),
                 NodeRef("expression").PrecedenceRoot(PrecedenceGroup.EXPRESSION),
                 NodeRef("gap").NoneToOne(), Char(')')).Name("braces")
    );

    Node("statement").Sequence(
        NodeRef("identifier"),
        NodeRef("gap").NoneToOne(),
        Char('='),
        NodeRef("gap").NoneToOne(),
        NodeRef("expression").PrecedenceRoot(PrecedenceGroup.EXPRESSION),
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

@Test
public void
Empty()
    throws IOException
{
    ParserUtil.TestParser(fileNode, "");
}

@Test
public void
Basic()
    throws IOException
{
    Parser parser = ParserUtil.TestParser(
        fileNode,
        "a = 1;\n" +
        "b = 1 + 2;" +
        "c = a + b * 2;\n" +
        "d = a * 2 + b;" +
        "e = 2 * 3 + 4;" +
        "f = 2 * 3 * 4 * 5 + 1;" +
        "g = 1 + 2 + 3;\n" +
        "h = 1 + 2 - 3 - 4 - 5 + 6 + 7;" +
        "i = 1 + 2 * 3 * 24 / 6 - 7;" +
        "j = 1 + (2 * 3 * 4) / 6 - 7;\n" +
        "k = 1 * 2 * 3 - 5 - 6 + 7 + 8 + 10 / 5 / 2;");
    Map<String, Integer> result = Compile(parser.GetResult(), parser.GetSummary());
    TreeMap<String, Integer> expectedData = new TreeMap<String, Integer>() {{
        put("a", 1);
        put("b", 3);
        put("c", 7);
        put("d", 5);
        put("e", 10);
        put("f", 121);
        put("g", 6);
        put("h", 4);
        put("i", 18);
        put("j", -2);
        put("k", 11);
    }};
    VerifyResult(result, expectedData);
}

Map<String, Integer>
Compile(Ast ast, Summary summary)
{
    TreeMap<String, Integer> result = new TreeMap<>();
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
        result.put(identNode.str, EvaluateNode(valueNode, result, summary));
    }
    return result;
}

int
EvaluateNode(Ast.Node node, Map<String, Integer> symbols, Summary summary)
{
    TestNodeTag valueTag = (TestNodeTag)node.tag;
    if (valueTag.type == TestNodeTag.Type.NUM_LITERAL) {
        return valueTag.intValue;
    } else if (valueTag.type == TestNodeTag.Type.IDENTIFIER) {
        if (!symbols.containsKey(node.str)) {
            summary.Error(node.startPosition, TestNodeTag.ErrorCode.UNKNOWN_VARIABLE,
                          "Unknown variable: %s", node.str);
        }
        return symbols.get(node.str);
    } else if (valueTag.type ==  TestNodeTag.Type.OPERATOR) {
        switch (valueTag.operator) {
        case '+': {
            int sum = 0;
            for (Ast.Node child: node.children) {
                sum += EvaluateNode(child, symbols, summary);
            }
            return sum;
        }
        case '-': {
            int sum = EvaluateNode(node.children.get(0), symbols, summary);
            for (int i = 1; i < node.children.size(); i++) {
                sum -= EvaluateNode(node.children.get(i), symbols, summary);
            }
            return sum;
        }
        case '*': {
            int prod = 1;
            for (Ast.Node child: node.children) {
                prod *= EvaluateNode(child, symbols, summary);
            }
            return prod;
        }
        case '/': {
            int prod = EvaluateNode(node.children.get(0), symbols, summary);
            for (int i = 1; i < node.children.size(); i++) {
                prod /= EvaluateNode(node.children.get(i), symbols, summary);
            }
            return prod;
        }
        default:
            throw new IllegalStateException("Unhandled operator: " + valueTag.operator);
        }
    } else {
        throw new IllegalStateException("Invalid node in statement: " + valueTag.type);
    }
}

}
