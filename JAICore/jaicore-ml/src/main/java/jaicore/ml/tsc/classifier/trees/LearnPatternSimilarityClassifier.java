package jaicore.ml.tsc.classifier.trees;

import java.util.ArrayList;
import java.util.List;

import org.aeonbits.owner.ConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jaicore.ml.core.exception.PredictionException;
import jaicore.ml.tsc.classifier.ASimplifiedTSClassifier;
import jaicore.ml.tsc.classifier.trees.LearnPatternSimilarityLearningAlgorithm.IPatternSimilarityConfig;
import jaicore.ml.tsc.dataset.TimeSeriesDataset;
import jaicore.ml.tsc.util.MathUtil;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

/**
 * Class representing the Learn Pattern Similarity classifier as described in
 * Baydogan, Mustafa & Runger, George. (2015). Time series representation and
 * similarity based on local autopatterns. Data Mining and Knowledge Discovery.
 * 30. 1-34. 10.1007/s10618-015-0425-y.
 *
 * This classifier currently only supports univariate time series prediction.
 *
 * @author Julian Lienen
 *
 */
public class LearnPatternSimilarityClassifier extends ASimplifiedTSClassifier<Integer> {

	/**
	 * Log4j logger
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(LearnPatternSimilarityClassifier.class);

	/**
	 * Segments (storing the start indexes) used for feature generation. The
	 * segments are randomly generated in the training phase.
	 */
	private int[][] segments;

	/**
	 * Segment differences (storing the start indexes) used for feature generation.
	 * The segments are randomly generated in the training phase.
	 */
	private int[][] segmentsDifference;

	/**
	 * The segments interval lengths used for each tree.
	 */
	private int[] lengthPerTree;

	/**
	 * The class attribute index per tree (as described in chapter 3.1 of the
	 * original paper)
	 */
	private int[] classAttIndexPerTree;

	/**
	 * The random regression model trees used for prediction.
	 */
	private RandomRegressionTree[] trees;

	/**
	 * The predicted leaf nodes for each instance per segment for each tree used
	 * within the 1NN search to predict the class values.
	 */
	private int[][][] trainLeafNodes;

	/**
	 * The targets of the training instances which are used within the 1NN search to
	 * predict the class values.
	 */
	private int[] trainTargets;

	/**
	 * Attributes used for the generation of Weka instances to use the internal Weka
	 * models.
	 */
	private ArrayList<Attribute> attributes;

	private final IPatternSimilarityConfig config;

	/**
	 * Standard constructor.
	 *
	 * @param seed
	 *            Seed used for randomized operations
	 * @param numTrees
	 *            Number of trees being trained
	 * @param maxTreeDepth
	 *            Maximum depth of the trained trees
	 * @param numSegments
	 *            Number of segments used per tree for feature generation
	 */
	public LearnPatternSimilarityClassifier(final int seed, final int numTrees, final int maxTreeDepth, final int numSegments) {
		this.config = ConfigCache.getOrCreate(IPatternSimilarityConfig.class);
		this.config.setProperty(IPatternSimilarityConfig.K_SEED, "" + seed);
		this.config.setProperty(IPatternSimilarityConfig.K_NUMTREES, "" + numTrees);
		this.config.setProperty(IPatternSimilarityConfig.K_MAXDEPTH, "" + maxTreeDepth);
		this.config.setProperty(IPatternSimilarityConfig.K_NUMSEGMENTS, "" + numSegments);
	}

	/**
	 * Predicts the class by generated segment and segment difference features based
	 * on <code>segments</code> and <code>segmentsDifference</code>. The induced
	 * instances are propagated to the forest of {@link RandomRegressionTree}s
	 * <code>trees</code>. The predicted leaf nodes are used within a 1NN search on
	 * the training leaf nodes to find the nearest instance and taking its class as
	 * prediction value.
	 *
	 * @param univInstance
	 *            Univariate instance to be predicted
	 *
	 */
	@Override
	public Integer predict(final double[] univInstance) throws PredictionException {
		if (!this.isTrained()) {
			throw new PredictionException("Model has not been built before!");
		}

		if (univInstance == null) {
			throw new IllegalArgumentException("Instance to be predicted must not be null or empty!");
		}

		int[][] leafNodeCounts = new int[this.trees.length][];

		for (int i = 0; i < this.trees.length; i++) {

			// Generate subseries features
			Instances seqInstances = new Instances("SeqFeatures", this.attributes, this.lengthPerTree[i]);

			for (int len = 0; len < this.lengthPerTree[i]; len++) {
				Instance instance = LearnPatternSimilarityLearningAlgorithm.generateSubseriesFeatureInstance(univInstance,
						this.segments[i], this.segmentsDifference[i], len);
				seqInstances.add(instance);
			}

			seqInstances.setClassIndex(this.classAttIndexPerTree[i]);
			leafNodeCounts[i] = new int[this.trees[i].nosLeafNodes];

			for(int inst = 0; inst< seqInstances.numInstances(); inst++) {
				LearnPatternSimilarityLearningAlgorithm.collectLeafCounts(leafNodeCounts[i], seqInstances.get(inst), this.trees[i]);
			}
		}
		return this.trainTargets[this.findNearestInstanceIndex(leafNodeCounts)];
	}

