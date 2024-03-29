package eu.europeana.metis.mediaprocessing;

import eu.europeana.corelib.definitions.jibx.RDF;
import eu.europeana.metis.mediaprocessing.exception.RdfConverterException;
import org.jibx.runtime.BindingDirectory;
import org.jibx.runtime.IBindingFactory;
import org.jibx.runtime.JiBXException;

/**
 * This class maintains an instance of {@link IBindingFactory} which it can make available upon
 * request.
 */
final class RdfBindingFactoryProvider {

  private static IBindingFactory rdfBindingFactory;

  /**
   * Constructor - this class should not be initialized.
   */
  private RdfBindingFactoryProvider() {
  }

  /**
   * @return A binding factory.
   * @throws RdfConverterException In case the binding factory could not be created.
   */
  static IBindingFactory getBindingFactory() throws RdfConverterException {
    synchronized (RdfBindingFactoryProvider.class) {
      if (rdfBindingFactory == null) {
        try {
          rdfBindingFactory = BindingDirectory.getFactory(RDF.class);
        } catch (JiBXException e) {
          throw new RdfConverterException("Unable to create binding factory", e);
        }
      }
      return rdfBindingFactory;
    }
  }
}
