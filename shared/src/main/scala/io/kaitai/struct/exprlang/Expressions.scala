package io.kaitai.struct.exprlang

import fastparse.noApi._
import Lexical.kw
import WsApi._

/**
  * Loosely based on /pythonparse/shared/src/main/scala/pythonparse/
  * from FastParse, Copyright (c) 2014 Li Haoyi (haoyi.sg@gmail.com)
  * http://www.lihaoyi.com/fastparse/
  *
  * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
  * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
  * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
  * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
  *
  * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
  * the Software.
  *
  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
  * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
  * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
  * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
  * IN THE SOFTWARE.
  */
object Expressions {

  val NAME: P[Ast.identifier] = Lexical.identifier
  val INT_NUMBER = Lexical.integer
  val FLOAT_NUMBER = Lexical.floatnumber
  val STRING: P[String] = Lexical.stringliteral

  val test: P[Ast.expr] = P( or_test ~ ("?" ~ test ~ ":" ~ test).? ).map{
      case (x, None) => x
      case (condition, Some((ifTrue, ifFalse))) => Ast.expr.IfExp(condition, ifTrue, ifFalse)
    }
  val or_test = P( and_test.rep(1, kw("or")) ).map{
    case Seq(x) => x
    case xs => Ast.expr.BoolOp(Ast.boolop.Or, xs)
  }
  val and_test = P( not_test.rep(1, kw("and")) ).map{
    case Seq(x) => x
    case xs => Ast.expr.BoolOp(Ast.boolop.And, xs)
  }
  val not_test: P[Ast.expr] = P( ("not" ~ not_test).map(Ast.expr.UnaryOp(Ast.unaryop.Not, _)) | comparison )
  val comparison: P[Ast.expr] = P( expr ~ (comp_op ~ expr).? ).map{
    case (lhs, None) => lhs
    case (lhs, Some(chunks)) =>
      val (op, rhs) = chunks
      Ast.expr.Compare(lhs, op, rhs)
  }

  // Common operators, mapped from their
  // strings to their type-safe representations
  def op[T](s: P0, rhs: T) = s.!.map(_ => rhs)
  val LShift = op("<<", Ast.operator.LShift)
  val RShift = op(">>", Ast.operator.RShift)
  val Lt = op("<", Ast.cmpop.Lt)
  val Gt = op(">", Ast.cmpop.Gt)
  val Eq = op("==", Ast.cmpop.Eq)
  val GtE = op(">=", Ast.cmpop.GtE)
  val LtE = op("<=", Ast.cmpop.LtE)
  val NotEq = op("<>" | "!=", Ast.cmpop.NotEq)
  val In = op("in", Ast.cmpop.In)
  val NotIn = op("not" ~ "in", Ast.cmpop.NotIn)
  val Is = op("is", Ast.cmpop.Is)
  val IsNot = op("is" ~ "not", Ast.cmpop.IsNot)
  val comp_op = P( LtE|GtE|Eq|Gt|Lt|NotEq|In|NotIn|IsNot|Is )
  val Add = op("+", Ast.operator.Add)
  val Sub = op("-", Ast.operator.Sub)
  val Pow = op("**", Ast.operator.Pow)
  val Mult= op("*", Ast.operator.Mult)
  val Div = op("/", Ast.operator.Div)
  val Mod = op("%", Ast.operator.Mod)
  val BitOr = op("|", Ast.operator.BitOr)
  val BitAnd = op("&", Ast.operator.BitAnd)
  val BitXor = op("^", Ast.operator.BitXor)

  def Chain(p: P[Ast.expr], op: P[Ast.operator]) = P( p ~ (op ~ p).rep ).map{
    case (lhs, chunks) =>
      chunks.foldLeft(lhs){case (lhs, (op, rhs)) =>
        Ast.expr.BinOp(lhs, op, rhs)
      }
  }
  val expr: P[Ast.expr] = P( Chain(xor_expr, BitOr) )
  val xor_expr: P[Ast.expr] = P( Chain(and_expr, BitXor) )
  val and_expr: P[Ast.expr] = P( Chain(shift_expr, BitAnd) )
  val shift_expr: P[Ast.expr] = P( Chain(arith_expr, LShift | RShift) )

