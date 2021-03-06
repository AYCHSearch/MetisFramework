package eu.europeana.metis.mediaprocessing.extraction;

import eu.europeana.metis.mediaprocessing.exception.CommandExecutionException;
import eu.europeana.metis.mediaprocessing.exception.MediaExtractionException;
import eu.europeana.metis.mediaprocessing.exception.MediaProcessorException;
import eu.europeana.metis.mediaprocessing.model.Thumbnail;
import eu.europeana.metis.mediaprocessing.model.ThumbnailImpl;
import eu.europeana.metis.utils.MediaType;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class performs thumbnail generation for images and PDF files using ImageMagick.
 */
class ThumbnailGenerator {

  private static final Logger LOGGER = LoggerFactory.getLogger(ThumbnailGenerator.class);

  private static final String PDF_MIME_TYPE = "application/pdf";
  private static final String PNG_MIME_TYPE = "image/png";
  private static final String JPEG_MIME_TYPE = "image/jpeg";

  private enum ThumbnailKind {

    MEDIUM(200, "-MEDIUM"),
    LARGE(400, "-LARGE");

    protected final int size;
    protected final String suffix;

    ThumbnailKind(int size, String suffix) {
      this.size = size;
      this.suffix = suffix;
    }
  }

  private static final String COMMAND_RESULT_FORMAT = "%w\n%h\n%[colorspace]\n";
  private static final int COMMAND_RESULT_WIDTH_LINE = 0;
  private static final int COMMAND_RESULT_HEIGHT_LINE = 1;
  private static final int COMMAND_RESULT_COLORSPACE_LINE = 2;
  private static final int COMMAND_RESULT_COLORS_LINE = 3;
  private static final int COMMAND_RESULT_MAX_COLORS = 6;

  private static String globalMagickCommand;
  private static Path globalColormapFile;

  private final String magickCmd;
  private final String colormapFile;

  private final CommandExecutor commandExecutor;

  /**
   * Constructor. This is a wrapper for {@link ThumbnailGenerator#ThumbnailGenerator(CommandExecutor,
   * String, String)} where the properties are detected. It is advisable to use this constructor for
   * non-testing purposes.
   *
   * @param commandExecutor A command executor. The calling class is responsible for closing this
   * object.
   * @throws MediaProcessorException In case the properties could not be initialized.
   */
  ThumbnailGenerator(CommandExecutor commandExecutor) throws MediaProcessorException {
    this(commandExecutor, getGlobalImageMagickCommand(commandExecutor), initColorMap().toString());
  }

  /**
   * Constructor.
   *
   * @param commandExecutor A command executor.The calling class is responsible for closing this
   * object
   * @param magickCommand The magick command (how to trigger imageMagick).
   * @param colorMapFile The location of the color map file.
   */
  ThumbnailGenerator(CommandExecutor commandExecutor, String magickCommand, String colorMapFile) {
    this.commandExecutor = commandExecutor;
    this.magickCmd = magickCommand;
    this.colormapFile = colorMapFile;
  }

