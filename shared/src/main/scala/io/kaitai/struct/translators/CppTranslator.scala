package io.kaitai.struct.translators

import io.kaitai.struct.Utils
import io.kaitai.struct.exprlang.Ast
import io.kaitai.struct.exprlang.Ast.expr
import io.kaitai.struct.exprlang.DataType.{BaseType, Int1Type}

class CppTranslator(provider: TypeProvider) extends BaseTranslator(provider) {
  override def doStringLiteral(s: String): String = "std::string(\"" + s + "\")"

  override def doArrayLiteral(t: BaseType, values: Seq[expr]): String = {
    t match {
      case Int1Type(_) =>
        val encodedStr = values.map((expr) =>
          expr match {
            case Ast.expr.IntNum(x) =>
              if (x < 0 || x > 0xff) {
                throw new RuntimeException(s"got a weird byte value in byte array: $x")
              } else {
                "\\x%02X".format(x)
              }
            case _ =>
              throw new RuntimeException(s"got $expr in byte array, unable to put it literally in C++")
          }
        ).mkString
        "std::string(\"" + encodedStr + "\", " + values.length + ")"
      case _ =>
        throw new RuntimeException("C++ literal arrays are not implemented yet")
    }
  }

  override def userTypeField(value: expr, attrName: String): String =
    s"${translate(value)}->${doName(attrName)}"

  override def doName(s: String) =
    s match {
      case "_root" => s
      case "_parent" => "_parent()"
      case "_io" => "_io()"
      case _ => s"$s()"
    }

  override def doEnumByLabel(enumType: String, label: String): String =
    s"${Utils.upperCamelCase(enumType)}.${label.toUpperCase}"

  override def doStrCompareOp(left: Ast.expr, op: Ast.cmpop, right: Ast.expr) = {
    if (op == Ast.cmpop.Eq) {
      s"${translate(left)}.equals(${translate(right)})"
    } else if (op == Ast.cmpop.NotEq) {
      s"!(${translate(left)}).equals(${translate(right)})"
    } else {
      s"(${translate(left)}.compareTo(${translate(right)}) ${cmpOp(op)} 0)"
    }
  }

  override def doSubscript(container: expr, idx: expr): String =
    s"${translate(container)}.get(${translate(idx)})"
  override def doIfExp(condition: expr, ifTrue: expr, ifFalse: expr): String =
    s"(${translate(condition)}) ? (${translate(ifTrue)}) : (${translate(ifFalse)})"

  // Predefined methods of various types
  override def strToInt(s: expr, base: expr): String =
    s"Long.parseLong(${translate(s)}, ${translate(base)})"
  override def strLength(s: expr): String =
    s"${translate(s)}.length()"
  override def strSubstring(s: expr, from: expr, to: expr): String =
    s"${translate(s)}.substr(${translate(from)}, (${translate(to)}) - (${translate(from)}))"

  override def arrayFirst(a: expr): String = ???

  override def arrayLast(a: expr): String = ???
}
