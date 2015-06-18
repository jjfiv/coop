package edu.umass.cs.jfoley.coop.conll.server;

/**
 * @author jfoley
 */
public class ServerErr extends RuntimeException {
  public static final int NotFound = 404;
  public static final int InternalError = 501;
  public static final int BadRequest = 400;

  public final int code;
  public final String msg;

  public ServerErr(int code, String msg) {
    this.code = code;
    this.msg = msg;
  }

  public ServerErr(String msg) {
    this(InternalError, msg);
  }

  public ServerErr(int code, Exception e) {
    this.code = code;
    this.msg = e.getMessage();
  }
  public ServerErr(String msg, Exception e) {
    super(msg, e);
    this.code = InternalError;
    this.msg = msg;
  }
}
