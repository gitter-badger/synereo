package com.biosimilarity.lift.model.specialK.Absyn; // Java Package generated by the BNF Converter.

public abstract class GuardedAgent implements java.io.Serializable {
  public abstract <R,A> R accept(GuardedAgent.Visitor<R,A> v, A arg);
  public interface Visitor <R,A> {
    public R visit(com.biosimilarity.lift.model.specialK.Absyn.Injection p, A arg);

  }

}