  private static Path initColorMap() throws MediaProcessorException {
    synchronized (ThumbnailGenerator.class) {

      // If we already found the color map, we don't need to do this
      if (globalColormapFile != null) {
        return globalColormapFile;
      }

      // Copy the color map file to the temp directory for use during this session.
      final Path colormapTempFile;
      try (InputStream colorMapInputStream =
          Thread.currentThread().getContextClassLoader().getResourceAsStream("colormap.png")) {
        colormapTempFile = Files.createTempFile("colormap", ".png");
        Files.copy(colorMapInputStream, colormapTempFile, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        LOGGER.warn("Could not load color map file: {}.", "colormap.png", e);
        throw new MediaProcessorException("Could not load color map file.", e);
      }

      // Ensure that the temporary file is removed (should not be needed).
      colormapTempFile.toFile().deleteOnExit();

      // So everything went well. We set this as the new color map file.
      globalColormapFile = colormapTempFile;
      return globalColormapFile;
    }
  }

  private static String getGlobalImageMagickCommand(CommandExecutor commandExecutor)
      throws MediaProcessorException {
    synchronized (ThumbnailGenerator.class) {
      if (globalMagickCommand == null) {
        globalMagickCommand = discoverImageMagickCommand(commandExecutor);
      }
      return globalMagickCommand;
    }
  }

  static String discoverImageMagickCommand(CommandExecutor commandExecutor)
      throws MediaProcessorException {

    // Try the 'magick' command for ImageMagick 7.
    try {
      final List<String> lines =
          commandExecutor.execute(Arrays.asList("magick", "-version"), true);
      if (String.join("", lines).startsWith("Version: ImageMagick 7")) {
        final String result = "magick";
        LOGGER.info("Found ImageMagic 7. Command: {}", result);
        return result;
      }
    } catch (CommandExecutionException e) {
      LOGGER.info("Could not find ImageMagick 7 because of: {}.", e.getMessage());
      LOGGER.debug("Could not find ImageMagick 7 due to following problem.", e);
    }

    // Try the 'convert' command for ImageMagick 6: find locations of the executable.
    final boolean isWindows =
        System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("win");
    List<String> paths;
    try {
      paths =
          commandExecutor.execute(Arrays.asList(isWindows ? "where" : "which", "convert"), true);
    } catch (CommandExecutionException e) {
      LOGGER.warn("Could not find ImageMagick 6 due to following problem.", e);
      paths = Collections.emptyList();
    }

    // Try the 'convert' command for ImageMagick 6: try executables to find the right one.
    for (String path : paths) {
      try {
        final List<String> lines =
            commandExecutor.execute(Arrays.asList(path, "-version"), true);
        if (String.join("", lines).startsWith("Version: ImageMagick 6")) {
          LOGGER.info("Found ImageMagic 6. Command: {}", path);
          return path;
        }
      } catch (CommandExecutionException e) {
        LOGGER.info("Could not find ImageMagick 6 at path {} because of: {}.", path,
            e.getMessage());
        LOGGER.debug("Could not find ImageMagick 6 at path {} due to following problem.", path, e);
      }
    }

    // So no image magick was found.
    LOGGER.error("Could not find ImageMagick 6 or 7. See previous log statements for details.");
    throw new MediaProcessorException("Could not find ImageMagick 6 or 7.");
  }

  /**
   * This is the main method of this class. It generates thumbnails for the given content.
   *
   * @param url The URL of the content. Used for determining the name of the output files.
   * @param detectedMimeType The detected mime type of the content.
   * @param content The resource content for which to generate thumbnails.
   * @return The metadata of the image as gathered during processing, together with the thumbnails.
   * The list can be null or empty, but does not contain null values or thumbnails without content.
   * @throws MediaExtractionException In case a problem occurred.
   */
  Pair<ImageMetadata, List<Thumbnail>> generateThumbnails(String url,
      String detectedMimeType, File content) throws MediaExtractionException {

    // Sanity checking
    if (content == null) {
      throw new MediaExtractionException("File content is null");
    }

    // TODO JV We should change this into a whitelist of supported formats.
    // Exception for DjVu files
    if (detectedMimeType.startsWith("image/vnd.djvu") || detectedMimeType.startsWith("image/x-djvu")
        || detectedMimeType.startsWith("image/x.djvu")) {
      throw new MediaExtractionException("Cannot generate thumbnails for DjVu file.");
    }

    // Obtain the thumbnail files (they are still empty) - create temporary files for them.
    final List<ThumbnailWithSize> thumbnails = prepareThumbnailFiles(url, detectedMimeType);

    // Load the thumbnails: delete the temporary files, and the thumbnails in case of exceptions.
    final ImageMetadata image;
    try {
      image = generateThumbnailsInternal(thumbnails, detectedMimeType, content);
    } catch (RuntimeException e) {
      closeAllThumbnailsSilently(thumbnails);
      throw new MediaExtractionException("Unexpected error during processing", e);
    } catch (MediaExtractionException e) {
      closeAllThumbnailsSilently(thumbnails);
      throw e;
    } finally {
      thumbnails.forEach(ThumbnailWithSize::deleteTempFileSilently);
    }

    // Done.
    final List<Thumbnail> resultThumbnails = thumbnails.stream()
        .map(ThumbnailWithSize::getThumbnail).collect(Collectors.toList());
    return new ImmutablePair<>(image, resultThumbnails);
  }

  private static void closeAllThumbnailsSilently(List<ThumbnailWithSize> thumbnails) {
    for (ThumbnailWithSize thumbnail : thumbnails) {
      thumbnail.getThumbnail().close();
    }
  }

  List<String> createThumbnailGenerationCommand(List<ThumbnailWithSize> thumbnails,
      String detectedMimeType, File content) {

    // Compile the command
    final List<String> command = new ArrayList<>(Arrays.asList(magickCmd, content.getPath() + "[0]",
        "-format", COMMAND_RESULT_FORMAT, "-write", "info:"));
    if (PDF_MIME_TYPE.equals(detectedMimeType)) {
      // in case of text (i.e. PDFs): specify white background
      command.addAll(Arrays.asList("-background", "white", "-alpha", "remove"));
    }
    final int thumbnailCounter = thumbnails.size();
    for (int i = 0; i < thumbnailCounter; i++) {
      // do not +delete the last one, use it to find dominant colors (smaller=quicker)
      if (i != thumbnailCounter - 1) {
        command.add("(");
        command.add("+clone");
      }
      final ThumbnailWithSize thumbnail = thumbnails.get(i);
      command.addAll(Arrays.asList("-thumbnail", thumbnail.getImageSize() + "x", "-write",
          thumbnail.getImageMagickTypePrefix() + thumbnail.getTempFileForThumbnail().toString()));
      if (i != thumbnailCounter - 1) {
        command.add("+delete");
        command.add(")");
      }
    }
    command.addAll(Arrays.asList("-colorspace", "sRGB", "-dither", "Riemersma", "-remap",
        colormapFile, "-format", "\n%c", "histogram:info:"));
    return command;
  }

  private ImageMetadata generateThumbnailsInternal(List<ThumbnailWithSize> thumbnails,
      String detectedMimeType, File content) throws MediaExtractionException {

    // Generate the thumbnails and read image properties.
    final List<String> response;
    try {
      response = commandExecutor
          .execute(createThumbnailGenerationCommand(thumbnails, detectedMimeType, content), false);
    } catch (CommandExecutionException e) {
      throw new MediaExtractionException("Could not analyze content and generate thumbnails.", e);
    }
    final ImageMetadata result = parseCommandResponse(response);

    // Check the thumbnails.
    for (ThumbnailWithSize thumbnail : thumbnails) {
      try {

        // Check that the thumbnails are not empty.
        final Path tempFileForThumbnail = thumbnail.getTempFileForThumbnail();
        if (getFileSize(tempFileForThumbnail) == 0) {
          throw new MediaExtractionException("Thumbnail file empty: " + tempFileForThumbnail);
        }

        // Copy the thumbnail. In case of images: don't make a thumbnail larger than the original.
        final boolean isImage = MediaType.getMediaType(detectedMimeType) == MediaType.IMAGE;
        final boolean shouldUseOriginal = isImage && result.getWidth() < thumbnail.getImageSize();
        if (shouldUseOriginal) {
          copyFile(content, thumbnail);
        } else {
          copyFile(thumbnail.getTempFileForThumbnail(), thumbnail);
        }

      } catch (IOException e) {
        throw new MediaExtractionException("Could not access thumbnail file", e);
      }
    }

    // Done.
    return result;
  }

  long getFileSize(Path file) throws IOException {
    return Files.size(file);
  }

  void copyFile(Path source, ThumbnailWithSize destination) throws IOException {
    try (final InputStream thumbnailStream = Files.newInputStream(source)) {
      destination.getThumbnail().markAsWithContent(thumbnailStream);
    }
  }

  void copyFile(File source, ThumbnailWithSize destination) throws IOException {
    copyFile(source.toPath(), destination);
  }

  List<ThumbnailWithSize> prepareThumbnailFiles(String url, String detectedMimeType) throws MediaExtractionException {

    // Decide on the thumbnail file type
    final String imageMagickThumbnailTypePrefix;
    final String thumbnailMimeType;
    if (PDF_MIME_TYPE.equals(detectedMimeType) || PNG_MIME_TYPE.equals(detectedMimeType)) {
      imageMagickThumbnailTypePrefix = "png:";
      thumbnailMimeType = PNG_MIME_TYPE;
    } else {
      imageMagickThumbnailTypePrefix = "jpeg:";
      thumbnailMimeType = JPEG_MIME_TYPE;
    }

    // Create the thumbnails: one for each kind
    final String md5 = md5Hex(url);
    final List<ThumbnailWithSize> result = new ArrayList<>(ThumbnailKind.values().length);
    try {
      for (ThumbnailKind thumbnailKind : ThumbnailKind.values()) {
        final String targetName = md5 + thumbnailKind.suffix;
        final ThumbnailImpl thumbnail = new ThumbnailImpl(url, thumbnailMimeType, targetName);
        result.add(
            new ThumbnailWithSize(thumbnail, thumbnailKind.size, imageMagickThumbnailTypePrefix));
      }
    } catch (IOException e) {
      throw new MediaExtractionException("Could not create temporary thumbnail files.", e);
    }

    // Done.
    return result;
  }

  private static String md5Hex(String s) throws MediaExtractionException {
    try {
      byte[] bytes = s.getBytes(StandardCharsets.UTF_8.name());
      byte[] md5bytes = MessageDigest.getInstance("MD5").digest(bytes);
      return String.format("%032x", new BigInteger(1, md5bytes));
    } catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
      throw new MediaExtractionException("Could not compute md5 hash", e);
    }
  }

