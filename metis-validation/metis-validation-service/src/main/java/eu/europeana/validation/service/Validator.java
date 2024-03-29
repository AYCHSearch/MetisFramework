package eu.europeana.validation.service;

import eu.europeana.validation.model.Schema;
import eu.europeana.validation.model.ValidationResult;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * EDM Validator class Created by gmamakis on 18-12-15.
 */
public class Validator implements Callable<ValidationResult> {

  private static final String NODE_ID_ATTR = "nodeId";
  private static final Logger LOGGER = LoggerFactory.getLogger(Validator.class);
  private static ConcurrentMap<String, Templates> templatesCache;

  private final String schema;
  private final String rootFileLocation;
  private final String schematronFileLocation;
  private final String document;
  private final SchemaProvider schemaProvider;
  private final ClasspathResourceResolver resolver;

  static {
    templatesCache = new ConcurrentHashMap<>();
  }


  /**
   * Constructor specifying the schema to validate against and the document
   *
   * @param schema schema that will be used for validation
   * @param rootFileLocation location of the schema root file
   * @param schematronFileLocation location of the schematron file
   * @param document document that will be validated
   * @param schemaProvider the class that provides the schemas. Make sure it is initialized with
   * safe schema location paths.
   * @param resolver the resolver used for parsing split xsds
   */
  public Validator(String schema, String rootFileLocation, String schematronFileLocation,
      String document, SchemaProvider schemaProvider, ClasspathResourceResolver resolver) {
    this.schema = schema;
    this.rootFileLocation = rootFileLocation;
    this.schematronFileLocation = schematronFileLocation;
    this.document = document;
    this.schemaProvider = schemaProvider;
    this.resolver = resolver;
  }

  /**
   * Get schema object specified with its name
   *
   * @param schemaName name of the schema
   * @return the schema object
   */
  private Schema getSchemaByName(String schemaName) throws SchemaProviderException {
    Schema schemaObject;
    if (schemaProvider.isPredefined(schemaName)) {
      schemaObject = schemaProvider.getSchema(schemaName);
    } else {
      if (rootFileLocation == null) {
        throw new SchemaProviderException("Missing root file location for custom schema");
      } else {
        schemaObject = schemaProvider
            .getSchema(schemaName, rootFileLocation, schematronFileLocation);
      }
    }
    if (schemaObject == null) {
      throw new SchemaProviderException("Could not find specified schema does not exist");
    }
    return schemaObject;
  }

  /**
   * Validate method using JAXP
   *
   * @return The outcome of the Validation
   */
  private ValidationResult validate() {
    LOGGER.debug("Validation started");
    InputSource source = new InputSource();
    source.setByteStream(new ByteArrayInputStream(document.getBytes(StandardCharsets.UTF_8)));
    try {
      Schema savedSchema = getSchemaByName(schema);

      resolver.setPrefix(StringUtils.substringBeforeLast(savedSchema.getPath(), File.separator));

      Document doc = EDMParser.getInstance().getEdmParser().parse(source);
      EDMParser.getInstance().getEdmValidator(savedSchema.getPath(), resolver)
          .validate(new DOMSource(doc));
      if (StringUtils.isNotEmpty(savedSchema.getSchematronPath())) {
        Transformer transformer = getTransformer(savedSchema);

        DOMResult result = new DOMResult();
        transformer.transform(new DOMSource(doc), result);
        NodeList nresults = result.getNode().getFirstChild().getChildNodes();
        final ValidationResult errorResult = checkNodeListForErrors(nresults);
        if (errorResult != null) {
          return errorResult;
        }
      }
    } catch (IOException | SchemaProviderException | SAXException | TransformerException e) {
      return constructValidationError(document, e);
    }
    LOGGER.debug("Validation ended");
    return constructOk();
  }

  private ValidationResult checkNodeListForErrors(NodeList nresults) {
    for (int i = 0; i < nresults.getLength(); i++) {
      Node nresult = nresults.item(i);
      if ("failed-assert".equals(nresult.getLocalName())) {
        String nodeId = nresult.getAttributes().getNamedItem(NODE_ID_ATTR) == null ? null
            : nresult.getAttributes().getNamedItem(NODE_ID_ATTR).getTextContent();
        return constructValidationError(document,
            "Schematron error: " + nresult.getTextContent().trim(), nodeId);
      }
    }
    return null;
  }

  private Transformer getTransformer(Schema schema)
      throws IOException, TransformerConfigurationException {
    StringReader reader;
    String schematronPath = schema.getSchematronPath();

    if (!templatesCache.containsKey(schematronPath)) {
      reader = new StringReader(
          FileUtils.readFileToString(new File(schematronPath), StandardCharsets.UTF_8.name()));
      final TransformerFactory transformerFactory = TransformerFactory.newInstance();
      transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Templates template = TransformerFactory.newInstance()
          .newTemplates(new StreamSource(reader));
      templatesCache.put(schematronPath, template);
    }

    return templatesCache.get(schematronPath).newTransformer();
  }

  private ValidationResult constructValidationError(String document, Exception e) {
    ValidationResult res = new ValidationResult();
    res.setMessage(e.getMessage());
    res.setRecordId(getRecordId(document));
    if (StringUtils.isEmpty(res.getRecordId())) {
      res.setRecordId("Missing record identifier for EDM record");
    }

    res.setSuccess(false);
    return res;
  }

  private ValidationResult constructValidationError(String document, String message,
      String nodeId) {
    ValidationResult res = new ValidationResult();
    res.setMessage(message);
    res.setRecordId(getRecordId(document));
    if (StringUtils.isEmpty(res.getRecordId())) {
      res.setRecordId("Missing record identifier for EDM record");
    }
    if (nodeId == null) {
      res.setNodeId("Missing node identifier");
    } else {
      res.setNodeId(nodeId);
    }

    res.setSuccess(false);
    return res;
  }

  private String getRecordId(String document) {
    Pattern pattern = Pattern.compile("ProvidedCHO\\s+rdf:about\\s?=\\s?\"(.+)\"\\s?>");
    Matcher matcher = pattern.matcher(document);
    if (matcher.find()) {
      return matcher.group(1);
    } else {
      return null;
    }
  }

  private ValidationResult constructOk() {
    ValidationResult res = new ValidationResult();

    res.setSuccess(true);
    return res;
  }

  @Override
  public ValidationResult call() {
    return validate();
  }

}


