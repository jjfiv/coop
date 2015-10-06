package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.conll.server.ServerErr;
import edu.umass.cs.jfoley.coop.conll.server.ServerFn;
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
  public final CoopIndex coopIndex;
  public final Directory htmlDir;
  private Map<String, ServerFn> apiMethods;

  public JSONServer(Directory coopDir, Directory htmlDir) throws IOException {
    this.coopIndex = new IntCoopIndex(coopDir);
    this.htmlDir = htmlDir;

    apiMethods = new ConcurrentHashMap<>();
    apiMethods.put("debug", (p) -> p);
    IndexFns.setup(coopIndex, apiMethods);
  }

  public static void main(String[] args) throws IOException, WebServerException {
    Parameters argp = Arguments.parse(args);

    String defaultIndex = "robust.ints";

    Directory input = Directory.Read(argp.get("input", defaultIndex));
    Directory htmlDir = Directory.Read(argp.get("html", "coop/front_html"));

    JSONServer server = new JSONServer(input, htmlDir);
    WebServer ws = WebServer.start(argp.get("port", 2347), server);


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
    if(path.contains("..")) doError(response, ServerErr.BadRequest, "Bad Request; contains '..'");

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
        doError(response, ServerErr.NotFound, "Couldn't find resource for: "+path);
        //response.sendError(ServerErr.NotFound, path);
        return;
      }

      response.setContentType(contentType);
      try (InputStream read = is; OutputStream out = response.getOutputStream()) {
        StreamUtil.copyStream(read, out);
        response.setStatus(200);
        return;
      }
    }

    doError(response, ServerErr.NotFound, "No response for: "+path);
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

    assert(path.startsWith("/api/"));

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

      response.setStatus(200);

    } catch (IllegalArgumentException iae) {
      iae.printStackTrace(System.err);
      doError(response, ServerErr.BadRequest, iae.getMessage());
    } catch (ServerErr err) {
      doError(response, err.code, err.msg);
    } catch (AssertionError e) {
      e.printStackTrace(System.err);
      try (PrintWriter out = response.getWriter()) {
        e.printStackTrace(out);
      }
      doError(response, 501, "Assertion Failed: "+e.getMessage());
    } catch (Exception e) {
      e.printStackTrace(System.err);
      try (PrintWriter out = response.getWriter()) {
        e.printStackTrace(out);
      }
      doError(response, 501, e.getMessage());
    }
  }

  public static void doError(HttpServletResponse response, int code, String message) throws IOException {
    response.setStatus(code);
    response.setContentType("application/json");
    PrintWriter out = response.getWriter();
    out.println(Parameters.parseArray("message", message).toString());
    out.close();
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
