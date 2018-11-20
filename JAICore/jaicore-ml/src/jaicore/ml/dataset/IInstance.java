package jaicore.ml.dataset;

import jaicore.ml.dataset.attribute.IAttributeValue;

public interface IInstance {

	public <T> IAttributeValue<T> getAttributeValue(int position, Class<T> type);

	public <T> IAttributeValue<T> getTargetValue(Class<T> type);

	public double[] getAsDoubleVector();

}
