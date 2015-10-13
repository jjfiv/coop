package edu.umass.cs.jfoley.coop.entityco;

import org.lemurproject.galago.utility.Parameters;

import java.util.HashMap;
import java.util.Map;

/**
 * @author jfoley
 */
public class EntityJudgedQuery {
  public final String qid;
  String text;
  final Map<String, Double> judgments;

  public EntityJudgedQuery(String qid, String text) {
    this.qid = qid;
    this.text = text;
    judgments = new HashMap<>();
  }

  public Parameters toJSON() {
    return Parameters.parseArray(
        "qid", qid,
        "number", qid,
        "raw", text,
        "text", "#sdm("+text+")",
        "entities", Parameters.wrap(judgments)
    );
  }

  public static EntityJudgedQuery fromJSON(Parameters p) {
    EntityJudgedQuery parsed = new EntityJudgedQuery(p.getString("qid"), p.getString("raw"));
    Parameters entities = p.getMap("entities");
    for (String eid : entities.keySet()) {
      parsed.judgments.put(eid, entities.getDouble(eid));
    }
    return parsed;
  }

  public String getText() {
    return text;
  }
}
