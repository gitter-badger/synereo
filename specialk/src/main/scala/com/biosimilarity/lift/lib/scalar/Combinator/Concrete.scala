// -*- mode: Scala;-*- 
// Filename:    rlambda.scala 
// Authors:     lgm                                                    
// Creation:    Sun Feb 13 20:20:23 2011 
// Copyright:   Not supplied 
// Description: 
// ------------------------------------------------------------------------

package com.biosimilarity.lift.lib.scalar

import com.biosimilarity.lift.lib.zipper._

import scala.xml._
import scala.util.parsing.combinator._
import scala.collection.SeqProxy

import java.net.URI

trait CoreLanguageForms {
  trait RLambdaParser extends JavaTokenParsers
  with Expressions with Nominals {    
    type Nominal = Name
    type Term = Expression

    def nextVar : Nominal

    def programXform : Parser[Expression] =
      progressionXform | bindingXform
    def progressionXform : Parser[Expression] =
      repsep( expressionXform, ";" ) ^^ {
	case expr :: exprs => {
	  ( expr /: exprs )(
	    { ( acc, e ) => {
	      Application( Abstraction( List( nextVar ), e ), List( acc ) )
	    }
	   }
	  )
	}
	case Nil => BottomLiteral
      }
    def bindingXform : Parser[Expression] =
      "val"~mentionXform~"="~expressionXform~";"~programXform ^^ {
	case "val"~mention~"="~expression~";"~program => {
	  Application(
	    Abstraction(
	      List( mention.reference ),
	      program
	    ),
	    List( expression )
	  )
	}
      }    
    def expressionXform : Parser[Expression] =
      (
	applicationXform
	| abstractionXform
	| valueXform
	| mentionXform
//	| arithmeticXform
	| embeddingXform
	| "("~expressionXform~")" ^^ {
	  case "("~term~")" => term
	}
      )

    def applicationXform : Parser[Application] =
      expressionXform~"("~repsep( expressionXform, "," )~")" ^^ {
	case op~"("~actls~")" => Application( op, actls )
      }
    def abstractionXform : Parser[Abstraction] =
      "("~repsep( mentionXform, "," )~")"~"=>"~"{"~programXform~"}" ^^ {
	case "("~fmls~")"~"=>"~"{"~body~"}" =>
	  Abstraction( fmls.map( _.reference ), body )
      }
    def mentionXform : Parser[Mention] =
      (
	ident ^^ ( x => Mention( StringVariable( x ) ) )
	| quotationXform ^^ ( x => Mention( x ) )
	//| URL ^^ ( x => Mention( URLVariable( x ) ) )
      )
    def valueXform : Parser[Expression] =
      (
	"["~repsep( expressionXform, "," )~"]" ^^ {
	  case "["~exprs~"]" => ListLiteral( exprs )
	}
	| "*"~mentionXform ^^ { case "*"~Mention( v ) => Dereference( v ) }
	| stringLiteral ^^ ( x => StringLiteral( x ) )
 	| floatingPointNumber ^^ ( x => DoubleLiteral( x.toDouble ) )
 	| wholeNumber ^^ ( x => IntegerLiteral( x.toInt ) )
 	| "true" ^^ ( x => BooleanLiteral( true ) )
 	| "false" ^^ ( x => BooleanLiteral( false ) )
 	| "bot" ^^ ( x => BottomLiteral )
      );
//     def arithmeticXform : Parser[Expression] =
//       ( summationXform | multiplicationXform | negationXform 
//        | reductionXform | mentionXform | valueXform )
//     def summationXform : Parser[Expression] = 
//       repsep( multiplicationXform, "+" ) ^^ {
// 	case summands => SummationLiteral( summands )
//       }
//     def multiplicationXform : Parser[Expression] = 
//       repsep( factorXform, "*" ) ^^ {
// 	case factors => MultiplicationLiteral( factors )
//       }
//     def factorXform : Parser[Expression] =
//       negationXform | reductionXform | mentionXform | valueXform | groupXform
//     def negationXform : Parser[Expression] =
//       "-"~( reductionXform | mentionXform | valueXform ) ^^ {
// 	case "-"~expr => NegationLiteral( expr )
//       }
//     def reductionXform : Parser[Expression] = 
//       "("~expressionXform~"("~repsep( expressionXform, "," )~")"~")" ^^ {
// 	case "("~op~"("~actls~")"~")" => Application( op, actls )
//       }
//     def groupXform : Parser[Expression] =
//       "("~arithmeticXform~")" ^^ {
// 	case "("~expr~")" => expr
//       }
    def embeddingXform : Parser[Expression] =
      "{"~programXform~"}" ^^ {
	case "("~expr~")" => expr
      }
    def quotationXform : Parser[Nominal] =
      "@"~"<"~expressionXform~">" ^^ {
	case "@"~"<"~term~">" => Transcription( term )
      }    
  }
}

object CLF extends CoreLanguageForms {
  object RLP extends RLambdaParser {
    private var currVar : Nominal = Transcription( BottomLiteral )
    def nextVar : Nominal = {
      val nV =
	Transcription( Application( Mention( currVar ), List( Mention( currVar ) ) ) )
      currVar = nV
      nV
    }
  }
}
