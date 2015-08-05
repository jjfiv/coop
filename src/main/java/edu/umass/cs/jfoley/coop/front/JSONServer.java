package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.jfoley.coop.conll.server.ServerErr;
import edu.umass.cs.jfoley.coop.conll.server.ServerFn;
import edu.umass.cs.jfoley.coop.index.IndexReader;
import org.lemurproject.galago.tupleflow.web.WebHandler;
import org.lemurproject.galago.tupleflow.web.WebServer;
import org.lemurproject.galago.tupleflow.web.WebServerException;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StreamUtil;
import org.lemurproject.galago.utility.tools.Arguments;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jfoley
 */
public class JSONServer implements WebHandler {
  public final IndexReader coopIndex;
  public final Directory htmlDir;
  private Map<String, ServerFn> apiMethods;

  public JSONServer(Directory coopDir, Directory htmlDir) throws IOException {
    this.coopIndex = new IndexReader(coopDir);
    this.htmlDir = htmlDir;

    apiMethods = new ConcurrentHashMap<>();
    apiMethods.put("debug", (p) -> p);
    IndexFns.setup(coopIndex, apiMethods);
  }

  public static void main(String[] args) throws IOException, WebServerException {
    Parameters argp = Arguments.parse(args);
    Directory input = Directory.Read(argp.get("input", "bills.index"));
    Directory htmlDir = Directory.Read(argp.get("html", "coop/front_html"));

    JSONServer server = new JSONServer(input, htmlDir);
    WebServer ws = WebServer.start(argp.get("port", 1234), server);


    System.out.println("Started server: "+ws.getURL()+" port="+ws.getPort());

    ws.join();
  }

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response) throws Exception {
    String method = request.getMethod();
    boolean GET = method.equals("GET");
    boolean POST = method.equals("POST");

    String path = request.getPathInfo();

    // safen-up.
    if(path.contains("..")) response.sendError(ServerErr.BadRequest);

    if(path.startsWith("/api/")) {
      handleAPI(request, response, method, path);
      return;
    }

    if(path.equals("/")) {
      path = "/index.html";
    }

    String contentType = null;
    if(path.endsWith(".html")) {
      contentType = "text/html";
    } else if(path.endsWith(".js")) {
      contentType = "application/javascript";
    } else if(path.endsWith(".css")) {
      contentType = "text/css";
    }

    if(contentType != null) {
      InputStream is = IO.openInputStream(htmlDir.childPath(path));
      if(is == null) {
        response.sendError(ServerErr.NotFound, path);
        return;
      }

      response.setContentType(contentType);
      try (InputStream read = is; OutputStream out = response.getOutputStream()) {
        StreamUtil.copyStream(read, out);
        response.setStatus(200);
        return;
      }
    }

    response.sendError(ServerErr.NotFound);
  }


  private void handleAPI(HttpServletRequest request, HttpServletResponse response, String method, String path) throws IOException {
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

    try {
      response.setStatus(200);
      response.setContentType("application/json");
      // allow other domains to use this from JS
      response.addHeader("Access-Control-Allow-Origin", "*");

      PrintWriter out = response.getWriter();
      long start = System.currentTimeMillis();
      Parameters json = apiFn.handleRequest(req);
      long end = System.currentTimeMillis();
      json.put("time", end-start);
      out.println(json.toString());
      out.close();

    } catch (IllegalArgumentException iae) {
      iae.printStackTrace(System.err);
      response.sendError(ServerErr.BadRequest, iae.getMessage());
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
}
