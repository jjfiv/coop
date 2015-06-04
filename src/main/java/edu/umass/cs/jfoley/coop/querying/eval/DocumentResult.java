package edu.umass.cs.jfoley.coop.querying.eval;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * @author jfoley
 */
public class DocumentResult<T> {
  public final int document;
  public final T value;

  public DocumentResult(int document, @Nonnull T value) {
    this.document = document;
    this.value = Objects.requireNonNull(value);
  }
}
