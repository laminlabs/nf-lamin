import java.nio.file.Path
import java.nio.file.Paths

class PathUtils {

    /**
     * Gets the suffix (extension) of a filename, including the leading dot.
     *
     * @param filename The filename string.
     * @return The suffix including the dot, or an empty string if no suffix
     * (e.g., "file", ".bashrc", "file.").
     * @throws IllegalArgumentException if the filename is null.
     */
  static String getSuffix(String filename) {
    if (filename == null) {
      throw new IllegalArgumentException('Filename cannot be null')
    }

    int dotIndex = filename.lastIndexOf('.')

    // If no dot, or dot is the first character (e.g., .bashrc), or dot is the last character
    if (dotIndex < 0 || dotIndex == 0 || dotIndex == filename.length() - 1) {
      return ''
    }

    // Return the substring from the last dot to the end
    return filename.substring(dotIndex)
  }

    /**
     * Gets the suffix (extension) from a Path object.
     * Convenience method using the getSuffix(String) method.
     *
     * @param path The Path object.
     * @return The suffix including the dot, or an empty string.
     * @throws IllegalArgumentException if the path is null.
     */
  static String getSuffix(Path path) {
    if (path == null) {
      throw new IllegalArgumentException('Path cannot be null')
    }
    Path fileName = path.getFileName()
    if (fileName == null) {
      return '' // Path represents a root directory or similar
    }
    return getSuffix(fileName.toString())
  }

}
