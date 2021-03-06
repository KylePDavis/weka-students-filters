/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <http://unlicense.org/>
 */

package weka.filters.unsupervised.attribute;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Vector;

import filters.FastICA;


import weka.core.Attribute;
import weka.core.AttributeStats;
import weka.core.Capabilities;
import weka.core.DenseInstance;
import weka.core.SparseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Option;
import weka.core.OptionHandler;
import weka.core.RevisionUtils;
import weka.core.Capabilities.Capability;
import weka.core.Utils;
import weka.filters.Filter;
import weka.filters.UnsupervisedFilter;
import weka.filters.unsupervised.attribute.Remove;

// TODO: add support for negative entropy estimator option
public class IndependentComponents
	extends Filter
	implements OptionHandler, UnsupervisedFilter{

	/** for serialization. */
	private static final long serialVersionUID = -5416810876710954131L;

	/** The data to transform analyse/transform. */
    protected Instances m_TrainInstances;

    /** Keep a copy for the class attribute (if set). */
    protected Instances m_TrainCopy;

	/** The header for the transformed data format. */
	protected Instances m_TransformedFormat;

	/** True when the instances sent to determineOutputFormat() has a class attribute */
	protected boolean m_HasClass;

	/** Class index. */
    protected int m_ClassIndex;

	/** Number of attributes. */
    protected int m_NumAttribs;

    /** Number of instances. */
    protected int m_NumInstances;

	/** where the magic happens */
	protected FastICA m_filter;

	/** If true, whiten input data. */
	protected boolean m_whiten = true;

	/** Number of attributes to include. */
	protected int m_OutputNumAtts = -1;

	/** Maximum number of FastICA iterations. */
	protected int m_numIterations = 200;

	/** Error tolerance for convergence. */
	protected double m_tolerance = 1E-4;

	public String globalInfo() {
		return "Performs Independent Component Analysis and transformation " +
				"of numeric data using the FastICA algorithm while ignoring " +
				"the class label.";
	}

	public String whitenDataTipText() {
		return "Whiten the data (decoupling transform) if set.";
	}

	public void setWhitenData(boolean flag) {
		m_whiten = flag;
	}

	public boolean getWhitenData() {
		return m_whiten;
	}

	public String numAttributesTipText() {
		return "Number of separate sources to identify in the output." +
				" (-1 = include all; default: -1)";
	}

	public void setNumAttributes(int num) {
		m_OutputNumAtts = num;
	}

	public int getNumAttributes() {
		return m_OutputNumAtts;
	}

	public String numIterationsTipText() {
		return "The maximum number of iterations of the FastICA main loop to allow.";
	}

	public void setNumIterations(int num) {
		m_numIterations = num;
	}

	public int getNumIterations() {
		return m_numIterations;
	}

	public String toleranceTipText() {
		return "Error tolerance for solution convergence.";
	}

	public void setTolerance(double tolerance) {
		m_tolerance = tolerance;
	}

	public double getTolerance() {
		return m_tolerance;
	}

	/**
	 * Returns the capabilities of this evaluator.
	 *
	 * @return            the capabilities of this evaluator
	 * @see               Capabilities
	 */
	public Capabilities getCapabilities() {
		Capabilities result = super.getCapabilities();
		result.disableAll();

		// attributes
		result.enable(Capability.NUMERIC_ATTRIBUTES);

		// class
		result.enable(Capability.NOMINAL_CLASS);
		result.enable(Capability.NUMERIC_CLASS);
		result.enable(Capability.DATE_CLASS);
		result.enable(Capability.MISSING_CLASS_VALUES);
		result.enable(Capability.NO_CLASS);

		return result;
	}

	/**
	 * Returns an enumeration describing the available options.
	 *
	 * @return      an enumeration of all the available options.
	 */
	public Enumeration<Option> listOptions() {
		Vector<Option> result = new Vector<Option>();

		result.addElement(new Option(
				"\tWhiten the data (decorrelate the inputs so the covariance\n" +
				"\tis the identity matrix) before performing ICA. This should\n" +
				"\t*probably* be enabled unless you whitened the data yourself\n" +
				"\tor have a specific reason not to perform whitening.",
				"W", 0, "-W"));

		result.addElement(new Option(
				"\tMaximum number of attributes to include in results.\n" +
				"\t(-1 = include all, default: all)",
				"A", 1, "-A <num>"));

		result.addElement(new Option(
				"\tMaximum number of iterations.\n\t(default: 200)",
				"N", 1, "-N <num>"));

		result.addElement(new Option(
				"\tConvergence tolerance to stop training.\n\t(default: 1E-4)",
				"T", 1, "-T <num>"));

		return result.elements();
	}

	/**
	 * Parses a list of options for this object. <p/>
	 *
	 <!-- options-start -->
	 * Valid options are: <p/>
	 *
	 * <pre> -W
	 *  Whiten the data (decorrelate the inputs so the covariance
	 *  is the identity matrix) before performing ICA. This should
	 *  *probably* be enabled unless you whitened the data yourself
	 *  or have a specific reason not to perform whitening.</pre>
	 *
	 * <pre> -A &lt;num&gt;
	 *  Maximum number of attributes to include in results.
	 *  (-1 = include all, default: all)</pre>
	 *
	 * <pre> -N &lt;num&gt;
	 *  Maximum number of iterations.
	 *  (default: 200)</pre>
	 *
	 * <pre> -T &lt;num&gt;
	 *  Convergence tolerance to stop training.
	 *  (default: 1E-4)</pre>
	 *
	 <!-- options-end -->
	 *
	 * @param options 	the list of options as an array of strings
	 * @throws Exception 	if an option is not supported
	 */
	public void setOptions(String[] options) throws Exception {
		String        tmpStr;

		setWhitenData(Utils.getFlag('W', options));

		tmpStr = Utils.getOption('A', options);
		if (tmpStr.length() != 0)
			setNumAttributes(Integer.parseInt(tmpStr));
		else
			setNumAttributes(-1);

		tmpStr = Utils.getOption('N', options);
		if (tmpStr.length() != 0)
			setNumIterations(Integer.parseInt(tmpStr));
		else
			setNumIterations(200);

		tmpStr = Utils.getOption('T', options);
		if (tmpStr.length() != 0)
			setTolerance(Double.parseDouble(tmpStr));
		else
			setTolerance(1E-4);
	}

	/**
	 * Gets the current settings of the filter.
	 *
	 * @return      an array of strings suitable for passing to setOptions
	 */
	public String[] getOptions() {
		Vector<String>   result;

		result = new Vector<String>();

		result.add("-W");

		result.add("-A");
		result.add("" + getNumAttributes());

		result.add("-N");
		result.add("" + getNumIterations());

		result.add("-T");
		result.add("" + getTolerance());

		return result.toArray(new String[result.size()]);
	}

	/**
	 * Determines the output format based on the input format and returns
	 * this. In case the output format cannot be returned immediately, i.e.,
	 * immediateOutputFormat() returns false, then this method will be called
	 * from batchFinished().
	 *
	 * @param inputFormat     the input format to base the output format on
	 * @return                the output format
	 * @throws Exception      in case the determination goes wrong
	 * @see   #batchFinished()
	 */
	protected Instances determineOutputFormat(Instances inputFormat)
			throws Exception {

		// Error if any data is missing or for non-numeric attributes
		for (int j = 0; j < inputFormat.numAttributes(); j++) {

			if (j == inputFormat.classIndex()) {  // skip the class index
				continue;
			}

			if (!inputFormat.attribute(j).isNumeric()) {
				throw new Exception("All data must be numeric.");
			}

			AttributeStats att = inputFormat.attributeStats(j);
			if (att.missingCount > 0) {
				throw new Exception("Missing data is not supported.");
			}
		}

		if (m_OutputNumAtts == -1)
			m_OutputNumAtts = m_NumAttribs;

		// Add generic labels for the required number of unmixed sources
		ArrayList<Attribute> attributes = new ArrayList<Attribute>();
		for (int i = 0; i < m_OutputNumAtts; i++) {
			attributes.add(new Attribute("Source_" + Integer.toString(i)));
		}

	    // Copy the class attribute from the input (if set)
	    if (m_HasClass) {
	    	attributes.add((Attribute) m_TrainCopy.classAttribute().copy());
	    }

	    Instances outputFormat = new Instances(
				m_TrainCopy.relationName() + "_ICA", attributes, 0);

		// set the class to be the last attribute if necessary
	    if (m_HasClass) {
	    	outputFormat.setClassIndex(outputFormat.numAttributes() - 1);
		}

		m_OutputNumAtts = outputFormat.numAttributes();

	    return outputFormat;
	}

	/**
	 * Sets the format of the input instances.
	 *
	 * @param instanceInfo    an {@link Instances} object containing the input
	 *                instance structure (any instances contained
	 *                in the object are ignored - only the structure
	 *                is required).
	 * @return            true if the outputFormat may be collected
	 *                immediately
	 * @throws Exception      if the input format can't be set successfully
	 */
	public boolean setInputFormat(Instances instanceInfo) throws Exception {
		super.setInputFormat(instanceInfo);

		m_filter = null;
		m_HasClass = false;

		return false;
	}

	/**
	 *
	 * Transform a single instance into the ICA projected space based on the
	 * FastICA filter object. This should not be called prior to training the
	 * filter with input(), then calling batchFinished().
	 *
	 * @param instance 		an {@link Instance} object containing one value of
	 * 				each attribute
	 * @return 				a {@link Instance} object containing the result of
	 * 				projecting the input into the ICA domain
	 * @throws Exception	if the filter has not been trained, or the
	 * 				transformation does not succeed
	 */
	protected Instance convertInstance(Instance instance) throws Exception {
		Instance result;
		Instance tempInst;
		double[][] tempval;

		if (m_filter == null) {
			throw new Exception("No ICA instance has been trained.");
		}

		// make a copy of the instance and transform it
		tempInst = new DenseInstance(instance);
		if (instance.classIndex() >= 0) {
			tempInst.deleteAttributeAt(instance.classIndex());
		}
		tempval = m_filter.transform(new double[][]{ tempInst.toDoubleArray() });

		double[] newVals = new double[m_OutputNumAtts];
		for (int i = 0; i < tempval[0].length; i++) {
			double v = tempval[0][i];
			newVals[i] = v;
		}

		if (m_HasClass) {
			newVals[m_OutputNumAtts - 1] = instance.value(instance.classIndex());
		}

		// copy the results back into an instance
		if (instance instanceof SparseInstance) {
	      result = new SparseInstance(instance.weight(), newVals);
	    } else {
	      result = new DenseInstance(instance.weight(), newVals);
	    }

		return result;
	}

	/**
     * Input an instance for filtering. Filter requires all training instances be
     * read before producing output.
     *
     * @param instance the input instance
     * @return true if the filtered instance may now be collected with output().
     * @throws IllegalStateException if no input format has been set
     * @throws Exception if conversion fails
     */
    @Override
	public boolean input(Instance instance) throws Exception {
		Instance inst;

		if (getInputFormat() == null) {
			throw new IllegalStateException("No input instance format defined");
		}

		if (isNewBatch()) {
			resetQueue();
			m_NewBatch = false;
		}

		if (isFirstBatchDone()) {
			inst = convertInstance(instance);
			inst.setDataset(getOutputFormat());
			push(inst);
			return true;
		} else {
			bufferInput(instance);
			return false;
		}
	}

	/**
	 * Signify that this batch of input to the filter is finished. If
	 * the filter requires all instances prior to filtering, output()
	 * may now be called to retrieve the filtered instances. Any
	 * subsequent instances filtered should be filtered based on setting
	 * obtained from the first batch (unless the inputFormat has been
	 * re-assigned or new options have been set). This default
	 * implementation assumes all instance processing occurs during
	 * inputFormat() and input().
	 *
	 * @return 	true 					if there are instances pending output
	 * @throws 	NullPointerException 	if no input structure has
	 * 						been defined
	 * @throws 	Exception 				if there was a problem finishing the batch
	 */
	 @Override
	 public boolean batchFinished() throws Exception {
		Instances insts;
		Instance inst;

		if (getInputFormat() == null) {
			throw new NullPointerException("No input instance format defined");
		}

		insts = getInputFormat();

		if (!isFirstBatchDone()) {
			setup(insts);
		}

		for (int i = 0; i < insts.numInstances(); i++) {
			inst = convertInstance(insts.instance(i));
			inst.setDataset(getOutputFormat());
			push(inst);
		}

		flushInput();
		m_NewBatch = true;
		m_FirstBatchDone = true;

		return (numPendingOutput() != 0);
	}

	/**
	 * Run the filter on the input data
	 *
	 * @param	instances 		{@link Instances} containing data for
	 * 					training/fitting the {@link FastICA} filter
	 * @throws	Exception 		if the {@link FastICA} or any preprocessing
	 * 					filters encounter a problem
	 */
	protected void setup(Instances instances) throws Exception {

		Instances m_TrainInstances = new Instances(instances);

		m_TrainCopy = new Instances(m_TrainInstances, 0);

		// TODO: add filters to handle missing data and non-numeric attributes &
		// remove the errors on missing/non-numeric from determineOutputFormat
		// m_ReplaceMissingFilter = new ReplaceMissingValues();
	    // m_ReplaceMissingFilter.setInputFormat(m_TrainInstances);
	    // m_TrainInstances = Filter.useFilter(m_TrainInstances,
	    //   m_ReplaceMissingFilter);
		//
	    // m_NominalToBinaryFilter = new NominalToBinary();
	    // m_NominalToBinaryFilter.setInputFormat(m_TrainInstances);
	    // m_TrainInstances = Filter.useFilter(m_TrainInstances,
	    //   m_NominalToBinaryFilter);

		if (m_TrainInstances.classIndex() >= 0) {
			// get rid of the class column
			m_HasClass = true;
			m_ClassIndex = m_TrainInstances.classIndex();
			Remove removeFilter = new Remove();
			String[] options = new String[2];
			options[0] = "-R";
			options[1] = Integer.toString(instances.classIndex());
			removeFilter.setOptions(options);
			removeFilter.setInputFormat(instances);
			m_TrainInstances = Filter.useFilter(instances, removeFilter);
		}

		// can evaluator handle the processed data ? e.g., enough attributes?
	    getCapabilities().testWithFail(m_TrainInstances);

		m_NumInstances = m_TrainInstances.numInstances();
	    m_NumAttribs = m_TrainInstances.numAttributes();

		// Convert the data to a row-indexed double[][]
		double[][] v = new double[m_NumInstances][];
		for (int i = 0; i < m_NumInstances; i++) {
			v[i] = m_TrainInstances.instance(i).toDoubleArray();
		}

		// Perform ICA
		m_filter = new FastICA(m_tolerance, m_numIterations, m_whiten);
		m_filter.fit(v, m_NumAttribs);

		m_TransformedFormat = determineOutputFormat(m_TrainInstances);
		setOutputFormat(m_TransformedFormat);

		m_TrainInstances = null;
	}

	  /**
	   * Returns the revision string.
	   *
	   * @return		the revision
	   */
	public String getRevision() {
	    return RevisionUtils.extract("$Revision: 1.0 $");
	  }

	/**
	 * Main method for running this filter.
	 *
	 * @param args 	should contain arguments to the filter: use -h for help
	 */
	public static void main(String[] args) {
		runFilter(new IndependentComponents(), args);
	}

}
