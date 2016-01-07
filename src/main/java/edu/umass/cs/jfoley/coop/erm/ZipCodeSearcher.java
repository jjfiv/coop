package edu.umass.cs.jfoley.coop.erm;

import au.com.bytecode.opencsv.CSVReader;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.web.json.JSONAPI;
import ciir.jfoley.chai.web.json.JSONMethod;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.front.PhrasePositionsIndex;
import edu.umass.cs.jfoley.coop.phrases.PhraseHit;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitList;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitsReader;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.TIntHashSet;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.core.retrieval.Results;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.core.retrieval.prf.RelevanceModel1;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.core.util.WordLists;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class ZipCodeSearcher {
  public static void main(String[] args) throws Exception {
    TagTokenizer tok = new TagTokenizer();

    Map<Integer, Parameters> zipcodeKB = new HashMap<>();
    String zipcsv = "/home/jfoley/data/zipcodes.csv";
    try (CSVReader reader = new CSVReader(IO.openReader(zipcsv))) {
      String[] header = reader.readNext();
      //System.out.println(Arrays.toString(header));
      while (true) {
        String[] row = reader.readNext();
        if (row == null) break;

        Parameters rowP = Parameters.create();
        for (int i = 0; i < row.length; i++) {
          rowP.put(header[i], row[i]);
        }
        int zipId = Integer.parseInt(rowP.getString("zip"));
        if (zipId == 99999) {
          continue;
        } // skip the unknown-zip
        zipcodeKB.put(zipId, rowP);
      }
    }

    IntCoopIndex target = new IntCoopIndex(new Directory("/mnt/scratch3/jfoley/robust.ints"));
    LocalRetrieval galagoRet = new LocalRetrieval("/mnt/scratch3/jfoley/robust.galago");

    PhraseHitsReader eIndex = new PhraseHitsReader(target, target.baseDir, "zipcode");
    PhrasePositionsIndex entitiesIndex = new PhrasePositionsIndex(eIndex, target.getTermVocabulary(), eIndex.getPhraseVocab(), eIndex.getDocumentsByPhrase());
    IOMap<Integer, IntList> mentionToEntities = eIndex.getAmbiguousPhrases();
    assert mentionToEntities != null;

    Map<String, JSONMethod> jsonMethods = new HashMap<>();

    jsonMethods.put("/zipsearch", (argp) -> {
      Parameters output = Parameters.create();
      String query = argp.getString("query");
      int depth = argp.get("n", 10);

      List<String> tokens = tok.tokenize(query).terms;
      Node sdm = new Node("sdm");
      sdm.addTerms(tokens);

      //System.out.println(tokens);
      Results res = galagoRet.transformAndExecuteQuery(sdm, Parameters.parseArray("requested", depth));
      Map<String, Double> docPriors = MapFns.mapKeys(RelevanceModel1.logstoposteriors(res.scoredDocuments), ScoredDocument::getName);

      TIntObjectHashMap<String> idToName = new TIntObjectHashMap<>();
      IntList topDocIds = new IntList();
      for (Pair<String, Integer> docIdPair : target.getNames().getReverse(new ArrayList<>(docPriors.keySet()))) {
        topDocIds.add(docIdPair.getValue());
        idToName.put(docIdPair.getValue(), docIdPair.getKey());
      }

      List<Pair<Integer, PhraseHitList>> inBulk = eIndex.getDocumentHits().getInBulk(topDocIds);

      Map<Integer, TIntIntHashMap> countsByDoc = new HashMap<>();
      HashSet<Integer> allMentions = new HashSet<>();

      for (Pair<Integer, PhraseHitList> pair : inBulk) {
        TIntIntHashMap ecounts = countsByDoc.computeIfAbsent(pair.getKey(), missing -> new TIntIntHashMap());
        PhraseHitList dochits = pair.getValue();
        for (PhraseHit dochit : dochits) {
          allMentions.add(dochit.id());
          ecounts.adjustOrPutValue(dochit.id(), 1, 1);
        }
        ecounts.put(-1, dochits.size()); // length
        assert(ecounts.getNoEntryValue() == 0) : "No entry value should be zero!";
      }

      Map<Integer, List<Integer>> entityToMentionsInDocuments = new HashMap<>();
      TIntIntHashMap freq = entitiesIndex.getCollectionFrequencies(new IntList(allMentions));

      Set<String> stopwords = WordLists.getWordListOrDie("inquery");

      Map<Integer, String> mentionNames = new HashMap<>();
      for (int mentionId : allMentions) {
        IntList ids = mentionToEntities.get(mentionId);
        if (ids == null) {
          throw new RuntimeException();
        }

        //if(ids.size() > 10) continue;
        String mentionName = StrUtil.join(target.translateToTerms(entitiesIndex.getPhraseVocab().getForward(mentionId)));

        if(stopwords.contains(mentionName)) continue;
        for (int zip : ids) {
          MapFns.extendListInMap(entityToMentionsInDocuments, zip, mentionId);
          //Parameters kbEntry = zipcodeKB.get(zip);
          //allZipEntities.add(zip);
        }
        mentionNames.put(mentionId, mentionName);

        //System.err.println(ids+" "+mentionName);
      }
      double clen = target.getCollectionLength();

      TIntHashSet seenMentions = new TIntHashSet();
      TopKHeap<ScoredDocument> topEntities = new TopKHeap<>(20);
      TopKHeap<ScoredDocument> topMentions = new TopKHeap<>(200);
      for (Map.Entry<Integer, List<Integer>> kv : entityToMentionsInDocuments.entrySet()) {
        int zipcode = kv.getKey();
        List<Integer> mentions = kv.getValue();

        double frac = 1.0 / (double) mentions.size();
        //double frac = 1.0;

        double escore = 0;
        for (int mentionId : mentions) {
          double mscore = 0;
          double cf = freq.get(mentionId);
          IntList ids = Objects.requireNonNull(mentionToEntities.get(mentionId));
          double mfrac = 1.0 / ids.size();

          for (int docId : topDocIds) {
            int count = countsByDoc.get(docId).get(mentionId);
            if(count <= 0) continue;
            double length = countsByDoc.get(docId).get(-1);
            double docProb = docPriors.get(idToName.get(docId));

            double odds = (clen * count) / (length * cf);
            mscore += mfrac * docProb * odds;
            assert(Double.isFinite(mscore));
          }

          if(!seenMentions.contains(mentionId)) {
            topMentions.offer(new ScoredDocument(mentionNames.get(mentionId), -1, escore));
            seenMentions.add(mentionId);
          }

          escore += mscore * frac;
        }
        Parameters kbEntry = zipcodeKB.get(zipcode);
        String ename = kbEntry.getString("name") + "("+kbEntry.getString("zip")+")";
        topEntities.offer(new ScoredDocument(ename, -1, escore));
      }

      List<Parameters> mentions = new ArrayList<>();
      for (ScoredDocument scoredDocument : topMentions.getSorted()) {
        mentions.add(Parameters.parseArray(
            "score", scoredDocument.getScore(),
            "name", scoredDocument.getName(),
            "entities", ListFns.map(mentionToEntities.getOrDefault((int) scoredDocument.document, new IntList()), eid -> zipcodeKB.getOrDefault(eid, null))
        ));
      }
      output.put("mentions", mentions);

      List<Parameters> entities = new ArrayList<>();
      System.out.println("Entities: ");
      for (ScoredDocument scoredDocument : topEntities.getSorted()) {
        System.out.printf("\t%1.3f\t%s\n", scoredDocument.getScore(), scoredDocument.getName());
        entities.add(Parameters.parseArray(
            "score", scoredDocument.getScore(),
            "name", scoredDocument.getName()
        ));
      }
      output.put("entities", entities);

      List<Parameters> documents = new ArrayList<>();
      System.out.println("Documents: ");
      List<ScoredDocument> scoredDocuments = res.scoredDocuments;
      for (int i = 0; i < scoredDocuments.size(); i++) {
        ScoredDocument scoredDocument = scoredDocuments.get(i);
        if (scoredDocument.rank > 10) break;
        System.out.printf("\t%1.3f\t%s\n", scoredDocument.getScore(), scoredDocument.getName());
        String text = galagoRet.getDocument(scoredDocument.getName(), Document.DocumentComponents.JustText).text;
        Parameters docP = Parameters.parseArray(
            "score", scoredDocument.getScore(),
            "name", scoredDocument.getName()
        );
        documents.add(docP);
        docP.put("text", text);

        TopKHeap<TopKHeap.Weighted<String>> topMentionsInDoc = new TopKHeap<>(50);
        TIntIntHashMap mcounts = countsByDoc.get(topDocIds.get(i));
        mcounts.forEachEntry((mid, count) -> {
          try {
            IntList forward = entitiesIndex.getPhraseVocab().getForward(mid);
            if (forward == null) return true;
            String mentionName = StrUtil.join(target.translateToTerms(forward));
            if (stopwords.contains(mentionName)) return true;
            topMentionsInDoc.offer(new TopKHeap.Weighted<>(count, mentionName));
          } catch (IOException e) {
            e.printStackTrace();
          }
          return true;
        });


        docP.put("mentions", ListFns.map(topMentionsInDoc.getSorted(), (x) -> Parameters.parseArray("mention", x.object, "weight", x.weight)));
      }
      output.put("documents", documents);

      return output;
    });

    JSONAPI.start(1234, jsonMethods);


  }
}