  ImageMetadata parseCommandResponse(List<String> response) throws MediaExtractionException {
    try {

      // Get the dominant colors
      final Pattern pattern = Pattern.compile("#([0-9A-F]{6})");
      final List<String> colorStrings = response.stream()
          .skip(COMMAND_RESULT_COLORS_LINE).sorted(Collections.reverseOrder())
          .limit(COMMAND_RESULT_MAX_COLORS).collect(Collectors.toList());
      final Supplier<Stream<Matcher>> streamMatcherSupplier = () -> colorStrings.stream()
          .map(pattern::matcher);
      if (!streamMatcherSupplier.get().allMatch(Matcher::find)) {
        throw new IllegalStateException("Invalid color line found.");
      }
      final List<String> dominantColors = streamMatcherSupplier.get().filter(Matcher::find)
          .map(matcher -> matcher.group(1)).collect(Collectors.toList());

      // Get width, height and color space
      final int width = Integer.parseInt(response.get(COMMAND_RESULT_WIDTH_LINE));
      final int height = Integer.parseInt(response.get(COMMAND_RESULT_HEIGHT_LINE));
      final String colorSpace = response.get(COMMAND_RESULT_COLORSPACE_LINE);

      // Done.
      return new ImageMetadata(width, height, colorSpace, dominantColors);
    } catch (RuntimeException e) {
      LOGGER.info("Could not parse ImageMagick response:\n" + StringUtils.join(response, "\n"), e);
      throw new MediaExtractionException("File seems to be corrupted", e);
    }
  }

