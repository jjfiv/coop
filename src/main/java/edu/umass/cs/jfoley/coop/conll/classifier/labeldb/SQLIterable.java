package edu.umass.cs.jfoley.coop.conll.classifier.labeldb;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;

import java.sql.SQLException;
import java.util.Iterator;

/**
 * @author jfoley
 */
public class SQLIterable<X> implements Iterable<X> {
  private final PreparedQuery<X> prep;
  private final Dao<X, ?> table;

  public SQLIterable(PreparedQuery<X> prep, Dao<X, ?> table) {
    this.table = table;
    this.prep = prep;
  }

  @Override
  public Iterator<X> iterator() {
    try {
      return table.iterator(prep);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}
