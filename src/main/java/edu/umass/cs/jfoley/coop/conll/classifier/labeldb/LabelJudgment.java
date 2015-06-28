package edu.umass.cs.jfoley.coop.conll.classifier.labeldb;

import ciir.jfoley.chai.lang.annotations.UsedByReflection;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * @author jfoley.
 */
@DatabaseTable(tableName = "labeljudgments")
public class LabelJudgment {
  @DatabaseField(canBeNull = false, throwIfNull = true, columnName = "labelId")
  public int labelId;
  @DatabaseField(canBeNull = false, throwIfNull = true, columnName = "tokenId")
  public int tokenId;
  @DatabaseField(canBeNull = false)
  public boolean positive;
  @DatabaseField(canBeNull = false)
  public long time;

  @UsedByReflection
  public LabelJudgment() {
    time = System.currentTimeMillis();
  };

  public LabelJudgment(int classifier, int token, boolean positive, long time) {
    this.labelId = classifier;
    this.tokenId = token;
    this.positive = positive;
    this.time = time;
  }
}
