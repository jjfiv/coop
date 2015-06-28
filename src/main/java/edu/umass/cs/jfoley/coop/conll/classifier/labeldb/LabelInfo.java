package edu.umass.cs.jfoley.coop.conll.classifier.labeldb;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import org.lemurproject.galago.utility.Parameters;

/**
 * A label is a set of judgments, created by a user, Only positive judgments are really recorded
 * @author jfoley.
 */
@DatabaseTable(tableName = "labels")
public class LabelInfo {
  @DatabaseField(generatedId = true, throwIfNull = true)
  public int id; // not editable or necessarily human-readable

  @DatabaseField(width=256)
  public String name; // for display

  @DatabaseField(width=4096)
  public String description; // for display;

  public Parameters toJSON() {
    Parameters output = Parameters.create();
    output.put("id", id);
    output.put("name", name);
    output.put("description", description);
    return output;
  }
}
