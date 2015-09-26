package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.collections.list.FixedSlidingWindow;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.jfoley.coop.conll.server.ServerErr;
import edu.umass.cs.jfoley.coop.conll.server.ServerFn;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.Tag;
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
  }

  public static class UserRelevanceJudgment {
    final String user;
    final long time;
    final int relevance;
    private final int queryId;

    public UserRelevanceJudgment(String user, int queryId, long time, int relevance) {
      this.queryId = queryId;
      this.user = user;
      this.time = time;
      this.relevance = relevance;
    }

    @Nonnull
    public Parameters asJSON() {
      Parameters p = Parameters.create();
      p.put("user", user);
      p.put("queryId", queryId);
      p.put("time", time);
      p.put("relevance", relevance);
      return p;
    }

    @Nonnull
    public static UserRelevanceJudgment parseJSON(Parameters input) {
      return new UserRelevanceJudgment(input.getString("user"), input.getInt("queryId"), input.getLong("time"), input.getInt("relevance"));
    }
  }

  public static class YearFact {
    private final int year;
    private final String html;
    private final int id;
    private final ArrayList<UserSubmittedQuery> queries;
    private final Map<String,List<UserRelevanceJudgment>> entities;

    public YearFact(int id, int year, String fact) {
      this.id = id;
      this.year = year;
      this.html = fact;
      this.queries = new ArrayList<>();
      this.entities = new HashMap<>();
    }

    @Nonnull
    public Parameters asJSON() {
      return Parameters.parseArray(
          "id", id,
          "year", year,
          "html", html,
          "queries", ListFns.map(queries, UserSubmittedQuery::asJSON),
          "entities", Parameters.wrap(MapFns.mapValues(entities, (js) -> ListFns.map(js, UserRelevanceJudgment::asJSON)))
      );
    }

    @Nonnull
    public static YearFact parseJSON(Parameters input) {
      YearFact fact = new YearFact(
          input.getInt("id"),
          input.getInt("year"),
          input.getString("html"));

      List<Parameters> queries = input.getAsList("queries", Parameters.class);
      fact.queries.addAll(ListFns.map(queries, UserSubmittedQuery::parseJSON));

      Parameters entities = input.getMap("entities");
      for (String entity : entities.keySet()) {
        fact.entities.put(entity, ListFns.map(entities.getAsList(entity, Parameters.class), UserRelevanceJudgment::parseJSON));
      }

      return fact;
    }

    public ArrayList<UserSubmittedQuery> getQueries() { return queries; }
    public String getHtml() { return html; }
    public int getYear() { return year; }
    public int getId() { return id; }
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
    apiMethods.put("db", p -> saveToJSON());
    apiMethods.put("rand", p -> {
      Random rand = new Random();
      int index = rand.nextInt(facts.size());
      return facts.get(index).asJSON();
    });
    apiMethods.put("suggestQuery", p -> {
      logger.info(p.toString());
      int factId = p.getInt("factId");
      UserSubmittedQuery q = new UserSubmittedQuery(
          nextFactId.incrementAndGet(), p.getString("user"), System.currentTimeMillis(), p.getString("query"));
      return submitQuery(factId, q);
    });
    apiMethods.put("deleteQuery", p -> {
      int factId = p.getInt("factId");
      int queryId = p.getInt("queryId");
      return deleteQuery(factId, queryId);
    });
    apiMethods.put("judgeEntity", p -> {
      int factId = p.getInt("factId");
      String entity = p.getString("entity");
      UserRelevanceJudgment rel = new UserRelevanceJudgment(
          p.getString("user"),
          p.getInt("queryId"),
          System.currentTimeMillis(),
          p.getInt("relevance"));
      return addJudgment(factId, entity, rel);
    });
    apiMethods.put("save", p -> { saveAnyway(); return null; });
    apiMethods.put("fact", p -> factById.get(p.getInt("id")).asJSON());
    apiMethods.put("judged", p -> judgedMapping());
  }

  public YFQServer(Parameters argp) throws IOException {
    this.dbDir = new Directory(argp.get("save", "coop/yfq.db.saves"));
    if(!argp.containsKey("port")) {
      argp.put("port", 1234);
    }

    System.err.println("Listing: ");
    dbDir.ls(System.err);

    long newestTime = 0;
    File newestBackup = null;
    for (File file : dbDir.children()) {
      if(!file.isFile()) continue;
      String name = file.getName();
      if(!name.endsWith(".json.gz") || !name.startsWith("db")) {
        continue;
      }
      try {
        String time = StrUtil.takeBeforeLast(name.substring(2), ".json.gz");
        long time_ms = Long.parseLong(time);
        if(time_ms > newestTime) {
          newestBackup = file;
        }
      } catch (Exception e) {
        continue;
      }
    }

    String resumeFile = argp.get("resume", newestBackup != null ? newestBackup.getName() : "shutdown.json.gz");

    htmlDir = Directory.Read(argp.get("html", "coop/yfq_query_writer"));

    apiMethods = new HashMap<>();
    setupFunctions();

    // bootstrap! / import!
    if(!dbDir.child(resumeFile).exists()) {
      logger.info("Import first time.");
      HashSet<String> stopwords = new HashSet<>(Objects.requireNonNull(WordLists.getWordList("inquery")));

      Parameters json = Parameters.parseStream(IO.openInputStream("coop/data/ecir15.wiki-year-facts.json.gz"));
      List<Parameters> factJSON = json.getAsList("data", Parameters.class);

      // tag tokenizer:
      TagTokenizer tok = new TagTokenizer();
      tok.addField("a");
      StringPooler.disable();

      int id = 1;
      facts = new ArrayList<>();
      factById = new TIntObjectHashMap<>();
      for (Parameters fact : factJSON) {
        String yearStr = fact.getString("year");
        if (yearStr.endsWith("BC")) continue;
        int year = Integer.parseInt(yearStr);
        if (year >= 1000 && year <= 1925) {
          String html = fact.getString("fact");
          boolean nonStopword = false;
          Document doc = tok.tokenize(html);
          for (String term : doc.terms) {
            if(stopwords.contains(term)) continue;
            nonStopword = true; break;
          }

          if(nonStopword) {
            YearFact yf = new YearFact(id++, year, html);

            for (Tag tag : doc.tags) {
              String url = tag.attributes.get("href");
              if(url == null) continue;
              String ent = StrUtil.takeAfter(url, "https://en.wikipedia.org/wiki/");
              yf.entities.put(ent, new ArrayList<>(Collections.singletonList(new UserRelevanceJudgment("WIKI-YEAR-FACTS", 0, 0, 1))));
            }

            factById.put(yf.id, yf);
            facts.add(yf);
          }
        }
      }
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
  }

  private synchronized Parameters addJudgment(int factId, String entity, UserRelevanceJudgment rel) {
    YearFact yearFact = factById.get(factId);
    MapFns.extendListInMap(yearFact.entities, entity, rel);
    return yearFact.asJSON();
  }

  private synchronized Parameters judgedMapping() {
    List<Parameters> judgedFacts = new ArrayList<>();
    for (YearFact fact : facts) {
      if(fact.queries.isEmpty()) {
        continue;
      }
      Parameters summary = Parameters.create();
      HashMap<String, String> userQueryMap = new HashMap<>();
      for (UserSubmittedQuery usq : fact.queries) {
        userQueryMap.put(usq.user, usq.query);
      }
      summary.put("users", Parameters.wrap(userQueryMap));
      summary.put("factId", fact.id);
      judgedFacts.add(summary);
    }
    return Parameters.parseArray("judged", judgedFacts);
  }

  private synchronized Parameters deleteQuery(int factId, int queryId) {
    logger.info("deleteQuery: "+factId+" "+queryId);
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
    return fact.asJSON();
  }

  private synchronized Parameters submitQuery(int factId, UserSubmittedQuery q) {
    logger.info("submitQuery: " + factId + " " + q.asJSON());
    YearFact fact = factById.get(factId);
    fact.queries.add(q);
    dirty.set(true);
    return fact.asJSON();
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
