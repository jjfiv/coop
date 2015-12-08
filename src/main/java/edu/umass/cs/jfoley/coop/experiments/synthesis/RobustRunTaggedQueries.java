package edu.umass.cs.jfoley.coop.experiments.synthesis;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.sys.counts.CountMetadata;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.front.QueryEngine;
import edu.umass.cs.jfoley.coop.phrases.PhraseDetector;
import gnu.trove.map.hash.TIntIntHashMap;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author jfoley
 */
public class RobustRunTaggedQueries {

  public static class TaggerIndex implements QueryEngine.QueryEvaluationContext {
    final IOMap<Integer, PostingMover<Integer>> entityCounts;
    private final long collectionLength;
    final TIntIntHashMap lengths;

    public TaggerIndex(IOMap<Integer, PostingMover<Integer>> entityCounts) throws IOException {
      this.entityCounts = entityCounts;
      PostingMover<Integer> lengthsMover = this.entityCounts.get(-1);
      assert lengthsMover != null;
      CountMetadata cm = (CountMetadata) lengthsMover.getMetadata();
      this.collectionLength = cm.totalCount;
      lengths = new TIntIntHashMap(cm.totalDocs);
      lengthsMover.execute((doc) -> {
        int length = lengthsMover.getPosting(doc);
        lengths.put(doc, length);
      });
    }


    @Override
    public int getLength(int document) {
      return lengths.get(document);
    }

    @Override
    public double getCollectionLength() {
      return collectionLength;
    }

    @Override
    public QueryEngine.QCNode<Integer> getUnigram(int lhs) throws IOException {
      PostingMover<Integer> mover = entityCounts.get(lhs);
      if(mover == null) return null;
      return new QueryEngine.IndexedCountsNode(mover);
    }

    @Override public QueryEngine.QCNode<Integer> getBigram(int lhs, int rhs) throws IOException { return null; }
    @Override public QueryEngine.QCNode<Integer> getUBigram(int lhs, int rhs) throws IOException { return null; }
  }
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);

    Map<String, String> queryByQid = new HashMap<>();
    for (String qline : LinesIterable.fromFile("rob04.titles.tsv").slurp()) {
      String qcol[] = qline.split("\t");
      String qid = qcol[0];
      String query = qcol[1];
      queryByQid.put(qid, query);
    }

    int requested = argp.get("requested", 1000);

    String index = "/mnt/scratch3/jfoley/robust.ints";
    IntCoopIndex dbpedia = new IntCoopIndex(Directory.Read(argp.get("dbpedia", "/mnt/scratch3/jfoley/dbpedia.ints")));
    IntCoopIndex target = new IntCoopIndex(Directory.Read(argp.get("target", index)));
    TaggerIndex taggerIndex = new TaggerIndex(target.entCounts);

    PhraseDetector tagger = dbpedia.loadPhraseDetector(10, target);
    TagTokenizer tok = new TagTokenizer();

    try (PrintWriter trecrun = IO.openPrintWriter("latest.trecrun")) {
      for (Map.Entry<String, String> kv : queryByQid.entrySet()) {
        String qid = kv.getKey();
        String qtext = kv.getValue();

        IntList qids = target.getTermVocabulary().translateReverse(tok.tokenize(qtext).terms, -1);
        System.out.println(qid + ": " + qtext + " " + qids);

        IntList entityIds = new IntList();
        tagger.match(qids.asArray(), (phraseId, position, size) -> entityIds.add(phraseId));

        List<QueryEngine.QCNode<Double>> pnodes = new ArrayList<>();
        for (int phraseId : entityIds) {
          try {
            String terms = StrUtil.join(target.getTermVocabulary().translateForward(target.getEntitiesIndex().getPhraseVocab().getForward(phraseId), null));
            QueryEngine.QCNode<Integer> counts = taggerIndex.getUnigram(phraseId);
            if (counts == null) {
              System.out.println("\tMISS: " + terms);
              continue;
            }
            System.out.println("\t" + terms);
            pnodes.add(new QueryEngine.LinearSmoothingNode(counts));
          } catch (Throwable err) {
            continue;
          }
        }
        if(pnodes.isEmpty()) {
          continue;
        }
        QueryEngine.QCNode<Double> ql = new QueryEngine.CombineNode(pnodes);
        Mover mover = QueryEngine.createMover(ql);
        ql.setup(taggerIndex);

        TopKHeap<ScoredDocument> bestDocuments = new TopKHeap<>(requested);
        for(mover.start(); !mover.isDone(); mover.next()) {
          int id = mover.currentKey();
          double score = Objects.requireNonNull(ql.calculate(taggerIndex, id));
          bestDocuments.offer(new ScoredDocument(id, score));
        }

        for (ScoredDocument sdoc : bestDocuments) {
          sdoc.documentName = target.getNames().getForward(IntMath.fromLong(sdoc.document));
          trecrun.println(sdoc.toTRECformat(qid, "erank"));
        }

      }
    }
  }
}
