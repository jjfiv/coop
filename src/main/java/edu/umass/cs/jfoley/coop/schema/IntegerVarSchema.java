package edu.umass.cs.jfoley.coop.schema;

import ciir.jfoley.chai.IntMath;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.kinds.VarInt;
import edu.umass.cs.jfoley.coop.document.DocVar;
import org.lemurproject.galago.utility.Parameters;

/**
 * @author jfoley
 */
public class IntegerVarSchema extends DocVarSchema<Integer> {
  public int maxValue;
  public int minValue;
  public int frequency;


  /** Reflection-only */
  private IntegerVarSchema() { super(null); }

  IntegerVarSchema(String name, int maxValue, int minValue, int frequency) {
     super(name);
     this.maxValue = maxValue;
     this.minValue = minValue;
     this.frequency = frequency;
   }

  @Override
  public Class<Integer> getInnerClass() {
    return Integer.class;
  }

  @Override
  public DocVar<Integer> createValue(Object obj) {
    if(obj instanceof Long) {
      return new DocVar<>(this, IntMath.fromLong((long) obj));
    } else if(obj instanceof Integer) {
      return new DocVar<>(this, ((Integer) obj).intValue());
    }
    return null;
  }


  @Override
  public Parameters toJSON() {
    return Parameters.parseArray(
        "type", "number",
        "name", name,
        "maxValue", maxValue,
        "minValue", minValue,
        "frequency", frequency
    );
  }

  @Override
  public Coder<Integer> getCoder() {
    return VarInt.instance;
  }

  @Override
  public void encounterValue(Integer value) {
    frequency++;
    maxValue = Math.max(maxValue, value);
    minValue = Math.min(minValue, value);
  }

  public static IntegerVarSchema create(String name) {
    return create(name, Parameters.create());
  }
  public static IntegerVarSchema create(String name, Parameters parameters) {
    int maxValue = parameters.get("maxValue", Integer.MIN_VALUE);
    int minValue = parameters.get("minValue", Integer.MAX_VALUE);
    int frequency = parameters.get("frequency", 0);
    return new IntegerVarSchema(name,maxValue,minValue,frequency);
  }
}