	/**
	 * Performs a simple nearest neighbor search on the stored
	 * <code>trainLeafNodes</code> for the given <code>leafNodeCounts</code> using
	 * Manhattan distance.
	 *
	 * @param leafNodeCounts
	 *            Leaf node counts induced during the prediction phase
	 * @return Returns the index of the nearest neighbor instance
	 */
	public int findNearestInstanceIndex(final int[][] leafNodeCounts) {
		double minDistance = Double.MAX_VALUE;
		int nearestInstIdx = 0;
		for (int inst = 0; inst < this.trainLeafNodes.length; inst++) {
			double tmpDist = 0;
			for (int i = 0; i < this.trainLeafNodes[inst].length; i++) {
				tmpDist += MathUtil.intManhattanDistance(this.trainLeafNodes[inst][i], leafNodeCounts[i]);
			}

			if (tmpDist < minDistance) {
				minDistance = tmpDist;
				nearestInstIdx = inst;
			}
		}
		return nearestInstIdx;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Integer predict(final List<double[]> multivInstance) throws PredictionException {
		LOGGER.warn(
				"Dataset to be predicted is multivariate but only first time series (univariate) will be considered.");

		return this.predict(multivInstance.get(0));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Integer> predict(final TimeSeriesDataset dataset) throws PredictionException {
		if (!this.isTrained()) {
			throw new PredictionException("Model has not been built before!");
		}

		if (dataset.isMultivariate()) {
			throw new UnsupportedOperationException("Multivariate instances are not supported yet.");
		}

		if (dataset == null || dataset.isEmpty()) {
			throw new IllegalArgumentException("Dataset to be predicted must not be null or empty!");
		}

		double[][] data = dataset.getValuesOrNull(0);
		List<Integer> predictions = new ArrayList<>();
		LOGGER.debug("Starting prediction...");
		for (int i = 0; i < data.length; i++) {
			predictions.add(this.predict(data[i]));
		}
		LOGGER.debug("Finished prediction.");
		return predictions;
	}

	/**
	 * @return the segments
	 */
	public int[][] getSegments() {
		return this.segments;
	}

	/**
	 * @param segments
	 *            the segments to set
	 */
	public void setSegments(final int[][] segments) {
		this.segments = segments;
	}

	/**
	 * @return the segmentsDifference
	 */
	public int[][] getSegmentsDifference() {
		return this.segmentsDifference;
	}

	/**
	 * @param segmentsDifference
	 *            the segmentsDifference to set
	 */
	public void setSegmentsDifference(final int[][] segmentsDifference) {
		this.segmentsDifference = segmentsDifference;
	}

	/**
	 * @return the lengthPerTree
	 */
	public int[] getLengthPerTree() {
		return this.lengthPerTree;
	}

	/**
	 * @param lengthPerTree
	 *            the lengthPerTree to set
	 */
	public void setLengthPerTree(final int[] lengthPerTree) {
		this.lengthPerTree = lengthPerTree;
	}

	/**
	 * @return the classAttIndexPerTree
	 */
	public int[] getClassAttIndexPerTree() {
		return this.classAttIndexPerTree;
	}

	/**
	 * @param classAttIndexPerTree
	 *            the classAttIndexPerTree to set
	 */
	public void setClassAttIndexPerTree(final int[] classAttIndexPerTree) {
		this.classAttIndexPerTree = classAttIndexPerTree;
	}

	/**
	 * @return the trees
	 */
	public RandomRegressionTree[] getTrees() {
		return this.trees;
	}

	/**
	 * @param trees
	 *            the trees to set
	 */
	public void setTrees(final RandomRegressionTree[] trees) {
		this.trees = trees;
	}

	/**
	 * @return the trainLeafNodes
	 */
	public int[][][] getTrainLeafNodes() {
		return this.trainLeafNodes;
	}

	/**
	 * @param trainLeafNodes
	 *            the trainLeafNodes to set
	 */
	public void setTrainLeafNodes(final int[][][] trainLeafNodes) {
		this.trainLeafNodes = trainLeafNodes;
	}

	/**
	 * @return the trainTargets
	 */
	public int[] getTrainTargets() {
		return this.trainTargets;
	}

	/**
	 * @param trainTargets
	 *            the trainTargets to set
	 */
	public void setTrainTargets(final int[] trainTargets) {
		this.trainTargets = trainTargets;
	}

	/**
	 * @return the attributes
	 */
	public ArrayList<Attribute> getAttributes() {
		return this.attributes;
	}

	/**
	 * @param attributes
	 *            the attributes to set
	 */
	public void setAttributes(final ArrayList<Attribute> attributes) {
		this.attributes = attributes;
	}

	@Override
	public LearnPatternSimilarityLearningAlgorithm getLearningAlgorithm(final TimeSeriesDataset dataset) {
		return new LearnPatternSimilarityLearningAlgorithm(this.config, this, dataset);
	}
}
