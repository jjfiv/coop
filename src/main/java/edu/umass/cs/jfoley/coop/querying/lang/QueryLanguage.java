package edu.umass.cs.jfoley.coop.querying.lang;

import ciir.jfoley.chai.collections.list.AChaiList;
import edu.umass.cs.jfoley.coop.index.DocumentLabelIndex;
import org.lemurproject.galago.utility.Parameters;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class QueryLanguage {
  /**
   * This interface marks any Query object.
   */
  interface QueryNode {
    String getOperator();
    default boolean isLeaf() { return size() == 0; }
    int size();
    QueryNode get(int index);
    Parameters toJSON();
  }

  abstract static class LeafNode implements QueryNode {
    @Override
    public int size() { return 0; }
    @Override
    public QueryNode get(int index) { throw new IndexOutOfBoundsException(); }
  }

  /**
   * Useful as a marker on a scorable element.
   */
  interface BooleanNode extends QueryNode {

  }

  /**
   * A node that represents a count.
   * If the count is nonzero, it can be interpreted as true.
   */
  interface CountNode extends BooleanNode { }

  /**
   * By convention, a Positions node is a span starting at position with a length of 1.
   */
  interface PositionsNode extends SpanNode { }

  /**
   * This models fields, sentences, snippets, tags, phrase matches, etc.
   * Galago/Waltz sometimes refer to Spans as Extents.
   */
  interface SpanNode extends CountNode {}

  interface ScoreNode extends QueryNode {}

  public static class Term extends LeafNode implements CountNode {
    public final String term;

    public Term(String term) {
      this.term = term;
    }

    @Override
    public String getOperator() {
      return "term";
    }

    @Override
    public Parameters toJSON() {
      return Parameters.parseArray("operator", getOperator(), "term", term);
    }
  }

  public static class Phrase extends AndNode implements SpanNode {
    public Phrase(List<Term> terms) {
      super(terms);
    }

    @Override
    public String getOperator() {
      return "phrase";
    }
  }

  public static class DocumentLabel extends LeafNode implements BooleanNode {
    public final DocumentLabelIndex.NamespacedLabel label;

    public DocumentLabel(DocumentLabelIndex.NamespacedLabel label) {
      this.label = label;
    }

    @Override
    public String getOperator() {
      return "document-label";
    }

    @Override
    public Parameters toJSON() {
      return Parameters.parseArray("operator", getOperator(), "label", label);
    }
  }

  public static abstract class NodeWithChildren extends AChaiList<QueryNode> implements QueryNode {
    protected final List<? extends QueryNode> children;

    public NodeWithChildren(@Nonnull List<? extends QueryNode> children) {
      this.children = children;
    }

    @Override
    @Nonnull
    public QueryNode get(int index) {
      return children.get(index);
    }

    @Override
    public int size() {
      return children.size();
    }

    @Override
    public Parameters toJSON() {
      // recurse on children:
      List<Parameters> cjson = new ArrayList<>(size());
      for(QueryNode child : this) {
        cjson.add(child.toJSON());
      }

      Parameters output = Parameters.create();
      output.put("operator", getOperator());
      output.put("children", cjson);
      return output;
    }
  }

  public static class AndNode<T extends QueryNode> extends NodeWithChildren {
    public AndNode(List<? extends QueryNode> children) {
      super(children);
    }

    @Override
    public String getOperator() {
      return "and";
    }
  }
  public static class OrNode<T extends QueryNode> extends NodeWithChildren {
    public OrNode(List<? extends QueryNode> children) {
      super(children);
    }

    @Override
    public String getOperator() {
      return "or";
    }
  }


}
