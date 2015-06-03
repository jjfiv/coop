package edu.umass.cs.jfoley.coop.document.schema;

import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.kinds.MappingCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.jfoley.coop.document.DocVarSchema;

import java.util.List;

/**
 * @author jfoley
 */
public class CategoricalVarSchema extends DocVarSchema<String> {
  private final List<String> values;

  public CategoricalVarSchema(String name, List<String> values) {
    super(name);
    this.values = values;
  }

  public String instance(int index) {
    return values.get(index);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Coder<String> getCoder() {
    // Maps to/from index in values array, encodes as a VarUInt
    return new MappingCoder<>(
        VarUInt.instance,
        values::indexOf, values::get);
  }
}
