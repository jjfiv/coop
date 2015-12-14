package edu.umass.cs.jfoley.coop.experiments.deepscore;

import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.TopKHeap;
import ciir.jfoley.chai.collections.list.DoubleList;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.hash.TIntHashSet;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.core.retrieval.Results;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.core.util.WordLists;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StringPooler;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author jfoley
 */
public class RobustLCE {
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);
    String index = "/mnt/scratch3/jfoley/robust.ints";
    IntCoopIndex target = new IntCoopIndex(Directory.Read(argp.get("target", index)));
    LocalRetrieval ret = new LocalRetrieval("/mnt/scratch3/jfoley/robust.galago");

    Map<String, String> qidToDesc = new TreeMap<>();
    for (String line : LinesIterable.fromFile("/home/jfoley/code/queries/robust04/rob04.titles.tsv").slurp()) {
      String[] data = line.split("\t");
      qidToDesc.put(data[0].trim(), data[1].trim());
    }

    TIntHashSet stopwords = new TIntHashSet();
    {
      List<String> surstopwords = new ArrayList<>(WordLists.getWordListOrDie("inquery"));
      IntList sw = target.getTermVocabulary().translateReverse(surstopwords, -1);
      for (int i = 0; i < sw.size(); i++) {
        stopwords.add(sw.getQuick(i));
      }
      System.err.println(surstopwords);
    }
    System.err.println(stopwords);

    final int numTerms = 100;
    //int numDocuments = argp.get("numDocs", 25);
    double expansionMu = argp.get("expansionMu", 1000);

   // oracle values for SDM from Huston and Croft "Parameters Learned in the Computation of Retrieval Models using Term Dependencies"
    double mu = argp.get("mu", 885);
    double uniw = argp.get("uniw", 0.86);
    double odw = argp.get("uniw", 0.0786);
    double uww = argp.get("uniw", 0.0612);

    TagTokenizer tok = new TagTokenizer();
    StringPooler.disable();

    Map<Pair<Integer,Integer>, PrintWriter> trecruns = new HashMap<>();

    qidToDesc.entrySet().parallelStream().forEach((kv -> {
      try {
        String qid = kv.getKey();
        String query = kv.getValue();
        System.err.println(qid+" "+query);
        Node qNode = new Node("sdm");
        synchronized (tok) {
          List<String> tokens = tok.tokenize(query).terms;
          qNode.addTerms(tokens);
        }

        Parameters qp = Parameters.create();
        qp.put("requested", 10000);
        // general mu param
        qp.put("mu", mu);
        // sdm params
        qp.put("uniw", uniw);
        qp.put("odw", odw);
        qp.put("uww", uww);
        Node xNode = ret.transformQuery(qNode, qp);

        Results res = ret.executeQuery(xNode, qp);
        System.err.println("\tCollected top SDM docs.");

        List<String> workingSet = new ArrayList<>(10000);
        for (int i = 0; i < res.scoredDocuments.size(); i++) {
          workingSet.add(res.scoredDocuments.get(i).getName());
        }

        Arrays.asList(10,25,50,100).parallelStream().forEach(numDocuments -> {
          try {
            List<String> names = new ArrayList<>(numDocuments);
            DoubleList sdmScores = new DoubleList(numDocuments);

            for (ScoredDocument scoredDocument : res.scoredDocuments) {
              if(scoredDocument.getRank() > numDocuments) continue;
              names.add(scoredDocument.getName());
              sdmScores.add(scoredDocument.getScore());
            }
            IntList ids = target.getNames().translateReverse(names, -1);

            IntList lengths = new IntList();
            List<TIntIntHashMap> docVs = new ArrayList<>();
            TIntIntHashMap totalTermsInTop = new TIntIntHashMap();
            for (int id : ids) {
              if(id < 0) continue;

              int[] data = target.getCorpus().getDocument(id);

              TIntIntHashMap bagOfWords = new TIntIntHashMap();
              for (int term : data) {
                if(stopwords.contains(term)) continue;
                bagOfWords.adjustOrPutValue(term, 1, 1);
                totalTermsInTop.adjustOrPutValue(term, 1, 1);
              }
              lengths.add(data.length);
              docVs.add(bagOfWords);
              assert(bagOfWords.getNoEntryValue() == 0);
            }
            System.err.println("\tPulled and tabulated the top "+docVs.size()+" docs for LCE.");

            IntList exciting = new IntList(totalTermsInTop.size());
            totalTermsInTop.forEachEntry((term, freq) -> {
              if(freq > 1) {
                exciting.push(term);
              }
              return true;
            });
            System.err.println("\tTrying "+exciting.size()+" features for LCE!");
            TopKHeap<TopKHeap.Weighted<Integer>> scoredTerms = new TopKHeap<>(numTerms*2);

            for (int termIndex = 0; termIndex < exciting.size(); termIndex++) {
              int tid = exciting.getQuick(termIndex);
              long cf = target.getPositionsIndex().collectionFrequency(tid);
              double clen = target.getPositionsIndex().getCollectionLength();
              double termDiscount = -Math.log(cf / clen);
              double bgmu = expansionMu * (cf / clen);

              double scoreSum = 0;
              for (int docIndex = 0; docIndex < docVs.size(); docIndex++) {
                double sdmScore = sdmScores.getQuick(docIndex);
                double lenmu = lengths.getQuick(docIndex) + expansionMu;
                double count = docVs.get(docIndex).get(tid);
                double unigramScore = (count + bgmu) / lenmu;
                scoreSum += sdmScore + Math.log(unigramScore) + termDiscount;
              }
              scoredTerms.offer(new TopKHeap.Weighted<>(scoreSum, tid));
            }

            List<TopKHeap.Weighted<Integer>> sorted = scoredTerms.getSorted();

            Arrays.asList(60,70,80,90,100,150,200).parallelStream().forEach(depth -> {
              System.err.println("\t"+qid+" Eval depth: " + depth+" numDocs: "+numDocuments+" numCandidates: "+exciting.size());
              Node full = new Node("combine");
              full.getNodeParameters().set("0", 0.25);
              full.getNodeParameters().set("1", 0.75);
              Node combine = new Node("combine");
              full.addChild(qNode.clone());
              full.addChild(combine);
              for (int i = 0; i < sorted.size(); i++) {
                if (i + 1 > depth) break; // 0+1 > 1 NO, 1+1 > 1 YES
                TopKHeap.Weighted<Integer> wt = sorted.get(i);
                String surface = null;
                try {
                  surface = target.getTermVocabulary().getForward(wt.object);
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
                if (surface != null) {
                  //System.err.println("\t\t"+surface);
                  combine.addChild(Node.Text(surface));
                  combine.getNodeParameters().set(Integer.toString(i), wt.getValue());
                }
              }

              Parameters fqp = Parameters.create();
              fqp.put("working", workingSet);
              fqp.put("mu", mu);
              // sdm params
              fqp.put("uniw", uniw);
              fqp.put("odw", odw);
              fqp.put("uww", uww);
              Results fres = ret.transformAndExecuteQuery(full, fqp);
              PrintWriter pw = trecruns.computeIfAbsent(Pair.of(numDocuments, depth), missing -> {
                try {
                  return IO.openPrintWriter("robust.llce.n"+numDocuments+".x" + depth + ".trecrun");
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
              synchronized (pw) {
                fres.printToTrecrun(pw, qid, "lce");
              }
            });

          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });

      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }));

    for (PrintWriter printWriter : trecruns.values()) {
      printWriter.close();
    }
  }
}
