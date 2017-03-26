package org.roxy.parser;

class TestNodeTag implements Ast.Tag {

public enum Type {
    STRING_CHAR,
    STRING_ESCAPE,
    STRING_LITERAL,
    NUM_LITERAL,
    IDENTIFIER,
    STATEMENT,
    OPERATOR,
    FILE
}

public interface ErrorCode {
    int INVALID_ESCAPE = Parser.ErrorCode.CUSTOM_START,
        INVALID_NUMBER = Parser.ErrorCode.CUSTOM_START + 1,
        DUP_IDENTIFIER = Parser.ErrorCode.CUSTOM_START + 2,
        UNKNOWN_VARIABLE = Parser.ErrorCode.CUSTOM_START + 3;
}

final Type type;
char escapedChar;
char operator;
int intValue;

@Override
public String
toString()
{
    switch (type) {
    case NUM_LITERAL:
        return String.format("%s(%d)", type, intValue);
    case OPERATOR:
        return String.format("%s(%c)", type, operator);
    default:
        return type.toString();
    }
}

public static Ast.TagFabric
GetFabric(Type type)
{
    return (Ast.Node node, Summary summary) -> {
        TestNodeTag tag = new TestNodeTag(type);
        tag.Compile(node, summary);
        return tag;
    };
}

public static Ast.TagFabric
GetOpFabric(char operator)
{
    return (Ast.Node node, Summary summary) -> {
        TestNodeTag tag = new TestNodeTag(Type.OPERATOR);
        tag.operator = operator;
        return tag;
    };
}

private TestNodeTag(Type type)
{
    this.type = type;
}

private void
Compile(Ast.Node node, Summary summary)
{
    switch (type) {
    case STRING_ESCAPE:
        CompileEscape(node, summary);
        break;
    case STRING_LITERAL:
        CompileString(node, summary);
        break;
    case NUM_LITERAL:
        CompileNumber(node, summary);
        break;
    }
}

private void
CompileEscape(Ast.Node node, Summary summary)
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
CompileString(Ast.Node node, Summary summary)
{
    StringBuilder sb = new StringBuilder();
    for (Ast.Node child : node.children) {
        TestNodeTag tag = (TestNodeTag) child.tag;
        if (tag.type == Type.STRING_CHAR) {
            sb.append(child.str);
        } else if (tag.type == Type.STRING_ESCAPE) {
            sb.append(tag.escapedChar);
        } else {
            throw new IllegalStateException("Invalid child node type: " + tag.type);
        }
    }
    node.str = sb.toString();
    node.children = null;
}

private void
CompileNumber(Ast.Node node, Summary summary)
{
    try {
        intValue = Integer.parseInt(node.str);
        node.str = null;
    } catch (NumberFormatException e) {
        summary.Error(node.startPosition, ErrorCode.INVALID_NUMBER,
                      "Invalid number literal: %s", e.getMessage());
    }
}

}
