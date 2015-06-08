package edu.umass.cs.jfoley.coop.bdaat;

import ciir.jfoley.chai.collections.chained.ChaiIterable;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.random.Sample;
import ciir.jfoley.chai.string.StrUtil;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;
import org.lemurproject.galago.core.index.KeyIterator;
import org.lemurproject.galago.core.index.disk.PositionIndexReader;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.core.retrieval.Results;
import org.lemurproject.galago.core.retrieval.processing.MaxScoreDocumentModel;
import org.lemurproject.galago.core.retrieval.processing.ProcessingModel;
import org.lemurproject.galago.core.retrieval.processing.RankedDocumentModel;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.utility.Parameters;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author jfoley.
 */
public class BDAATExperiment {
  public static void main(String[] args) throws Exception {
    Parameters argp = Parameters.parseArgs(args);
    Map<String, Class<? extends ProcessingModel>> classesUnderTest = new HashMap<>();
    classesUnderTest.put("daat", RankedDocumentModel.class);
    classesUnderTest.put("maxscore-daat", MaxScoreDocumentModel.class);
    classesUnderTest.put("bdaat", BDAATProcessingModel.class);

    File out = new File(argp.getString("output"));
    String model = argp.getString("model");
    LocalRetrieval retrieval = new LocalRetrieval(argp.getString("index"));

    String name = classesUnderTest.get(model).getName();

    int numQueries = argp.get("numQueries", 30);
    int numTermsPerQuery = argp.getInt("numTerms");

    Parameters qp = Parameters.create();
    qp.put("processingModel", name);

    PositionIndexReader positions = (PositionIndexReader) retrieval.getIndex().getIndexPart("postings");

    Iterable<String> galagoKeyIterable = () -> {
      try {
        final KeyIterator iter = positions.getIterator();

        return new Iterator<String>() {
          @Override
          public boolean hasNext() {
            return !iter.isDone();
          }

          @Override
          public String next() {
            if(iter.isDone()) throw new NoSuchElementException();
            try {
              String current = iter.getKeyString();
              iter.nextKey();
              return current;
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        };
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    };

    Random rand;
    if(argp.isLong("seed")) {
      rand = new Random(argp.getLong("seed"));
    } else {
      rand = new Random();
    }

    final int minTF = argp.get("minimumTF", 2000);

    List<String> meaningfulTerms = ListFns.repeatUntilAtLeast(ChaiIterable.create(galagoKeyIterable).filter(
        (term) -> {
          try {
            long documentCount = positions.getTermCounts(term).getStatistics().nodeDocumentCount;
            return documentCount > minTF;
          } catch (IOException e) {
            return false;
          }
        }
    ).intoList(), numQueries * numTermsPerQuery);

    List<String> randomTerms = Sample.byRandomWeight(meaningfulTerms, numQueries*numTermsPerQuery, rand);

    List<List<String>> queries = IterableFns.intoList(IterableFns.batches(randomTerms, numTermsPerQuery));
    assert(queries.size() == numQueries);

    List<Node> qlQueries = new ArrayList<>(numQueries);
    List<Parameters> queryParam = new ArrayList<>(numQueries);
    for (List<String> query : queries) {
      assert(query.size() == numTermsPerQuery);

      Node ql = new Node("combine");
      for (String term : query) {
        ql.addChild(Node.Text(term));
      }

      Parameters currentQP = qp.clone();
      qlQueries.add(retrieval.transformQuery(ql, currentQP));
      queryParam.add(currentQP);
    }

    TLongArrayList times = new TLongArrayList(numQueries);
    TIntArrayList sizes = new TIntArrayList(numQueries);
    for (int i = 0; i < qlQueries.size(); i++) {
      Node qlQuery = qlQueries.get(i);
      long start = System.nanoTime();
      Results results = retrieval.executeQuery(qlQuery, queryParam.get(i));
      sizes.add(results.scoredDocuments.size());
      long end = System.nanoTime();
      times.add(end-start);
    }

    Parameters resultsAsJSON = Parameters.create();
    resultsAsJSON.put("queries", ChaiIterable.create(queries).map((ts) -> StrUtil.join(ts, " ")).intoList());

    List<Long> timesNS = new ArrayList<>(times.size());
    times.forEach(timesNS::add);
    List<Integer> resultSizes = new ArrayList<>(sizes.size());
    sizes.forEach(resultSizes::add);
    resultsAsJSON.put("model", model);
    resultsAsJSON.put("times", timesNS);
    resultsAsJSON.put("sizes", resultSizes);

    IO.spit(resultsAsJSON.toString(), out);
  }
}
