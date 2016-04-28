package de.uni_koblenz.west.koral.master.graph_cover_creator.impl;

import org.apache.jena.graph.Node;
import org.apache.jena.iri.IRI;
import org.apache.jena.riot.system.IRIResolver;

import de.uni_koblenz.west.koral.common.measurement.MeasurementCollector;
import de.uni_koblenz.west.koral.common.measurement.MeasurementType;
import de.uni_koblenz.west.koral.common.utils.RDFFileIterator;
import de.uni_koblenz.west.koral.master.utils.DeSerializer;

import java.io.File;
import java.io.OutputStream;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Creates a hierarchical hash cover.
 * 
 * @author Daniel Janke &lt;danijankATuni-koblenz.de&gt;
 *
 */
public class HierarchicalCoverCreator extends HashCoverCreator {

  public HierarchicalCoverCreator(Logger logger, MeasurementCollector measurementCollector) {
    super(logger, measurementCollector);
  }

  @Override
  protected void createCover(RDFFileIterator rdfFiles, int numberOfGraphChunks,
          OutputStream[] outputs, boolean[] writtenFiles, File workingDir) {
    int hierarchyLevel = identifyHierarchyLevel(new RDFFileIterator(rdfFiles), numberOfGraphChunks);
    if (measurementCollector != null) {
      measurementCollector.measureValue(MeasurementType.LOAD_GRAPH_COVER_CREATION_FILE_WRITE_START,
              System.currentTimeMillis());
    }
    for (Node[] statement : rdfFiles) {
      if (!statement[0].isURI()) {
        processStatement(numberOfGraphChunks, outputs, writtenFiles, statement);
      } else {
        String[] iriParts = getIRIHierarchy(DeSerializer.serializeNode(statement[0]));
        String iriPrefix = getIriPrefix(iriParts, hierarchyLevel);
        int targetChunk = computeHash(iriPrefix) % outputs.length;
        if (targetChunk < 0) {
          targetChunk *= -1;
        }

        writeStatementToChunk(targetChunk, numberOfGraphChunks, statement, outputs, writtenFiles);
      }
    }
    if (measurementCollector != null) {
      measurementCollector.measureValue(MeasurementType.LOAD_GRAPH_COVER_CREATION_FILE_WRITE_END,
              System.currentTimeMillis());
    }
  }

  private int identifyHierarchyLevel(RDFFileIterator rdfFiles, int numberOfGraphChunks) {
    if (measurementCollector != null) {
      measurementCollector.measureValue(
              MeasurementType.LOAD_GRAPH_COVER_CREATION_HIERARCHY_LEVEL_IDENTIFICATION_START,
              System.currentTimeMillis());
    }
    /*
     * The first dimension identifies the hierarchy level. Level 0 stores the
     * number of triples that have not an IRI as subject. They are not counted
     * in the following levels.
     * 
     * The second dimension aggregates the number of triples per graph chunk on
     * the given hierarchy. The length of this dimension is the number of graph
     * chunks.
     * 
     * The third dimension consists of tuples. The first element stores the
     * total number of triples assigned to a graph chunk at this hierarchy
     * level. The second element counts the number of triples assigned to this
     * chunk whose hierarchy level is <= the current level (i.e., they have too
     * short IRIs)
     */
    long[][][] tripleOccurences = new long[1][numberOfGraphChunks][2];
    tripleOccurences = computeTripleFrequencyPerHierarchyLevel(tripleOccurences, rdfFiles,
            numberOfGraphChunks);

    int balancedHierarchyLevel = Integer.MAX_VALUE;
    double minBalance = Double.MAX_VALUE;
    for (int i = 1; i < tripleOccurences.length; i++) {
      double currentHierarchyLevelBalance = getHierarchyLevelBalance(tripleOccurences[0],
              tripleOccurences[i]);
      if (currentHierarchyLevelBalance < minBalance) {
        balancedHierarchyLevel = i;
        minBalance = currentHierarchyLevelBalance;
      }
    }

    if (measurementCollector != null) {
      measurementCollector.measureValue(
              MeasurementType.LOAD_GRAPH_COVER_CREATION_HIERARCHY_LEVEL_IDENTIFICATION_END,
              System.currentTimeMillis());
    }

    return balancedHierarchyLevel - 1;
  }

  private double getHierarchyLevelBalance(long[][] nonIriTriple, long[][] iriTriples) {
    double averageNumberOfTriples = 0;
    for (int chunk = 0; chunk < iriTriples.length; chunk++) {
      averageNumberOfTriples += nonIriTriple[chunk][0] + iriTriples[chunk][0];
    }
    averageNumberOfTriples /= iriTriples.length;

    double standardDeviation = 0;
    for (int chunk = 0; chunk < iriTriples.length; chunk++) {
      double factor = (nonIriTriple[chunk][0] + iriTriples[chunk][0]) - averageNumberOfTriples;
      standardDeviation += factor * factor;
    }
    standardDeviation /= iriTriples.length;
    return Math.sqrt(standardDeviation);
  }

