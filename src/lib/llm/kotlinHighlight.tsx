import React from "react"
import { Text, type TextStyle } from "react-native"

const KEYWORDS = new Set([
    "abstract",
    "actual",
    "annotation",
    "as",
    "break",
    "by",
    "catch",
    "class",
    "companion",
    "const",
    "constructor",
    "continue",
    "crossinline",
    "data",
    "do",
    "dynamic",
    "else",
    "enum",
    "expect",
    "external",
    "final",
    "finally",
    "for",
    "fun",
    "get",
    "if",
    "import",
    "in",
    "infix",
    "init",
    "inline",
    "inner",
    "interface",
    "internal",
    "is",
    "lateinit",
    "noinline",
    "object",
    "open",
    "operator",
    "out",
    "override",
    "package",
    "private",
    "property",
    "protected",
    "public",
    "receiver",
    "reified",
    "return",
    "sealed",
    "set",
    "setparam",
    "super",
    "suspend",
    "tailrec",
    "this",
    "throw",
    "try",
    "typealias",
    "typeof",
    "val",
    "var",
    "vararg",
    "when",
    "where",
    "while",
    "yield",
])

const LITERALS = new Set(["true", "false", "null"])

type TokenKind = "comment" | "string" | "number" | "keyword" | "literal" | "type" | "annotation" | "plain"

export interface KotlinPalette {
    text: string
    comment: string
    string: string
    number: string
    keyword: string
    literal: string
    type: string
    annotation: string
}

/** VSCode-ish dark palette tuned for the dark `muted` background used by code citations. */
export const DARK_PALETTE: KotlinPalette = {
    text: "#e6edf3",
    comment: "#8b949e",
    string: "#a5d6ff",
    number: "#79c0ff",
    keyword: "#ff7b72",
    literal: "#79c0ff",
    type: "#7ee787",
    annotation: "#d2a8ff",
}

/** GitHub light palette for the light `muted` background. */
export const LIGHT_PALETTE: KotlinPalette = {
    text: "#1f2328",
    comment: "#6e7781",
    string: "#0a3069",
    number: "#0550ae",
    keyword: "#cf222e",
    literal: "#0550ae",
    type: "#116329",
    annotation: "#8250df",
}

/** Single-pass tokenizer. Order in the alternation matters: triple-quoted strings before regular strings,
 *  block comments before line comments don't conflict but block must allow newlines, etc. The fallback "any
 *  other character" group catches whitespace and punctuation as plain text. */
const TOKEN_RE =
    /("""[\s\S]*?""")|(\/\/[^\n]*)|(\/\*[\s\S]*?\*\/)|("(?:\\.|[^"\\\n])*")|('(?:\\.|[^'\\])')|(`[^`\n]+`)|(@[A-Za-z_][A-Za-z0-9_]*)|(0[xX][0-9a-fA-F_]+[Ll]?|\d[\d_]*\.\d[\d_]*[fFLl]?|\d[\d_]*[fFLl]?)|([A-Za-z_][A-Za-z0-9_]*)|([\s\S])/g

interface Token {
    kind: TokenKind
    value: string
}

function tokenize(src: string): Token[] {
    const tokens: Token[] = []
    let m: RegExpExecArray | null
    let lastIndex = 0
    TOKEN_RE.lastIndex = 0
    while ((m = TOKEN_RE.exec(src)) !== null) {
        if (m.index > lastIndex) tokens.push({ kind: "plain", value: src.slice(lastIndex, m.index) })
        const [full, tripleStr, lineCom, blockCom, dqStr, sqStr, btIdent, anno, num, ident] = m
        let kind: TokenKind = "plain"
        if (tripleStr || dqStr || sqStr) kind = "string"
        else if (lineCom || blockCom) kind = "comment"
        else if (anno) kind = "annotation"
        else if (num) kind = "number"
        else if (btIdent) kind = "type"
        else if (ident) {
            if (KEYWORDS.has(ident)) kind = "keyword"
            else if (LITERALS.has(ident)) kind = "literal"
            else if (/^[A-Z]/.test(ident)) kind = "type"
            else kind = "plain"
        }
        tokens.push({ kind, value: full })
        lastIndex = m.index + full.length
    }
    if (lastIndex < src.length) tokens.push({ kind: "plain", value: src.slice(lastIndex) })
    return tokens
}

interface KotlinCodeProps {
    text: string
    palette: KotlinPalette
    style?: TextStyle
}

/** Renders [text] as syntax-highlighted Kotlin via nested Text spans. The outer Text owns layout (font, line
 *  height, padding from the parent View) and the inner spans only set `color`. */
export function KotlinCode({ text, palette, style }: KotlinCodeProps) {
    const tokens = React.useMemo(() => tokenize(text), [text])
    return (
        <Text style={[{ color: palette.text, fontFamily: "monospace" }, style]}>
            {tokens.map((t, i) => (
                <Text key={i} style={t.kind === "plain" ? undefined : { color: palette[t.kind] }}>
                    {t.value}
                </Text>
            ))}
        </Text>
    )
}
