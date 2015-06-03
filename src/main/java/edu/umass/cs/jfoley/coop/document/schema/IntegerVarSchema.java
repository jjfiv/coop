package edu.umass.cs.jfoley.coop.document.schema;

import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.kinds.VarInt;
import edu.umass.cs.jfoley.coop.document.DocVarSchema;

/**
 * @author jfoley
 */
public class IntegerVarSchema extends DocVarSchema<Integer> {
  public IntegerVarSchema(String name) {
    super(name);
  }

  @Override
  public Coder<Integer> getCoder() {
    return VarInt.instance;
  }
}
