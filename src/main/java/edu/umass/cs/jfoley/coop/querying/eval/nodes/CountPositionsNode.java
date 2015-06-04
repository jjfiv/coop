package edu.umass.cs.jfoley.coop.querying.eval.nodes;

import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.jfoley.coop.querying.eval.QueryEvalNode;

import javax.annotation.Nonnull;

/**
 * @author jfoley
 */
public class CountPositionsNode extends MappingQueryNode<PositionsList, Integer> {
  protected CountPositionsNode(QueryEvalNode<PositionsList> inner) {
    super(inner);
  }

  @Override
  public Integer map(@Nonnull PositionsList input) {
    return input.size();
  }
}
