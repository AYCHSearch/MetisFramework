package eu.europeana.metis.core.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * @author Simon Tzanakis (Simon.Tzanakis@europeana.eu)
 * @since 2017-05-31
 */
@ResponseStatus(value= HttpStatus.NOT_FOUND, reason="No userWorkflowExecution found")
public class NoWorkflowExecutionFoundException extends Exception {
  private static final long serialVersionUID = -3332292346834265371L;
  public NoWorkflowExecutionFoundException(String message){
    super(message);
  }
}