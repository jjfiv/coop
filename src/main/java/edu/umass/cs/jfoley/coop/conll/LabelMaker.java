package edu.umass.cs.jfoley.coop.conll;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.string.StrUtil;
import org.lemurproject.galago.tupleflow.web.WebHandler;
import org.lemurproject.galago.tupleflow.web.WebServer;
import org.lemurproject.galago.tupleflow.web.WebServerException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author jfoley
 */
public class LabelMaker implements WebHandler, Closeable {
  private final TermBasedIndex.TermBasedIndexReader index;
  private WebServer server;

  public LabelMaker(Directory indexDir) throws IOException {
    this.index = new TermBasedIndex.TermBasedIndexReader(indexDir);
  }

  public static void main(String[] args) throws IOException, WebServerException {
    LabelMaker lm = new LabelMaker(Directory.Read("./CoNLL03.eng.train.run.stoken.index"));

    WebServer server = WebServer.start(1234, lm);
    lm.setServer(server);
    server.join();

    lm.close();
  }

  @Override
  public void close() throws IOException {
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
      String endpoint = StrUtil.takeAfter(path, "/api/");
      try (PrintWriter out = response.getWriter()) {
        out.println(endpoint);
      }
    }
  }

  public void setServer(WebServer server) {
    this.server = server;
  }
}
