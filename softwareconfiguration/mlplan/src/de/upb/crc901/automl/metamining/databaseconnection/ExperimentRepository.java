package de.upb.crc901.automl.metamining.databaseconnection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import dataHandling.mySQL.MetaDataDataBaseConnection;
import de.upb.crc901.automl.hascowekaml.MLPipelineComponentInstanceFactory;
import hasco.model.ComponentInstance;
import jaicore.basic.SQLAdapter;
import weka.core.Instances;

/**
 * Manages a connection to experiment data of pipelines on dataset in a data
 * base.
 * 
 * @author Helena Graf
 *
 */
public class ExperimentRepository {
	private SQLAdapter adapter;
	private String host;
	private String user;
	private String password;
	private MLPipelineComponentInstanceFactory factory;
	private int CPUs;
	private String metaFeatureSetName;
	private String datasetSetName;

	private List<HashMap<String, List<Double>>> pipelinePerformances = new ArrayList<HashMap<String, List<Double>>>();
	private MetaDataDataBaseConnection metaDataBaseConnection;

	public ExperimentRepository(String host, String user, String password, MLPipelineComponentInstanceFactory factory,
			int CPUs, String metaFeatureSetName, String datasetSetName) {
		this.user = user;
		this.password = password;
		this.host = host;
		this.factory = factory;
		this.CPUs = CPUs;
		this.metaDataBaseConnection = new MetaDataDataBaseConnection(host, user, password, "hgraf");
		this.metaFeatureSetName = metaFeatureSetName;
		this.datasetSetName = datasetSetName;
	}

	public List<ComponentInstance> getDistinctPipelines() throws Exception {
		connect();

		String query = "SELECT COUNT(DISTINCT pipeline) FROM evaluations";
		ResultSet resultSet = adapter.getResultsOfQuery(query);
		resultSet.next();
		int distinctPipelineCount = resultSet.getInt("COUNT(DISTINCT pipeline)");
		System.out.println(distinctPipelineCount + " distinct pipelines will be downloaded.");

		int chunkSize = Math.floorDiv(distinctPipelineCount, CPUs);
		int lastchunkSize = distinctPipelineCount - (chunkSize * (CPUs - 1));

		ComponentInstanceDatabaseGetter[] threads = new ComponentInstanceDatabaseGetter[CPUs];

		for (int i = 0; i < threads.length; i++) {
			threads[i] = new ComponentInstanceDatabaseGetter();
			threads[i].setAdapter(adapter);
			threads[i].setOffset(i * chunkSize);
			threads[i].setLimit(i == threads.length - 1 ? chunkSize : lastchunkSize);
			threads[i].setFactory(factory);
			threads[i].start();
		}

		List<ComponentInstance> pipelines = new ArrayList<ComponentInstance>();
		for (int i = 0; i < threads.length; i++) {
			threads[i].join();
			pipelines.addAll(threads[i].getPipelines());
			pipelinePerformances.addAll(threads[i].getPipelinePerformances());
		}

		for (int i = 0; i < threads.length; i++) {
			System.out.println(
					"Threads " + threads[i].getId() + " finished succesfully: " + threads[i].isFinishedSuccessfully());
		}

		disconnect();

		return pipelines;
	}

	public Instances getDatasetCahracterizations() {
		// get distinct data sets (ordered=

		// get data set characterizations
		return null;
	}

	/**
	 * Gets all the pipeline results for the distinct pipelines from
	 * {@link #getDistinctPipelines()}, thus has to be called after that method.
	 * 
	 * @return The results of pipelines on datasets: rows: data sets, columns:
	 *         pipelines, entries: array of results of pipeline on data set
	 * @throws SQLException
	 *             If something goes wrong while connecting to the database
	 */
	public double[][][] getPipelineResultsOnDatasets() throws SQLException {
		// These ONLY need to be in the same order dataset-wise as get
		// datasetcharacterizations
		List<String> datasets = metaDataBaseConnection.getMembersOfMetadataSet(metaFeatureSetName);

		// Map hgraf datasets to mlplan_results datasets
		HashMap<String, String> datasetNameToIndexMap = new HashMap<>();
		// TODO get all the dataset mappings and select those who have no ml plan id as
		// isys id otherwise ml plan id

		double[][][] results = new double[datasetNameToIndexMap.size()][pipelinePerformances.size()][];

		for (int j = 0; j < datasets.size(); j++) {
			String dataset = datasets.get(j);
			for (int i = 0; i < pipelinePerformances.size(); i++) {
				// Does the pipeline have a result for the dataset
				List<Double> datasetResults = pipelinePerformances.get(i).get(datasetNameToIndexMap.get(dataset));
				if (datasetResults != null && datasetResults.size() > 0) {
					results[j][i] = datasetResults.stream().mapToDouble(value -> value).toArray();
				}
			}
		}
		return results;
	}

	// /**
	// * Returns the characterization of the data set if it is available in the data
	// * base and null otherwise
	// *
	// * @param dataSetName
	// * @return
	// * @throws SQLException
	// */
	// public HashMap<String, Double> getCharacterizationForDataset(String
	// dataSetName, String metaFeatureSetName)
	// throws SQLException {
	// throw new UnsupportedOperationException();
	// // TODO need to get data set id for name (implement properly) to speed up
	// evaluation
	//// int datasetId = 0;
	////
	//// // get chara for data set id
	//// Instances metaDataForInstance = connect.getMetaDataSetForDataSet(datasetId,
	// metaFeatureSetName);
	//// metaDataForInstance.deleteAttributeAt(0);
	////
	//// boolean onlyNans = true;
	//// HashMap<String, Double> mfValues = new HashMap<String, Double>();
	//// for (int i = 0; i < metaDataForInstance.numAttributes(); i++) {
	//// mfValues.put(metaDataForInstance.attribute(i).name(),
	// metaDataForInstance.get(0).value(i));
	//// if (!Double.isNaN(metaDataForInstance.get(0).value(i))) {
	//// onlyNans = false;
	//// }
	//// }
	////
	//// if (onlyNans) {
	//// return null;
	//// }
	////
	//// return mfValues;
	// }

	private void connect() {
		adapter = new SQLAdapter(host, user, password, "mlplan_results");
	}

	private void disconnect() {
		adapter.close();
	}
}