  static class ThumbnailWithSize {

    private final ThumbnailImpl thumbnail;
    private final int imageSize;
    private final Path tempFileForThumbnail;
    private final String imageMagickTypePrefix;

    ThumbnailWithSize(ThumbnailImpl thumbnail, int imageSize, Path tempFileForThumbnail,
        String imageMagickTypePrefix) {
      this.thumbnail = thumbnail;
      this.imageSize = imageSize;
      this.tempFileForThumbnail = tempFileForThumbnail;
      this.imageMagickTypePrefix = imageMagickTypePrefix;
    }

    ThumbnailWithSize(ThumbnailImpl thumbnail, int imageSize, String imageMagickTypePrefix)
        throws IOException {
      this(thumbnail, imageSize, Files.createTempFile("thumbnail_", null), imageMagickTypePrefix);
    }

    ThumbnailImpl getThumbnail() {
      return thumbnail;
    }

    int getImageSize() {
      return imageSize;
    }

    Path getTempFileForThumbnail() {
      return tempFileForThumbnail;
    }

    String getImageMagickTypePrefix() {
      return imageMagickTypePrefix;
    }

    void deleteTempFileSilently() {
      try {
        Files.delete(getTempFileForThumbnail());
      } catch (IOException e) {
        LOGGER.warn("Could not close thumbnail: {}", getTempFileForThumbnail(), e);
      }
    }
  }
}
