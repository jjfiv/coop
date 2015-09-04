package edu.umass.cs.jfoley.coop.conll.classifier.labeldb;

import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;

import java.io.File;
import java.sql.SQLException;

/**
 * @author jfoley
 */
public class DBConfig {
  private final String jdbcURL;
  private final String password;
  private final String user;

  public DBConfig(String jdbcURL, String user, String pass) {
    this.jdbcURL = jdbcURL;
    this.user = user;
    this.password = pass;
  }
  public DBConfig(String jdbcURL) {
    this(jdbcURL, "user", "");
  }
  public String getJDBCURL() { return jdbcURL; }
  public String getUser() { return user; }
  public String getPassword() { return password; }

  public static DBConfig h2File(File file) {
    assert(!file.getName().endsWith(".mv.db"));
    return new DBConfig("jdbc:h2:file:"+file.getAbsolutePath()+";DB_CLOSE_DELAY=-1");
  }

  public ConnectionSource getConnectionSource() throws SQLException {
    return new JdbcConnectionSource(getJDBCURL(), getUser(), getPassword());
  }
}