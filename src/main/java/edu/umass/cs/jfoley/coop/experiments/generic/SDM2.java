// BSD License (http://lemurproject.org/galago-license)
package edu.umass.cs.jfoley.coop.experiments.generic;

import org.lemurproject.galago.core.retrieval.Retrieval;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.core.retrieval.query.NodeParameters;
import org.lemurproject.galago.core.retrieval.traversal.Traversal;
import org.lemurproject.galago.core.util.WordLists;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Transforms a #sdm operator into a full expansion of the sequential dependence
 * model. That means:
 *
 * #seqdep( #text:term1() #text:term2() ... termk ) -->
 *
 * #weight ( 0.8 #combine ( term1 term2 ... termk) 0.15 #combine ( #od(term1
 * term2) #od(term2 term3) ... #od(termk-1 termk) ) 0.05 #combine ( #uw8(term
 * term2) ... #uw8(termk-1 termk) ) )
 *
 *
 *
 * @author irmarc
 */
public class SDM2 extends Traversal {
  private final int windowLimitDefault;
  private final double unigramDefault;
  private final double orderedDefault;
  private final double unorderedDefault;

  private final String odOp;
  private final int odWidth;
  private final String uwOp;
  private final int uwWidth;

  private final boolean stopUnigramsDefault;
  private final String defaultWordList;

  public SDM2(Retrieval retrieval) throws IOException {
    Parameters parameters = retrieval.getGlobalParameters();
    unigramDefault = parameters.get("uniw", 0.8);
    orderedDefault = parameters.get("odw", 0.15);
    unorderedDefault = parameters.get("uww", 0.05);
    windowLimitDefault = (int) parameters.get("windowLimit", 2);

    // drop unigrams if they're stopwords
    stopUnigramsDefault = parameters.get("sdm.stopUnigrams", false);
    defaultWordList = parameters.get("stopwordlist", "inquery");

    odOp = parameters.get("sdm.od.op", "ordered");
    odWidth = parameters.get("sdm.od.width", 1);

    uwOp = parameters.get("sdm.uw.op", "unordered");
    uwWidth = parameters.get("sdm.uw.width", 4);
  }

  public Set<String> getStopwords(Parameters qp) throws IOException {
    return Objects.requireNonNull(
        WordLists.getWordList(qp.get("stopwordlist", defaultWordList)),
        "Couldn't find stopword list: " + qp.get("stopwordlist", defaultWordList));
  }

  @Override
  public void beforeNode(Node original, Parameters qp) throws Exception {
  }

  @Override
  public Node afterNode(Node original, Parameters qp) throws Exception {
    if (original.getOperator().equals("sdm2")) {

      boolean stopUnigrams = qp.get("stopUnigrams", this.stopUnigramsDefault);
      boolean stopUnordered = qp.get("stopUnordered", false);
      double unigramW = qp.get("uniw", unigramDefault);
      double orderedW = qp.get("odw", orderedDefault);
      double unorderedW = qp.get("uww", unorderedDefault);
      int windowLimit = qp.get("windowLimit", windowLimitDefault);
      Set<String> stopwords = getStopwords(qp);

      NodeParameters np = original.getNodeParameters();
      unigramW = np.get("uniw", unigramW);
      orderedW = np.get("odw", orderedW);
      unorderedW = np.get("uww", unorderedW);
      windowLimit = (int) np.get("windowLimit", windowLimit);

      List<Node> children = Node.cloneNodeList(original.getInternalNodes());
      // If there's only one child, pass it along no matter if it was a stopword or not.
      if (children.size() == 1) {
        return new Node("combine", children);
      }

      // collect non-stopword unigrams if needed
      List<Node> unigrams;
      if (stopUnigrams) {
        unigrams = new ArrayList<>();
        for (Node child : children) {
          if (child.getOperator().equals("text")) {
            String term = child.getDefaultParameter();
            if (stopwords.contains(term)) {
              //System.err.println("Skip unigram: " + term);
              continue;
            }
            unigrams.add(child.clone());
          }
        }
      } else {
        unigrams = children;
      }

      // formatting is ok - now reassemble
      // unigrams go as-is
      Node unigramNode = new Node("combine", unigrams);

      // ordered and unordered can go at the same time
      ArrayList<Node> ordered = new ArrayList<>();
      ArrayList<Node> unordered = new ArrayList<>();

      for (int n = 2; n <= windowLimit; n++) {
        for (int i = 0; i < (children.size() - n + 1); i++) {
          List<Node> seq = children.subList(i, i + n);

          String orderedOp = this.odOp;
          String unorderedOp = this.uwOp;

          if (n == 2) {
            orderedOp = "bigram";
            unorderedOp = "ubigram";
          }

          ordered.add(new Node(qp.get("sdm.od.op", orderedOp), new NodeParameters(qp.get("sdm.od.width", odWidth)), Node.cloneNodeList(seq)));

          boolean doThisUnordered = true;
          if (stopUnordered) {
            // if unordered contains stopword, skip this node:
            for (Node node : seq) {
              if (node.getOperator().equals("text")) {
                String term = node.getDefaultParameter();
                if (stopwords.contains(term)) {
                  doThisUnordered = false;
                  break;
                }
              }
            }
          }
          if (doThisUnordered) {
            unordered.add(new Node(qp.get("sdm.uw.op", unorderedOp), new NodeParameters(qp.get("sdm.uw.width", uwWidth) * seq.size()), Node.cloneNodeList(seq)));
          } else {
            //System.err.println("Skip unordered: " + seq);
          }
        }
      }

      Node orderedWindowNode = new Node("combine", ordered);
      Node unorderedWindowNode = new Node("combine", unordered);

      NodeParameters weights = new NodeParameters();
      ArrayList<Node> immediateChildren = new ArrayList<>();

      int index = 0;

      if (!unigramNode.isEmpty()) {
        // unigrams - 0.80
        weights.set(Integer.toString(index++), unigramW);
        immediateChildren.add(unigramNode);
      }

      // if deleting stopwords results in an empty query, put them back!
      if (!orderedWindowNode.isEmpty()) {
        // ordered
        weights.set(Integer.toString(index++), orderedW);
        immediateChildren.add(orderedWindowNode);
      }

      if (!unorderedWindowNode.isEmpty()) {
        // unordered
        weights.set(Integer.toString(index++), unorderedW);
        immediateChildren.add(unorderedWindowNode);
      }

      // Finally put them all inside a combine node w/ the weights
      return new Node("combine", weights, immediateChildren, original.getPosition());
    } else {
      return original;
    }
  }
}