  private long[][][] computeTripleFrequencyPerHierarchyLevel(long[][][] tripleOccurences,
          RDFFileIterator rdfFiles, int numberOfGraphChunks) {
    for (Node[] statement : rdfFiles) {
      if (!statement[0].isURI()) {
        transformBlankNodes(statement);
        // assign to triple to chunk according to hash on subject
        String subjectString = statement[0].toString();
        int targetChunk = computeHash(subjectString) % numberOfGraphChunks;
        if (targetChunk < 0) {
          targetChunk *= -1;
        }
        tripleOccurences[0][targetChunk][0]++;
      } else {
        String[] iriParts = getIRIHierarchy(DeSerializer.serializeNode(statement[0]));
        if (iriParts.length >= tripleOccurences.length) {
          tripleOccurences = extendArray(tripleOccurences, iriParts.length);
        }
        for (int i = 0; i < iriParts.length; i++) {
          String prefix = getIriPrefix(iriParts, i);
          int targetChunk = computeHash(prefix) % numberOfGraphChunks;
          if (targetChunk < 0) {
            targetChunk *= -1;
          }
          if (i == (iriParts.length - 1)) {
            // this is the last hierarchy level
            for (int futureLevel = i + 1; futureLevel < tripleOccurences.length; futureLevel++) {
              tripleOccurences[futureLevel][targetChunk][0]++;
              tripleOccurences[futureLevel][targetChunk][1]++;
            }
          } else {
            tripleOccurences[i + 1][targetChunk][0]++;
          }
        }
      }
    }
    return tripleOccurences;
  }

  private long[][][] extendArray(long[][][] tripleOccurences, int newLength) {
    long[][][] extendedArray = new long[newLength + 1][][];
    System.arraycopy(tripleOccurences, 0, extendedArray, 0, tripleOccurences.length);
    for (int i = tripleOccurences.length; i < extendedArray.length; i++) {
      extendedArray[i] = new long[extendedArray[0].length][2];
      if (i > 1) {
        for (int chunk = 0; chunk < extendedArray[i].length; chunk++) {
          extendedArray[i][chunk][0] = extendedArray[i - 1][chunk][1];
          extendedArray[i][chunk][1] = extendedArray[i - 1][chunk][1];
        }
      }
    }
    return extendedArray;
  }

  private String[] getIRIHierarchy(String iriStr) {
    if (iriStr.startsWith("<")) {
      iriStr = iriStr.substring(1);
      if (iriStr.endsWith(">")) {
        iriStr = iriStr.substring(0, iriStr.length() - 1);
      }
    }
    IRI iri = IRIResolver.parseIRI(iriStr);
    String host = iri.getRawHost();
    String[] hostParts = null;
    if (host != null) {
      hostParts = host.split(Pattern.quote("."));
    }
    String path = iri.getRawPath();
    String[] pathParts = null;
    if (path != null) {
      if (path.startsWith("/")) {
        path = path.substring(1);
      }
      if (path.endsWith("/")) {
        path = path.substring(0, path.length() - 1);
      }
      if (!path.isEmpty()) {
        pathParts = path.split(Pattern.quote("/"));
      }
    }
    String fragment = iri.getRawFragment();
    String query = iri.getRawQuery();

    int iriHierarchyLength = 0;
    iriHierarchyLength += hostParts == null ? 0 : hostParts.length;
    iriHierarchyLength += pathParts == null ? 0 : pathParts.length;
    iriHierarchyLength += (fragment == null) || fragment.isEmpty() ? 0 : 1;
    iriHierarchyLength += (query == null) || query.isEmpty() ? 0 : 1;

    String[] result = new String[iriHierarchyLength];
    int nextInsertionIndex = 0;
    if (hostParts != null) {
      for (int i = hostParts.length - 1; i >= 0; i--) {
        result[nextInsertionIndex++] = hostParts[i];
      }
    }
    if (pathParts != null) {
      for (String pathPart : pathParts) {
        result[nextInsertionIndex++] = pathPart;
      }
    }
    if ((fragment != null) && !fragment.isEmpty()) {
      result[nextInsertionIndex++] = fragment;
    }
    if ((query != null) && !query.isEmpty()) {
      result[nextInsertionIndex++] = query;
    }
    return result;
  }

  private String getIriPrefix(String[] iriParts, int hierarchyLevel) {
    StringBuilder sb = new StringBuilder("/");
    for (int i = 0; (i <= hierarchyLevel) && (i < iriParts.length); i++) {
      sb.append("/").append(iriParts[i]);
    }
    return sb.toString();
  }

}