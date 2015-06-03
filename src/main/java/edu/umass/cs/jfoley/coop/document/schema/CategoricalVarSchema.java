package edu.umass.cs.jfoley.coop.document.schema;

import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.kinds.MappingCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.jfoley.coop.document.DocVarSchema;
import org.lemurproject.galago.utility.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class CategoricalVarSchema extends DocVarSchema<String> {
  private final List<String> values;
  private final boolean definedInAdvance;

  public CategoricalVarSchema(String name, List<String> values, boolean definedInAdvance) {
    super(name);
    this.values = values;
    this.definedInAdvance = definedInAdvance;
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

  @Override
  public Class<String> getInnerClass() {
    return String.class;
  }

  @Override
  public void encounterValue(String value) {
    if(definedInAdvance) {
      if(!values.contains(value)) {
        throw new RuntimeException("Schema error, found categorical value for "+name+" in corpus of: ``"+value+"'' that was not defined in the schema yet!");
      }
    } else {
      values.add(value);
    }
  }

  public static DocVarSchema create(String name, Parameters args) {
    if (args.isList("values")) {
      return new CategoricalVarSchema(name, args.getAsList("values", String.class), true);
    }
    return new CategoricalVarSchema(name, new ArrayList<>(), false);
  }
}
