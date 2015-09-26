package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.Comparing;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.random.ReservoirSampler;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.sys.PostingsConfig;
import edu.umass.cs.ciir.waltz.sys.counts.CountMetadata;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import edu.umass.cs.jfoley.coop.front.QueryEngine;
import edu.umass.cs.jfoley.coop.front.TermPositionsIndex;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.util.WordLists;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StringPooler;
import org.lemurproject.galago.utility.tools.Arguments;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class IndexedSDMQuery {

  public static class SDMPartReaders {
    public static PostingsConfig<Integer, Integer> countIndexCfg = new PostingsConfig<>(
        FixedSize.ints, VarUInt.instance, Comparing.defaultComparator(), new CountMetadata()
    );
    public static PostingsConfig<Long, Integer> bigramIndexCfg = new PostingsConfig<>(
        FixedSize.longs, VarUInt.instance, Comparing.defaultComparator(), new CountMetadata()
    );

    final Directory input;
    final IOMap<Integer, PostingMover<Integer>> unigrams;
    final IOMap<Long, PostingMover<Integer>> bigrams;
    final IOMap<Long, PostingMover<Integer>> ubigrams;

    public SDMPartReaders(Directory input) throws IOException {
      assert (exists(input));
      this.input = input;

      unigrams = countIndexCfg.openReader(input, "unigram");
      bigrams = bigramIndexCfg.openReader(input, "bigram");
      ubigrams = bigramIndexCfg.openReader(input, "ubigram");
    }

    public static boolean exists(Directory input) {
      return input.child("bigram.keys").exists() &&
          input.child("ubigram.keys").exists() &&
          input.child("unigram.keys").exists();
    }

    @Nullable
    public PostingMover<Integer> getUnigram(int id) throws IOException {
      return unigrams.get(id);
    }

    @Nullable
    public PostingMover<Integer> getBigram(int lhs, int rhs) throws IOException {
      return bigrams.get(IntCorpusSDMIndex.Bigram.toLong(lhs, rhs));
    }

    @Nullable
    public PostingMover<Integer> getUBigram(int lhs, int rhs) throws IOException {
      if (lhs == rhs) {
        return getUnigram(lhs);
      }

      long key;
      if (lhs < rhs) {
        key = IntCorpusSDMIndex.Bigram.toLong(lhs, rhs);
      } else {
        key = IntCorpusSDMIndex.Bigram.toLong(rhs, lhs);
      }
      return ubigrams.get(key);
    }
  }

  public static void main(String[] args) throws IOException {
    Parameters argp = Arguments.parse(args);
    final String queries = argp.get("queries", "db.saves/shutdown.json.gz");
    Parameters qdb = Parameters.parseFile(queries);

    System.err.println("Loaded "+qdb.getList("facts").size()+" facts!");

    IntCoopIndex dbpedia = new IntCoopIndex(Directory.Read("dbpedia.ints"));
    System.err.println("Loaded dpbedia.");
    assert (SDMPartReaders.exists(dbpedia.baseDir));
    //SDMPartReaders sdmR = new SDMPartReaders(dbpedia.baseDir);
    System.err.println("Loaded SDMPartReaders.");
    TermPositionsIndex sdmR = dbpedia.getPositionsIndex("lemmas");

    HashSet<String> stopwords = new HashSet<>(Objects.requireNonNull(WordLists.getWordList("inquery")));
    IntList stopInts = new IntList(dbpedia.translateFromTerms(new ArrayList<>(stopwords)));
    System.err.println("Loaded StopWords.");

    ReservoirSampler<YFQServer.YearFact> newFacts = new ReservoirSampler<>(new Random(13), 10);
    for (Parameters yfjson : qdb.getList("facts", Parameters.class)) {
      YFQServer.YearFact yf = YFQServer.YearFact.parseJSON(yfjson);
      newFacts.add(yf);
    }
    System.err.println("Parsed Facts.");
    TagTokenizer tok = new TagTokenizer();
    StringPooler.disable();

    for (YFQServer.YearFact newFact : newFacts) {
      List<String> terms = tok.tokenize(newFact.getHtml()).terms;
      System.err.println("Q: "+newFact.getId()+ " "+ terms);
      IntList ids = dbpedia.translateFromTerms(terms);

      List<QueryEngine.QCNode<Double>> uF = new ArrayList<>();
      List<QueryEngine.QCNode<Double>> odF = new ArrayList<>();
      List<QueryEngine.QCNode<Double>> uwF = new ArrayList<>();

      int QN = ids.size();
      for (int i = 0; i < QN; i++) {
        int lhs = ids.getQuick(i);
        if(lhs == -1) continue;
        // don't add unigram stopword features, only add them to bigrams/ubigrams
        if(!stopInts.containsInt(lhs)) {
          QueryEngine.QCNode<Integer> unigram = sdmR.getUnigram(lhs);
          assert(unigram != null);
          uF.add(new QueryEngine.LinearSmoothingNode(unigram));
        }
        if(i+1 < QN) {
          int rhs = ids.getQuick(i+1);
          if(rhs == -1) continue;
          QueryEngine.QCNode<Integer> bigram = sdmR.getBigram(lhs, rhs);
          QueryEngine.QCNode<Integer> ubigram = sdmR.getUBigram(lhs, rhs);
          if(bigram != null) {
            odF.add(new QueryEngine.LinearSmoothingNode(bigram));
          }
          if(ubigram != null) {
            uwF.add(new QueryEngine.LinearSmoothingNode(ubigram));
          }
        }
      }

      QueryEngine.QCNode<Double> sdmScorer = new QueryEngine.CombineNode(Arrays.asList(
          new QueryEngine.CombineNode(uF),
          new QueryEngine.CombineNode(odF),
          new QueryEngine.CombineNode(uwF)
      ));

      System.out.println("terms: "+ids);
      System.out.println("sdmScorer: "+sdmScorer.toReprString());

    }

  }
}