  val arith_expr: P[Ast.expr] = P( Chain(term, Add | Sub) )
  val term: P[Ast.expr] = P( Chain(factor, Mult | Div | Mod) )
  val factor: P[Ast.expr] = P(
    ("+" ~ factor) |
    ("-" ~ factor).map(Ast.expr.UnaryOp(Ast.unaryop.Minus, _)) |
    ("~" ~ factor).map(Ast.expr.UnaryOp(Ast.unaryop.Invert, _)) |
    power
  )
  val power: P[Ast.expr] = P( atom ~ trailer.rep ~ (Pow ~ factor).? ).map{
    case (lhs, trailers, rhs) =>
      val left = trailers.foldLeft(lhs)((l, t) => t(l))
      rhs match{
        case None => left
        case Some((op, right)) => Ast.expr.BinOp(left, op, right)
      }
  }
  val atom: P[Ast.expr] = {
    val empty_list = ("[" ~ "]").map(_ => Ast.expr.List(Nil))
    val empty_dict = ("{" ~ "}").map(_ => Ast.expr.Dict(Nil, Nil))
    P(
      empty_list |
      empty_dict |
      "(" ~ test ~ ")" |
      "[" ~ list ~ "]" |
      "{" ~ dictorsetmaker ~ "}" |
      enumByName |
      STRING.rep(1).map(_.mkString).map(Ast.expr.Str) |
      NAME.map(Ast.expr.Name(_)) |
      FLOAT_NUMBER.map(Ast.expr.FloatNum) |
      INT_NUMBER.map(Ast.expr.IntNum)
    )
  }
  val list_contents = P( test.rep(1, ",") ~ ",".? )
  val list = P( list_contents ).map(Ast.expr.List(_))

  val trailer: P[Ast.expr => Ast.expr] = {
    val call = P("(" ~ arglist ~ ")").map{ case (args) => (lhs: Ast.expr) => Ast.expr.Call(lhs, args)}
    val slice = P("[" ~ test ~ "]").map{ case (args) => (lhs: Ast.expr) => Ast.expr.Subscript(lhs, args)}
    val attr = P("." ~ NAME).map(id => (lhs: Ast.expr) => Ast.expr.Attribute(lhs, id))
    P( call | slice | attr )
  }

  val exprlist: P[Seq[Ast.expr]] = P( expr.rep(1, sep = ",") ~ ",".? )
  val testlist: P[Seq[Ast.expr]] = P( test.rep(1, sep = ",") ~ ",".? )
  val dictorsetmaker: P[Ast.expr] = {
    val dict_item = P( test ~ ":" ~ test )
    val dict: P[Ast.expr.Dict] = P(
      (dict_item.rep(1, ",") ~ ",".?).map{x =>
        val (keys, values) = x.unzip
        Ast.expr.Dict(keys, values)
      }
    )
    P( /*dict_comp |*/ dict /*| set_comp | set*/)
  }

  val arglist: P[Seq[Ast.expr]] = P( (test).rep(0, ",") )

  val comp_if: P[Ast.expr] = P( "if" ~ test )

  val testlist1: P[Seq[Ast.expr]] = P( test.rep(1, sep = ",") )

  val enumByName: P[Ast.expr.EnumByLabel] = P( (NAME) ~ "::" ~ (NAME) ).map {
    case(enumName, enumLabel) => Ast.expr.EnumByLabel(enumName, enumLabel)
  }

  val topExpr: P[Ast.expr] = P( test ~ End )

  class ParseException(msg: String) extends RuntimeException(msg, null)

  def parse(src: String): Ast.expr = {
    val r = Expressions.topExpr.parse(src)
    r match {
      case Parsed.Success(value, index) => value
      case f: Parsed.Failure =>
        Console.err.println(s"parsing expression '${src}' failed on ${f.extra.line}:${f.extra.col}")
        Console.err.println(s"expected: ${f.extra.traced.expected}")
        throw new ParseException(f.msg)
    }
  }
}
