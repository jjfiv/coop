package edu.umass.cs.jfoley.coop.conll.classifier;

import ciir.jfoley.chai.Timing;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.errors.FatalError;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.lang.DoubleFns;
import ciir.jfoley.chai.random.Sample;
import edu.umass.cs.jfoley.coop.conll.SentenceIndexedToken;
import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import gnu.trove.map.hash.TIntFloatHashMap;

import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class RandomlyInitClassifier  {
  public interface FeatureVector extends Iterable<Pair<Integer, Float>> {
    Iterator<Pair<Integer, Float>> iterator();
  }
  public static class SparseFloatFeatures implements FeatureVector {
    TIntFloatHashMap features;

    @Override
    public Iterator<Pair<Integer, Float>> iterator() {
      final int[] keys = features.keys();
      return new Iterator<Pair<Integer, Float>>() {
        int pos = 0;
        @Override
        public boolean hasNext() {
          return pos < keys.length;
        }

        @Override
        public Pair<Integer, Float> next() {
          float val = features.get(keys[pos]);
          return Pair.of(pos++, val);
        }
      };
    }
  }
  public static class SparseBooleanFeatures implements FeatureVector {
    final IntList active;
    public SparseBooleanFeatures(IntList active) {
      this.active = active;
    }

    @Override
    public Iterator<Pair<Integer, Float>> iterator() {
      return IterableFns.map(active, (idx) -> Pair.of(idx, 1.0f)).iterator();
    }
  }
  public static abstract class Classifier {
    protected final int numFeatures;
    public Classifier(int numFeatures) {
      this.numFeatures = numFeatures;
    }

    abstract double train(List<? extends FeatureVector> pos, List<? extends FeatureVector> neg);
    abstract boolean predict(FeatureVector fv);
  }
  public static class PerceptronClassifier extends Classifier {
    private float[] w;
    private int numIterations = 1000;

    public PerceptronClassifier(int numFeatures) {
      super(numFeatures);
      reset();
    }

    public void reset() {
      w = new float[numFeatures+1];
    }

    @Override
    public double train(List<? extends FeatureVector> pos, List<? extends FeatureVector> neg) {
      List<Pair<Integer, FeatureVector>> data = new ArrayList<>(pos.size()+neg.size());
      for (FeatureVector fv : pos) {
        data.add(Pair.of(1, fv));
      }
      for (FeatureVector fv : neg) {
        data.add(Pair.of(-1, fv));
      }

      Collections.shuffle(data);

      int total = data.size();
      int correct = 0;
      boolean changed = false;
      int numIters;
      for (numIters = 0; numIters < numIterations; numIters++) {
        correct = 0;
        for (Pair<Integer, FeatureVector> labeledData : data) {
          int label = labeledData.left;
          FeatureVector fv = labeledData.right;

          int predicted = predict(fv) ? 1 : -1;

          if(predicted != label) {
            for (Pair<Integer, Float> kv : fv) {
              w[kv.left] += label * kv.right;
            }
            w[w.length-1] += label;
            changed = true;
          } else {
            correct++;
          }
        } // data-loop

        // skip iterations if we're somehow perfect:
        if(!changed && correct == total) break;
      } // iter-loop

      System.out.println("Number of iterations: "+numIters);
      return (double) correct / (double) total;
    }

    @Override
    public boolean predict(FeatureVector fv) {
      double dotP = 0.0;
      for (Pair<Integer, Float> kv : fv) {
        dotP += w[kv.left] * kv.right;
      }
      dotP += w[w.length - 1]; // * 1.0 if we extended all the features.

      return (dotP >= 0);
    }

    public TIntFloatHashMap getSparseFeatures() {
      TIntFloatHashMap output = new TIntFloatHashMap();
      for (int i = 0; i < w.length; i++) {
        if(!DoubleFns.equals(w[i], 0.0, 0.01)) {
          output.put(i, w[i]);
        }
      }
      return output;
    }

  }

  public static void main(String[] args) throws IOException {

    try (TermBasedIndexReader index = new TermBasedIndexReader(Directory.Read("./CoNLL03.eng.train.run.stoken.index"))) {

      IntList counts = new IntList(Arrays.asList(1,10,20,50,100,1000,2000));
      List<String> kinds = Arrays.asList("I-PER", "I-LOC", "I-ORG", "I-MISC");
      Random rand = new Random(13);

      for(String kind : kinds) {
        for (int count : counts) {

          // Prepare data
          List<Integer> positive = new IntList();
          List<Integer> negative = new IntList();

          while(positive.size() < count) {
            List<Integer> sentenceIds = Sample.randomIntegers(rand, 200, index.getSentenceCount());
            for (List<SentenceIndexedToken> tokens : index.pullSentences(sentenceIds)) {
              for (SentenceIndexedToken token : tokens) {
                Map<String, String> terms = token.getTerms();
                String ner = terms.get("true_ner");
                if (ner.equals(kind)) {
                  positive.add(token.tokenId);
                } else if(negative.size() < count*4) {
                  negative.add(token.tokenId);
                }
                if(positive.size() == count) break;
              }
              if(positive.size() == count) break;
            }
          }

          // Print info about prepared data:
          System.out.printf("%s\t%4d\t%5d\n", kind, positive.size(), negative.size());

          List<SparseBooleanFeatures> posF = new ArrayList<>();
          List<SparseBooleanFeatures> negF = new ArrayList<>();
          long pullPos = Timing.milliseconds(() -> {
            try {
              posF.addAll(index.pullFeatures(positive));
            } catch (IOException e) {
              throw new FatalError(e);
            }
          });
          long pullNeg = Timing.milliseconds(() -> {
            try {
              negF.addAll(index.pullFeatures(negative));
            } catch (IOException e) {
              throw new FatalError(e);
            }
          });
          System.out.println("Pull pos: " + pullPos + " pull neg: " + pullNeg);

          // train classifier:
          PerceptronClassifier classifier = new PerceptronClassifier(index.classifiers.featuresAboveThreshold.size());
          double accuracy = classifier.train(posF, negF);
          System.out.printf("Training Accuracy: %3.1f%%\n", 100.0 * accuracy);
          System.out.printf("Number of features: %d\n", classifier.getSparseFeatures().size());
          //System.out.printf("Features: %s\n", classifier.getSparseFeatures());

        }
      }


    }
  }
}
