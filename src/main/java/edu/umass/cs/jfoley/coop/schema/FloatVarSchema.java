package edu.umass.cs.jfoley.coop.schema;

import ciir.jfoley.chai.math.StreamingStats;
import edu.umass.cs.ciir.waltz.coders.Coder;
import edu.umass.cs.ciir.waltz.coders.kinds.FloatCoder;
import edu.umass.cs.jfoley.coop.document.DocVar;
import org.lemurproject.galago.utility.Parameters;

import java.util.Map;

/**
 * @author jfoley
 */
public class FloatVarSchema extends DocVarSchema<Float> {
  double mean;
  double max;
  double min;
  double total;
  double variance;
  double stddev;
  double count;
  StreamingStats stats = null;

  public FloatVarSchema(String name) {
    super(name);
    mean = 0.0;
    variance = 0.0;
    stddev = 0.0;
    max = Double.MIN_VALUE;
    min = Double.MAX_VALUE;
    total = 0;
    count = 0.0;
  }

  @Override
  public Class<Float> getInnerClass() {
    return Float.class;
  }

  @Override
  public DocVar<Float> createValue(Object obj) {
    if(obj instanceof Number) {
      return new DocVar<>(this, ((Number) obj).floatValue());
    } else if(obj instanceof String) {
      return new DocVar<>(this, Float.parseFloat((String) obj));
    }
    throw new RuntimeException("Couldn't handle incoming object: "+obj+" "+obj.getClass());
  }

  @Override
  public Parameters toJSON() {

    Map<String,Double> p = stats.features();
    mean = p.get("mean");
    variance = p.get("variance");
    stddev = p.get("stddev");
    max = p.get("max");
    min = p.get("min");
    total = p.get("total");
    count = p.get("count");

    Parameters json = Parameters.wrap(stats.features());
    json.put("type", "float");
    json.put("name", name);
    return json;
  }

  @Override
  public void encounterValue(Float value) {
    if(stats == null) {
      stats = new StreamingStats();
      // TODO init?
    }
    stats.push(value);
  }

  @Override
  public Coder<Float> getCoder() {
    return new FloatCoder();
  }

  public static FloatVarSchema create(String name, Parameters p) {
    FloatVarSchema fvs = new FloatVarSchema(name);
    fvs.mean = p.get("mean", 0.0);
    fvs.variance = p.get("variance", 0.0);
    fvs.stddev = p.get("stddev", 0.0);
    fvs.max = p.get("max", Double.MIN_VALUE);
    fvs.min = p.get("min", Double.MAX_VALUE);
    fvs.total = p.get("total", 0);
    fvs.count = p.get("count", 0.0);
    return fvs;
  }
}
