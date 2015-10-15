package edu.umass.cs.jfoley.coop.querying.eval;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * @author jfoley
 */
public class DocumentResult<T> implements Comparable<DocumentResult<?>> {
  public final int document;
  public final T value;

  public DocumentResult(int document, @Nonnull T value) {
    this.document = document;
    this.value = Objects.requireNonNull(value);
  }

  @Override
  public int compareTo(@Nonnull DocumentResult<?> o) {
    return Integer.compare(document, o.document);
  }
}
