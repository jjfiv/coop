package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.collections.list.FixedSlidingWindow;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.conll.server.ServerErr;
import edu.umass.cs.jfoley.coop.conll.server.ServerFn;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.lemurproject.galago.tupleflow.web.WebHandler;
import org.lemurproject.galago.tupleflow.web.WebServer;
import org.lemurproject.galago.tupleflow.web.WebServerException;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StreamUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
  private AtomicInteger nextQueryId = new AtomicInteger(1);

  private IntCoopIndex pages;
  private final Directory dbDir;
  private AtomicBoolean dirty = new AtomicBoolean(false);
  private AtomicBoolean running = new AtomicBoolean(true);
  public final Directory htmlDir;
  private Map<String, ServerFn> apiMethods;

  WebServer webServer;

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
      doError(response, ServerErr.NotFound, "No such API call: "+endpoint);
      System.err.println(apiMethods);
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
      if(json == null) {
        json = Parameters.create();
      }
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
    } else if(path.endsWith(".js.map")) {
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
    System.err.println(message);
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

    public boolean isDeleted() {
      return deleted > 0;
    }
  }

  public static class UserRelevanceJudgment {
    final String item;
    final String user;
    final long time;
    final int relevance;

    public UserRelevanceJudgment(String item, String user, long time, int relevance) {
      this.item = item;
      this.user = user;
      this.time = time;
      this.relevance = relevance;
    }

    @Nonnull
    public Parameters asJSON() {
      Parameters p = Parameters.create();
      p.put("item", item);
      p.put("user", user);
      p.put("time", time);
      p.put("relevance", relevance);
      return p;
    }

    @Nonnull
    public static UserRelevanceJudgment parseJSON(Parameters input) {
      return new UserRelevanceJudgment(input.getString("item"), input.getString("user"), input.getLong("time"), input.getInt("relevance"));
    }
  }

  public static class YearFact {
    private final int year;
    private final String html;
    private final List<String> terms;
    private final int id;
    private final ArrayList<UserSubmittedQuery> queries;
    private final List<UserRelevanceJudgment> judgments;

    public YearFact(int id, int year, String fact, List<String> terms) {
      this.id = id;
      this.year = year;
      this.html = fact;
      this.terms = terms;
      this.queries = new ArrayList<>();
      this.judgments = new ArrayList<>();
    }

    @Nonnull
    public Parameters asJSON() {
      return Parameters.parseArray(
          "id", id,
          "year", year,
          "html", html,
          "terms", terms,
          "queries", ListFns.map(queries, UserSubmittedQuery::asJSON),
          "judgments", ListFns.map(judgments, UserRelevanceJudgment::asJSON)
      );
    }

    @Nonnull
    public static YearFact parseJSON(Parameters input) {
      YearFact fact = new YearFact(
          input.getInt("id"),
          input.getInt("year"),
          input.getString("html"),
          input.getAsList("terms", String.class));

      fact.queries.addAll(ListFns.map(input.getAsList("queries", Parameters.class), UserSubmittedQuery::parseJSON));
      fact.judgments.addAll(ListFns.map(input.getAsList("judgments", Parameters.class), UserRelevanceJudgment::parseJSON));
      return fact;
    }

    public ArrayList<UserSubmittedQuery> getQueries() { return queries; }
    public String getHtml() { return html; }
    public int getYear() { return year; }
    public int getId() { return id; }

    public void addJudgment(UserRelevanceJudgment judgment) {
      judgments.add(judgment);
    }

    public boolean hasJudgment() {
      for (UserRelevanceJudgment judgment : judgments) {
        if(judgment.time > 0) return true;
      }
      return false;
    }

    public boolean hasQueries() {
      return queries.size() > 0;
    }

    public List<UserRelevanceJudgment> getJudgments() {
      return judgments;
    }
  }

  public void setupFunctions() {
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
    apiMethods.put("suggestQuery", p -> {
      logger.info(p.toString());
      int factId = p.getInt("factId");
      UserSubmittedQuery q = new UserSubmittedQuery(
          nextQueryId.incrementAndGet(), p.getString("user"), System.currentTimeMillis(), p.getString("query"));
      submitQuery(factId, q);
      return getFacts();
    });
    apiMethods.put("deleteQuery", p -> {
      int factId = p.getInt("factId");
      int queryId = p.getInt("queryId");
      deleteQuery(factId, queryId);
      return getFacts();
    });
    apiMethods.put("judgeItem", p -> {
      int factId = p.getInt("factId");
      UserRelevanceJudgment rel = new UserRelevanceJudgment(
          p.getString("item"),
          p.getString("user"),
          System.currentTimeMillis(),
          p.getInt("relevance"));
      addJudgment(factId, rel);
      return getFacts();
    });
    apiMethods.put("searchPages", p -> IntCoopIndex.searchQL(pages, p));
    apiMethods.put("page", p -> {
      Parameters output = Parameters.create();

      if (p.isString("name")) {
        String name = p.getString("name");
        Integer id = pages.getNames().getReverse(name);
        if (id == null) {
          return output;
        }
        output.put("id", id);
        output.put("name", name);

        if (id != -1) {
          IntList page = new IntList(pages.getCorpus().getDocument(id));
          output.put("terms", pages.translateToTerms(page));
          output.put("termIds", page);
        }
        return output;
      } else if (p.isLong("id")) {
        int id = p.getInt("id");
        String name = pages.getNames().getForward(id);
        if (name == null) {
          return output;
        }
        output.put("id", id);
        output.put("name", name);

        if (id != -1) {
          IntList page = new IntList(pages.getCorpus().getDocument(id));
          output.put("terms", pages.translateToTerms(page));
          output.put("termIds", page);
        }
        return output;
      }
      throw new UnsupportedOperationException(p.toString());
    });
    apiMethods.put("save", p -> { saveAnyway(); return null; });
    apiMethods.put("facts", p -> getFacts());
  }

  public synchronized Parameters getFacts() {
    return Parameters.parseArray("facts", ListFns.map(facts, YearFact::asJSON));
  }

  @Nullable
  public static File getNewestSave(Directory dir) throws IOException {
    long newestTime = 0;
    File newestBackup = null;
    for (File file : dir.children()) {
      if(!file.isFile()) continue;
      String name = file.getName();
      if(!name.endsWith(".json.gz") || !name.startsWith("db")) {
        continue;
      }
      try {
        String time = StrUtil.takeBeforeLast(name.substring(2), ".json.gz");
        long time_ms = Long.parseLong(time);
        if(time_ms > newestTime) {
          newestTime = time_ms;
          newestBackup = file;
        }
      } catch (Exception e) {
        continue;
      }
    }

    return newestBackup;
  }

  public YFQServer(Parameters argp) throws Exception {
    this.dbDir = new Directory(argp.get("save", "coop/sampled.db.saves"));
    if(!argp.containsKey("port")) {
      argp.put("port", 1234);
    }

    System.err.println("Listing: ");
    dbDir.ls(System.err);

    File newestBackup = getNewestSave(dbDir);
    String resumeFile = argp.get("resume", newestBackup != null ? newestBackup.getName() : "shutdown.json.gz");

    htmlDir = Directory.Read(argp.get("html", "coop/yfq_query_writer"));

    apiMethods = new HashMap<>();
    setupFunctions();

    // bootstrap! / import!
    if(!dbDir.child(resumeFile).exists()) {
      logger.info("Import first time.");
      load(new File("coop/data/sampled-facts.json"));
      dirty.set(true);
    } else {
      logger.info("Resuming server.");
      load(dbDir.child(resumeFile));
    }

    this.saveOccasionally = new Thread(() -> {
      while(running.get()) {
        saveIfDirty();
        try {
          Thread.sleep(30*1000); // every 30s
        } catch (InterruptedException e) { }
      }
    });
    saveOccasionally.start();

    assert(factById != null);

    pages = new IntCoopIndex(Directory.Read("/mnt/scratch/jfoley/inex-page-djvu.ints"));
  }

  private synchronized Parameters addJudgment(int factId, UserRelevanceJudgment rel) {
    YearFact yearFact = factById.get(factId);
    yearFact.judgments.add(rel);
    return yearFact.asJSON();
  }

  private synchronized void deleteQuery(int factId, int queryId) {
    logger.info("deleteQuery: factId="+factId+" queryId="+queryId);
    boolean change = false;
    YearFact fact = factById.get(factId);
    for (UserSubmittedQuery query : fact.queries) {
      if(query.id == queryId) {
        query.deleted = System.currentTimeMillis();
        change = true;
      }
    }
    if(change) {
      dirty.lazySet(true);
    }
  }

  private synchronized Parameters submitQuery(int factId, UserSubmittedQuery q) {
    logger.info("submitQuery: " + factId + " " + q.asJSON());
    YearFact fact = factById.get(factId);
    fact.queries.add(q);
    dirty.set(true);
    return fact.asJSON();
  }

  private void save(File fp) throws IOException {
    Parameters db = getFacts();
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
        nextQueryId.set(maxId);
      }
    } catch (Exception e) {
      logger.log(Level.WARNING, "Loading of "+fileName+" failed!");
    }
  }

  public static void main(String[] args) throws Exception {
    Parameters argp = Parameters.parseArgs(args);
    try (YFQServer server = new YFQServer(argp)) {
      logger.info("Loaded: " + server.facts.size() + " facts!");
      WebServer ws = WebServer.start(argp, server);
      server.webServer = ws;
      ws.join();
    }
  }


  @Override
  public void close() throws IOException {
    logger.info("Closing pages index...");
    pages.close();
    logger.info("Closing pages index...DONE");

    try {
      logger.info("Shutting down background-thread...");
      running.set(false);
      saveOccasionally.join();
      logger.info("Shutting down background-thread...DONE.");
    } catch (InterruptedException e) {
      logger.log(Level.WARNING, e.getMessage(), e);
    }
    logger.info("Saving for shutdown...");
    saveAnyway();
    logger.info("Saving for shutdown...DONE.");
  }

  public void saveAnyway() {
    long timestamp = System.currentTimeMillis();
    try {
      File backupFile = dbDir.child("db" + timestamp + ".json.gz");
      logger.info("Saving "+backupFile);
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

  public void saveIfDirty() {
    if(dirty.compareAndSet(true, false)) {
      saveAnyway();
    }
  }
}
