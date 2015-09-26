package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.collections.list.FixedSlidingWindow;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.jfoley.coop.conll.server.ServerErr;
import edu.umass.cs.jfoley.coop.conll.server.ServerFn;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.util.WordLists;
import org.lemurproject.galago.tupleflow.web.WebHandler;
import org.lemurproject.galago.tupleflow.web.WebServer;
import org.lemurproject.galago.tupleflow.web.WebServerException;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StreamUtil;
import org.lemurproject.galago.utility.StringPooler;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author jfoley
 */
public class YFQServer implements Closeable, WebHandler {
  static Logger logger = Logger.getLogger("YFQServer");
  private List<YearFact> facts;
  TIntObjectHashMap<YearFact> factById;
  private AtomicInteger nextFactId = new AtomicInteger(1);

  private final Directory dbDir;
  private AtomicBoolean dirty = new AtomicBoolean(false);
  private AtomicBoolean running = new AtomicBoolean(true);
  public final Directory htmlDir;
  private Map<String, ServerFn> apiMethods;

  WebServer webServer;

  private static final String shutdownFileName = "shutdown.json.gz";
  FixedSlidingWindow<File> backupFiles = new FixedSlidingWindow<>(10);
  private Thread saveOccasionally;

  public void handleAPI(HttpServletRequest request, HttpServletResponse response, String method, String path) throws IOException {
    Parameters req;
    switch (method) {
      case "GET":
        req = WebServer.parseGetParameters(request);
        break;
      case "POST":
        req = parseBody(request);
        break;
      default:
        doError(response, ServerErr.BadRequest, "Unsupported method.");
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

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response) throws Exception {
    String method = request.getMethod();
    String path = request.getPathInfo();

    // safen-up.
    if(path.contains("..")) doError(response, ServerErr.BadRequest, "Bad Request; contains '..'");

    if(path.startsWith("/api/")) {
      handleAPI(request, response, method, path);
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

  public static class UserSubmittedQuery {
    final int id;
    final String user;
    final long time;
    final String query;
    public long deleted;

    public UserSubmittedQuery(int id, String user, long time, String query) {
      this.id = id;
      this.user = user;
      this.time = time;
      this.query = query;
      this.deleted = 0;
    }

    @Nonnull
    public Parameters asJSON() {
      return Parameters.parseArray(
          "id", id,
          "user", user,
          "time", time,
          "query", query,
          "deleted", deleted
      );
    }

    @Nonnull
    public static UserSubmittedQuery parseJSON(Parameters input) {
      return new UserSubmittedQuery(input.getInt("id"), input.get("user", "jfoley"), input.getLong("time"), input.getString("query"));
    }
  }

  public static class YearFact {
    private final int year;
    private final String html;
    private final int id;
    private final ArrayList<UserSubmittedQuery> queries;

    public YearFact(int id, int year, String fact) {
      this.id = id;
      this.year = year;
      this.html = fact;
      this.queries = new ArrayList<>();
    }

    @Nonnull
    public Parameters asJSON() {
      return Parameters.parseArray(
          "id", id,
          "year", year,
          "html", html,
          "queries", ListFns.map(queries, UserSubmittedQuery::asJSON)
      );
    }

    @Nonnull
    public static YearFact parseJSON(Parameters input) {
      int year = input.getInt("year");
      int id = input.getInt("id");
      String html = input.getString("html");
      List<Parameters> queries = input.getAsList("queries", Parameters.class);
      YearFact fact = new YearFact(id, year, html);
      fact.queries.addAll(ListFns.map(queries, UserSubmittedQuery::parseJSON));
      return fact;
    }

    public ArrayList<UserSubmittedQuery> getQueries() { return queries; }
    public String getHtml() { return html; }
    public int getYear() { return year; }
    public int getId() { return id; }
  }

  public YFQServer(Parameters argp) throws IOException {
    this.dbDir = new Directory(argp.get("save", "db.saves"));

    dbDir.ls(System.err);
    String resumeFile = argp.get("resume", shutdownFileName);
    htmlDir = Directory.Read(argp.get("html", "coop/yfq_query_writer"));

    apiMethods = new HashMap<>();
    apiMethods.put("debug", (p) -> p);
    apiMethods.put("quit", (p) -> {
      this.running.lazySet(false);
      try {
        this.webServer.stop();
      } catch (WebServerException e) {
        throw new RuntimeException(e);
      }
      return p;
    });
    apiMethods.put("db", p -> saveToJSON());
    apiMethods.put("rand", p -> {
      Random rand = new Random();
      int index = rand.nextInt(facts.size());
      return facts.get(index).asJSON();
    });
    apiMethods.put("suggestQuery", p -> {
      int factId = p.getInt("factId");
      UserSubmittedQuery q = new UserSubmittedQuery(
          nextFactId.incrementAndGet(), p.getString("user"), System.currentTimeMillis(), p.getString("query"));
      submitQuery(factId, q);
      dirty.lazySet(true);
      return q.asJSON();
    });
    apiMethods.put("deleteQuery", p -> {
      int factId = p.getInt("factId");
      int queryId = p.getInt("queryId");
      return Parameters.parseArray("deleted", deleteQuery(factId, queryId));
    });
    apiMethods.put("fact", p -> factById.get(p.getInt("id")).asJSON());

    // bootstrap! / import!
    if(!dbDir.child(resumeFile).exists()) {
      logger.info("Import first time.");
      HashSet<String> stopwords = new HashSet<>(Objects.requireNonNull(WordLists.getWordList("inquery")));

      Parameters json = Parameters.parseStream(IO.openInputStream("coop/data/ecir15.wiki-year-facts.json.gz"));
      List<Parameters> factJSON = json.getAsList("data", Parameters.class);

      int id = 1;
      facts = new ArrayList<>();
      for (Parameters fact : factJSON) {
        String yearStr = fact.getString("year");
        if (yearStr.endsWith("BC")) continue;
        int year = Integer.parseInt(yearStr);
        if (year >= 1000 && year <= 1925) {
          String html = fact.getString("fact");
          TagTokenizer tok = new TagTokenizer();
          StringPooler.disable();
          boolean nonStopword = false;
          for (String term : tok.tokenize(html).terms) {
            if(stopwords.contains(term)) continue;
            nonStopword = true; break;
          }

          if(nonStopword) {
            facts.add(new YearFact(id++, year, html));
          }
        }
      }
    } else {
      logger.info("Resuming server.");
      load(dbDir.child(resumeFile));
    }

    dirty.set(true);

    this.saveOccasionally = new Thread(() -> {
      while(running.get()) {
        saveIfDirty();
        try {
          Thread.sleep(30*1000); // every 30s
        } catch (InterruptedException e) { }
      }
    });
  }

  private synchronized boolean deleteQuery(int factId, int queryId) {
    logger.info("deleteQuery: "+factId+" "+queryId);
    boolean change = false;
    for (UserSubmittedQuery query : factById.get(factId).queries) {
      if(query.id == queryId) {
        query.deleted = System.currentTimeMillis();
        change = true;
      }
    }
    if(change) {
      dirty.lazySet(true);
    }
    return change;
    //return factById.get(factId).queries((q) -> q.id == queryId);
  }

  private synchronized void submitQuery(int factId, UserSubmittedQuery q) {
    logger.info("submitQuery: "+factId+" "+q.asJSON());
    factById.get(factId).queries.add(q);
  }

  private void saveForShutdown() {
    try {
      save(this.dbDir.child(shutdownFileName));
    } catch (IOException e) {
      throw new RuntimeException("Couldn't save for shutdown!", e);
    }
  }

  synchronized Parameters saveToJSON() {
    Parameters db = Parameters.create();
    db.put("facts", ListFns.map(facts, YearFact::asJSON));
    return db;
  }

  private void save(File fp) throws IOException {
    Parameters db = saveToJSON();
    try (PrintWriter pw = IO.openPrintWriter(fp.getAbsolutePath())) {
      pw.println(db);
    }
  }

  public void load(File fileName) throws IOException {
    try {
      ArrayList<YearFact> newFacts = new ArrayList<>();
      Parameters db = Parameters.parseStream(IO.openInputStream(fileName));
      TIntObjectHashMap<YearFact> byId = new TIntObjectHashMap<>();
      int maxId = 1;
      for (Parameters yfjson : db.getList("facts", Parameters.class)) {
        YearFact yf = YearFact.parseJSON(yfjson);
        newFacts.add(yf);
        byId.put(yf.id, yf);
        for (UserSubmittedQuery query : yf.queries) {
          maxId = Math.max(maxId, query.id);
        }
      }
      synchronized (this) {
        facts = newFacts;
        factById = byId;
        nextFactId.set(maxId);
      }
    } catch (Exception e) {
      logger.log(Level.WARNING, "Loading of "+fileName+" failed!");
    }
  }

  public static void main(String[] args) throws IOException, WebServerException {
    Parameters argp = Parameters.parseArgs(args);
    try (YFQServer server = new YFQServer(argp)) {
      logger.info("Loaded: " + server.facts.size() + " facts!");
      WebServer ws = WebServer.start(argp, server);
      server.webServer = ws;
      ws.join();
    }
  }


  @Override
  public void close() {
    try {
      logger.info("Shutting down background-thread...");
      running.set(false);
      saveOccasionally.join();
      logger.info("Shutting down background-thread...DONE.");
    } catch (InterruptedException e) {
      logger.log(Level.WARNING, e.getMessage(), e);
    }
    logger.info("Saving for shutdown...");
    saveForShutdown();
    logger.info("Saving for shutdown...DONE.");
  }

  public void saveIfDirty() {
    if(dirty.compareAndSet(true, false)) {
      long timestamp = System.currentTimeMillis();
      try {
        File backupFile = dbDir.child("db" + timestamp + ".json.gz");
        save(backupFile);
        File old;
        synchronized (this) {
          old = backupFiles.replace(backupFile);
        }
        if(old != null) {
          if(!old.delete()) {
            logger.log(Level.WARNING, "Couldn't delete old backup: " + old);
          }
        }
      } catch (Exception e) {
        logger.log(Level.WARNING, "Couldn't save timestamp :(", e);
      }
    }
  }
}
