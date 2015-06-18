package edu.umass.cs.jfoley.coop.conll.server;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import org.lemurproject.galago.tupleflow.web.WebHandler;
import org.lemurproject.galago.tupleflow.web.WebServer;
import org.lemurproject.galago.tupleflow.web.WebServerException;
import org.lemurproject.galago.utility.Parameters;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jfoley
 */
public class LabelMaker implements WebHandler, Closeable {
  private final TermBasedIndexReader index;
  private WebServer server;
  private Map<String, ServerFn> apiMethods;

  public LabelMaker(Directory indexDir) throws IOException {
    this.index = new TermBasedIndexReader(indexDir);
    this.apiMethods = new HashMap<>();
    apiMethods.put("debug", (p) -> p);
  }

  public void start(int port) throws WebServerException {
    this.setServer(WebServer.start(port, this));
  }
  public void join() throws WebServerException {
    this.server.join();
    this.server = null;
  }

  public static void main(String[] args) throws IOException, WebServerException {
    LabelMaker lm = new LabelMaker(Directory.Read("./CoNLL03.eng.train.run.stoken.index"));
    lm.start(1234);
    lm.join();
    // forces shutdown
    lm.close();
  }

  @Override
  public void close() throws IOException {
    try {
      if(server != null) {
        server.stop();
        server.join();
      }
    } catch (WebServerException e) {
      throw new RuntimeException(e);
    }
    index.close();
  }

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response) throws Exception {
    String method = request.getMethod();
    boolean GET = method.equals("GET");
    boolean POST = method.equals("POST");

    String path = request.getPathInfo();
    if((GET || POST) && path.equals("/stop")) {
      new Thread(() -> {
        try {
          server.stop();
        } catch (WebServerException e) {
          throw new RuntimeException(e);
        }
      }).start();
      response.setStatus(200);
      return;
    }

    if(path.startsWith("/api/")) {
      Parameters req;
      switch (method) {
        case "GET":
          req = WebServer.parseGetParameters(request);
          break;
        case "POST":
          req = parseBody(request);
          break;
        default:
          response.sendError(ServerErr.BadRequest, "Unsupported method.");
          return;
      }

      String endpoint = StrUtil.takeAfter(path, "/api/");
      ServerFn apiFn = apiMethods.get(endpoint);
      if(apiFn == null) {
        response.sendError(ServerErr.NotFound, "No such API call: "+endpoint);
        return;
      }
      response.setStatus(200);
      response.setContentType("application/json");
      // allow other domains to use this from JS
      response.addHeader("Access-Control-Allow-Origin", "*");

      try (PrintWriter out = response.getWriter()) {
        out.println(apiFn.handleRequest(req).toString());
      } catch (IllegalArgumentException iae) {
        iae.printStackTrace(System.err);
        response.sendError(ServerErr.BadRequest, "Illegal argument received "+iae);
      } catch (ServerErr err) {
        response.sendError(err.code, err.msg);
      } catch (AssertionError e) {
        e.printStackTrace(System.err);
        try (PrintWriter out = response.getWriter()) {
          e.printStackTrace(out);
        }
        response.sendError(501, "Assertion Failed: "+e.getMessage());
      } catch (Exception e) {
        e.printStackTrace(System.err);
        try (PrintWriter out = response.getWriter()) {
          e.printStackTrace(out);
        }
        response.sendError(501, e.getMessage());
      }
      return;
    }

    response.sendError(ServerErr.NotFound);
  }

  public static Parameters parseBody(HttpServletRequest req) throws ServerErr {
    String contentType = req.getContentType();
    String ct = StrUtil.takeBefore(contentType, ";");
    switch(ct) {
      default:
        throw new ServerErr(ServerErr.BadRequest, "Content-Type='"+contentType+"' not allowed, try 'application/json' instead.");
      case "text/json":
      case "application/json":
        try {
          return Parameters.parseReader(req.getReader());
        } catch (IOException e) {
          throw new ServerErr(400, e.getMessage());
        }
    }
  }

  public void setServer(WebServer server) {
    this.server = server;
  }
}
