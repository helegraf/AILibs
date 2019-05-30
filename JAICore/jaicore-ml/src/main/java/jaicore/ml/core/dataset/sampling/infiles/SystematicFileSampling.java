package jaicore.ml.core.dataset.sampling.infiles;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import jaicore.basic.TempFileHandler;
import jaicore.basic.algorithm.AlgorithmExecutionCanceledException;
import jaicore.basic.algorithm.events.AlgorithmEvent;
import jaicore.basic.algorithm.exceptions.AlgorithmException;
import jaicore.ml.core.dataset.ArffUtilities;
import jaicore.ml.core.dataset.sampling.SampleElementAddedEvent;

/**
 * File-level implementation of Systematic Sampling: Sort datapoints and pick
 * every k-th datapoint for the sample.
 * 
 * @author Lukas Brandt
 */
public class SystematicFileSampling extends AFileSamplingAlgorithm {

	private Random random;
	private int index;
	private int addedDatapoints;
	private TempFileHandler tempFileHandler;
	private Comparator<String> datapointComparator;
	private BufferedReader sortedDatasetFileReader;
	private List<Integer> indicesForSelection;

	/**
	 * Simple constructor that uses the default datapoint comparator.
	 * 
	 * @param random
	 *            Random Object for determining the sampling start point.
	 */
	public SystematicFileSampling(Random random, File input) {
		this(random, null, input);
	}

	/**
	 * Constructor for a custom datapoint comparator.
	 * 
	 * @param random
	 *            Random Object for determining the sampling start point.
	 * @param datapointComparator
	 *            Comparator to sort the dataset.
	 */
	public SystematicFileSampling(Random random, Comparator<String> datapointComparator, File input) {
		super(input);
		this.random = random;
		this.datapointComparator = datapointComparator;
		this.tempFileHandler = new TempFileHandler();
	}

	@Override
	public AlgorithmEvent nextWithException()
			throws InterruptedException, AlgorithmExecutionCanceledException, AlgorithmException {
		switch (this.getState()) {
		case CREATED:
			// Sort dataset and skip with reader the ARFF header.
			File sortedDatasetFile = null;
			try {
				DatasetFileSorter sorter = new DatasetFileSorter(this.getInput(), this.tempFileHandler);
				if (this.datapointComparator != null) {
					sorter.setComparator(this.datapointComparator);
				}
				sortedDatasetFile = sorter.sort(
						this.tempFileHandler.getTempFileDirPath() + File.separator + UUID.randomUUID().toString());
				sortedDatasetFile.deleteOnExit();
				this.sortedDatasetFileReader = new BufferedReader(new FileReader(sortedDatasetFile));
				ArffUtilities.skipWithReaderToDatapoints(this.sortedDatasetFileReader);
			} catch (IOException e) {
				throw new AlgorithmException(e, "Was not able to create a sorted dataset file.");
			}
			// Count datapoints in the sorted dataset and initialize variables.
			try {
				this.addedDatapoints = 0;
				this.index = 0;
				int datapointAmount = ArffUtilities.countDatasetEntries(sortedDatasetFile, true);
				this.indicesForSelection = new LinkedList<>();
				int k = datapointAmount / this.sampleSize;
				int startIndex = this.random.nextInt(datapointAmount);
				int i = 0;
				while (this.indicesForSelection.size() < this.sampleSize) {
					int e = (startIndex + k * (i++)) % datapointAmount;
					this.indicesForSelection.add(e);
				}
				this.indicesForSelection.sort(Integer::compare);
				return this.activate();
			} catch (IOException e) {
				throw new AlgorithmException(e, "Was not able to count the datapoints.");
			}
		case ACTIVE:
			// If the sample size is not reached yet, add the next datapoint from the
			// systematic sampling method.
			if (this.addedDatapoints < this.sampleSize) {
				try {
					// Determine and find the next k-th element.
					int e = this.indicesForSelection.get(this.addedDatapoints);
					String datapoint = this.sortedDatasetFileReader.readLine();
					this.index++;
					while (this.index < e) {
						datapoint = this.sortedDatasetFileReader.readLine();
						this.index++;
					}
					// Add this datapoint to the output file.
					assert datapoint != null;
					this.outputFileWriter.write(datapoint + "\n");
					this.addedDatapoints++;
					return new SampleElementAddedEvent(getId());
				} catch (IOException e) {
					throw new AlgorithmException(e, "Was not able to read from sorted dataset file.");
				}
			} else {
				// Delete sorted dataset file and terminate
				this.cleanUp();
				return this.terminate();
			}
		case INACTIVE:
			this.cleanUp();
			if (this.addedDatapoints < this.sampleSize) {
				throw new AlgorithmException("Expected sample size was not reached before termination");
			} else {
				return this.terminate();
			}
		default:
			this.cleanUp();
			throw new IllegalStateException("Unknown algorithm state " + this.getState());
		}
	}

	@Override
	protected void cleanUp() {
		this.tempFileHandler.cleanUp();
	}

}
