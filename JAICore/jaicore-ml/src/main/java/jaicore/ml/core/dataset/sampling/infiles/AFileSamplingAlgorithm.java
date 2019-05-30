package jaicore.ml.core.dataset.sampling.infiles;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jaicore.basic.algorithm.AAlgorithm;
import jaicore.basic.algorithm.AlgorithmExecutionCanceledException;
import jaicore.basic.algorithm.EAlgorithmState;
import jaicore.basic.algorithm.exceptions.AlgorithmException;
import jaicore.basic.algorithm.exceptions.AlgorithmTimeoutedException;
import jaicore.ml.core.dataset.ArffUtilities;

/**
 * An abstract class for file-based sampling algorithms providing basic
 * functionality of an algorithm.
 *
 * @author Lukas Brandt
 */
public abstract class AFileSamplingAlgorithm extends AAlgorithm<File, File> {

	private static final Logger LOG = LoggerFactory.getLogger(AFileSamplingAlgorithm.class);

	protected Integer sampleSize = null;
	private String outputFilePath = null;
	protected FileWriter outputFileWriter;

	protected AFileSamplingAlgorithm(final File input) {
		super(input);
	}

	public void setSampleSize(final int size) {
		this.sampleSize = size;
	}

	public void setOutputFileName(final String outputFilePath) throws IOException {
		this.outputFilePath = outputFilePath;
		this.outputFileWriter = new FileWriter(outputFilePath);
	}

	@Override
	public File call() throws InterruptedException, AlgorithmExecutionCanceledException, AlgorithmException {
		Instant timeoutTime = null;
		if (this.getTimeout().milliseconds() <= 0) {
			LOG.debug("Invalid or no timeout set. There will be no timeout in this algorithm run");
			timeoutTime = Instant.MAX;
		} else {
			timeoutTime = Instant.now().plus(this.getTimeout().milliseconds(), ChronoUnit.MILLIS);
			if (LOG.isDebugEnabled()) {
				LOG.debug("Set timeout to {}", timeoutTime);
			}
		}
		// Check missing or invalid configuration.
		if (this.outputFilePath == null || this.outputFilePath.length() == 0) {
			throw new AlgorithmException("No output file path specified");
		}
		if (this.sampleSize == null) {
			throw new AlgorithmException("No valid sample size specified");
		}
		File dataset = this.getInput();
		if (dataset == null || !dataset.exists() || !dataset.isFile()) {
			throw new AlgorithmException("No dataset file or an invalid dataset file was given as an input.");
		}
		// Working configuration, so create the actual sample.
		// Write the ARFF header to the output file.
		try {
			this.outputFileWriter.write(ArffUtilities.extractArffHeader(this.getInput()));
		} catch (IOException e) {
			throw new AlgorithmException(e, "Error while writing to given output path.");
		}
		// Check if the requested sample size is zero and we can stop directly.
		if (this.sampleSize == 0) {
			LOG.warn("Sample size is 0, so an empty data set is returned!");
			return new File(this.outputFilePath);
		}
		// Start the sampling process otherwise.
		this.setState(EAlgorithmState.CREATED);
		while (this.hasNext()) {
			try {
				this.checkAndConductTermination();
			} catch (AlgorithmTimeoutedException e) {
				this.cleanUp();
				throw new AlgorithmException(e.getMessage());
			}
			if (Instant.now().isAfter(timeoutTime)) {
				LOG.warn("Algorithm is running even though it has been timeouted. Cancelling..");
				this.cancel();
				throw new AlgorithmException("Algorithm is running even though it has been timeouted");
			} else {
				this.next();
			}
		}
		try {
			this.outputFileWriter.flush();
			this.outputFileWriter.close();
		} catch (IOException e) {
			this.cleanUp();
			throw new AlgorithmException(e, "Could not close File writer for sampling output file");
		}
		this.cleanUp();
		return new File(this.outputFilePath);
	}

	/**
	 * Implement custom clean up behaviour.
	 */
	protected abstract void cleanUp();

}
