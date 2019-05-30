package jaicore.ml.tsc.classifier.trees;

import static org.junit.Assert.assertArrayEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import junit.framework.Assert;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

/**
 * Unit tests for {@link LearnPatternSimilarityLearningAlgorithm}.
 *
 * @author Julian Lienen
 *
 */
public class LearnPatternSimilarityAlgorithmTest {
	/**
	 * Maximal delta for asserts with precision.
	 */
	private static final double EPS_DELTA = 0.000001;

	/**
	 * Number of segments used within the tests.
	 */
	private static final int NUM_SEGMENTS = 2;

	/**
	 * Number of trees used within the tests.
	 */
	private static final int NUM_TREES = 10;

	/**
	 * Maximum tree depth used within the tests.
	 */
	private static final int MAX_TREE_DEPTH = 10;

	/**
	 * Pre-generated segments used within the tests.
	 */
	private int[] segments;

	/**
	 * Pre-generated segment differences used within the tests.
	 */
	private int[] segmentsDifference;

	/**
	 * Attributes used for feature generation within the tests.
	 */
	private ArrayList<Attribute> attributes;

	/**
	 * Algorithm instance used for non-static calls within the tests.
	 */
	private LearnPatternSimilarityLearningAlgorithm algorithm;

	private double[][] exampleInstanceValues;

	@Before
	public void setup() {
		// Set attributes
		this.attributes = new ArrayList<>();
		for (int i = 0; i < 2 * NUM_SEGMENTS; i++) {
			this.attributes.add(new Attribute("val" + i));
		}

		this.segments = new int[] { 2, 3 };
		this.segmentsDifference = new int[] { 3, 1 };

		LearnPatternSimilarityClassifier classifier = new LearnPatternSimilarityClassifier(42, NUM_TREES, MAX_TREE_DEPTH, NUM_SEGMENTS);
		this.algorithm = classifier.getLearningAlgorithm(null);

		this.exampleInstanceValues = new double[][] { { 2, 4, 3, 1, 0, 5, 2.5 }, { 5, 1, 8, 5, 4, 4, 9 } };
	}

	/**
	 * See
	 * {@link LearnPatternSimilarityLearningAlgorithm#generateSubseriesFeatureInstance(double[], int, int[], int[], int)}.
	 */
	@Test
	public void generateSubseriesFeatureInstanceTest() {

		final int lenIdx = 2;

		final Instance resultInstance = LearnPatternSimilarityLearningAlgorithm.generateSubseriesFeatureInstance(this.exampleInstanceValues[0], this.segments, this.segmentsDifference, lenIdx);

		// Check number of attributes
		Assert.assertEquals("The number of attributes does not match the expected number of attributes.", this.segments.length + this.segmentsDifference.length, resultInstance.numAttributes());

		// Check feature values
		assertArrayEquals("The generated features do not match the expected generated feature values.", new double[] { 0, -2.5, 5, -1 }, resultInstance.toDoubleArray(), EPS_DELTA);
	}

	/**
	 * See
	 * {@link LearnPatternSimilarityLearningAlgorithm#generateSubseriesFeaturesInstances(java.util.ArrayList, int, int[], int[], double[][])}.
	 */
	@Test
	public void generateSubseriesFeaturesInstancesTest() {
		final int lenIdx = 2;

		final Instances resultInstances = LearnPatternSimilarityLearningAlgorithm.generateSubseriesFeaturesInstances(this.attributes, lenIdx, this.segments, this.segmentsDifference, this.exampleInstanceValues);

		// Check number of attributes
		Assert.assertEquals("The number of attributes does not match the expected number of attributes.", this.segments.length + this.segmentsDifference.length, resultInstances.numAttributes());

		// Check feature values
		assertArrayEquals("The generated features do not match the expected generated feature values.", new double[] { 3, -1, 1, -1 }, resultInstances.get(0).toDoubleArray(), EPS_DELTA);
	}

	/**
	 * See {@link LearnPatternSimilarityLearningAlgorithm#initializeRegressionTree(int)}.
	 */
	@Test
	public void initializeRegressionTreeTest() {
		final RandomRegressionTree regTree = this.algorithm.initializeRegressionTree(100);

		// Check tree initialization
		Assert.assertEquals("The random regression tree uses a different seed than configured after initalization.", this.algorithm.getConfig().seed(), regTree.getSeed());
		Assert.assertEquals("The random regression tree uses a different maximum tree depth than configured after initalization.", this.algorithm.getConfig().maxDepth(), regTree.getMaxDepth());
		Assert.assertEquals("The random regression tree uses a different K value than configured after initalization.", 1, regTree.getKValue());
	}

	/**
	 * See
	 * {@link LearnPatternSimilarityLearningAlgorithm#generateSegmentsAndDifferencesForTree(int[], int[], int, int, java.util.Random)}.
	 */
	@Test
	public void generateSegmentsAndDifferencesForTreeTest() {
		final int[] segments = new int[NUM_SEGMENTS];
		final int[] segmentsDifference = new int[NUM_SEGMENTS];
		final int length = 10;
		final int timeSeriesLength = 50;

		final Random random = new Random(this.algorithm.getConfig().seed());

		this.algorithm.generateSegmentsAndDifferencesForTree(segments, segmentsDifference, length, timeSeriesLength, random);

		Assert.assertTrue("A start element of the generated segments is outside of the valid range.", Arrays.stream(segments).anyMatch(i -> i > 0 && i < (timeSeriesLength - length)));
		Assert.assertTrue("A start element of the generated segment differences is outside of the valid range.", Arrays.stream(segmentsDifference).anyMatch(i -> i > 0 && i < (timeSeriesLength - length - 1)));
	}
}
