package edu.umass.cs.jfoley.coop.entityco;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.jfoley.coop.PMITerm;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.front.PhrasePositionsIndex;
import edu.umass.cs.jfoley.coop.phrases.PhraseHit;
import edu.umass.cs.jfoley.coop.phrases.PhraseHitList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import org.lemurproject.galago.core.eval.EvalDoc;
import org.lemurproject.galago.core.eval.QuerySetResults;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.core.retrieval.Results;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.lists.Ranked;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author jfoley
 */
public class TopDocsWikiPMI {
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);

    String dataset = "robust04";
    Map<String, Set<String>> topDocsByQuery = new HashMap<>();

    // for mention->entity probs:
    LocalRetrieval jeffWiki = PMIRankingExperiment.openJeffWiki(argp);

    String index = "/tmp/";

    int depth = argp.get("docDepth", 20);

    QuerySetResults baseline = new QuerySetResults(argp.getString("baselineQuery"));
    for (String qid : baseline.getQueryIterator()) {
      for (EvalDoc evalDoc : baseline.get(qid).getIterator()) {
        if(evalDoc.getRank() <= depth) {
          MapFns.extendSetInMap(topDocsByQuery, qid, evalDoc.getName());
        }
      }
    }

    List<EntityJudgedQuery> queries = ConvertEntityJudgmentData.parseQueries(new File(argp.get("queries", "coop/data/" + dataset + ".json")));

    assert(queries.size() > 0);

    Collections.sort(queries, (lhs, rhs) -> lhs.qid.compareTo(rhs.qid));
    IntCoopIndex dbpedia = new IntCoopIndex(Directory.Read(argp.get("dbpedia", "dbpedia.ints")));
    IntCoopIndex target = new IntCoopIndex(Directory.Read(argp.get("target", index)));
    PhrasePositionsIndex eIndex = target.getEntitiesIndex();
    IOMap<Integer, IntList> ambiguous = eIndex.getPhraseHits().getAmbiguousPhrases();
    assert(ambiguous != null);

    int numEntities = argp.get("requested", 5000);
    int minEntityFrequency = argp.get("minEntityFrequency", 2);

    long start, end;
    try (PrintWriter trecrun = IO.openPrintWriter(argp.get("output", dataset + ".top20wpmi.m" + minEntityFrequency + ".trecrun"))) {
      for (EntityJudgedQuery query : queries) {
        String qid = query.qid;

        List<String> topDocs = new ArrayList<>(topDocsByQuery.get(qid));
        System.out.println(qid + " " + query.text+" topDocs: "+topDocs.size());

        IntList topDocIds = new IntList();
        for (Pair<String, Integer> docIdPair : target.getNames().getReverse(topDocs)) {
          topDocIds.add(docIdPair.getValue());
        }

        start = System.currentTimeMillis();
        List<Pair<Integer, PhraseHitList>> inBulk = eIndex.getPhraseHits().getDocumentHits().getInBulk(topDocIds);
        end = System.currentTimeMillis();

        System.out.println("Data pull: "+(end-start)+"ms. for "+topDocIds.size()+" documents.");
        TIntIntHashMap ecounts = new TIntIntHashMap();

        for (Pair<Integer, PhraseHitList> pair : inBulk) {
          //int doc = pair.getKey();
          PhraseHitList dochits = pair.getValue();

          for (PhraseHit dochit : dochits) {
            ecounts.adjustOrPutValue(dochit.id(), 1, 1);
          }
        }

        start = System.currentTimeMillis();
        TIntIntHashMap freq = eIndex.getCollectionFrequencies(new IntList(ecounts.keys()));
        end = System.currentTimeMillis();
        System.out.println("Pull efrequencies: " + (end - start) + "ms.");

        TopKHeap<PMITerm<Integer>> pmiEntities = new TopKHeap<>(numEntities);
        double collectionLength = target.getCollectionLength();
        ecounts.forEachEntry((eid, frequency) -> {
          if (frequency >= minEntityFrequency) {
            int cf = 1;
            cf = freq.get(eid);
            if (cf == freq.getNoEntryValue()) {
              cf = 1;
            }

            pmiEntities.add(new PMITerm<>(eid, cf, topDocIds.size(), frequency, collectionLength));
          }
          return true;
        });


        List<ScoredDocument> scoredEntities = new ArrayList<>();

        TObjectDoubleHashMap<String> entityScores = new TObjectDoubleHashMap<>();

        HashSet<String> entities = new HashSet<>();

        int items = 0;
        for (PMITerm<Integer> pmiEntity : pmiEntities.getSorted()) {
          int eid = pmiEntity.term;
          IntList eterms = eIndex.getPhraseVocab().getForward(eid);
          List<String> sterms = target.translateToTerms(eterms);

          IntList ids = ambiguous.get(eid);
          if (ids == null) {
            ids = new IntList();
            ids.push(eid);
          }

          List<String> names = new ArrayList<>(dbpedia.getNames().getForwardMap(ids).values());
          if (items++ < 10) {
            System.out.println(sterms + "\t" + pmiEntity.logPMI() + "\t" + names);
          }
          entities.addAll(names);
        }

        if(entities.isEmpty()) {
          System.err.println("# couldn't predict any entities for qid="+query.qid);
        }

        Parameters qp = Parameters.create();
        qp.put("working", new ArrayList<>(entities));
        qp.put("warnMissingDocuments", false);
        Node gq = new Node("sdm");
        TagTokenizer tok = new TagTokenizer();
        for (String term : tok.tokenize(query.text).terms) {
          gq.addChild(Node.Text(term));
        }
        System.err.println("Fetch WikiSDM prob for "+entities.size()+" entities: "+gq);
        Results results = jeffWiki.transformAndExecuteQuery(gq, qp);
        Map<String, Double> entityProbs = results.asDocumentFeatures();

        for (PMITerm<Integer> pmiEntity : pmiEntities.getSorted()) {
          int eid = pmiEntity.term;
          IntList ids = ambiguous.get(eid);
          if (ids == null) {
            ids = new IntList();
            ids.push(eid);
          }

          List<String> names = new ArrayList<>(dbpedia.getNames().getForwardMap(ids).values());


          int size = names.size();
          double scoreProportion = pmiEntity.logPMI();
          for (String dbpediaName: names) {
            Double escore = entityProbs.get(dbpediaName);
            if(escore == null) continue;
            double score = scoreProportion + escore - Math.log(size);
            entityScores.adjustOrPutValue(dbpediaName, score, score);
            //scoredEntities.add(new ScoredDocument(dbpediaName, -1, pmiEntity.pmi()));
          }
        }

        entityScores.forEachEntry((ent, score) -> {
          scoredEntities.add(new ScoredDocument(ent, -1, score));
          return true;
        });
        Ranked.setRanksByScore(scoredEntities);

        for (ScoredDocument entity : scoredEntities) {
          if(entity.rank < 4) {
            System.out.println("\t"+entity.documentName+" "+entity.score);
            Integer docId = dbpedia.getNames().getReverse(entity.documentName);
            if(docId == null) {
              continue;
            }
            IntList terms = new IntList(dbpedia.getCorpus().getDocument(docId));
            System.out.println("\t\t"+ StrUtil.join(dbpedia.translateToTerms(terms)));
          }
          trecrun.println(entity.toTRECformat(qid, "jfoley-topdocs-wpmi"));
        }
      }
    }
  }
}
