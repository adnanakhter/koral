package de.uni_koblenz.west.cidre.common.query.execution;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;

import de.uni_koblenz.west.cidre.common.executor.WorkerTask;

/**
 * Supertype of all query operations.
 * 
 * @author Daniel Janke &lt;danijankATuni-koblenz.de&gt;
 *
 */
public interface QueryOperatorTask extends WorkerTask, Serializable {

	public long[] getResultVariables();

	/**
	 * @return -1 iff no join var exists
	 */
	public long getFirstJoinVar();

	public byte[] serialize();

	public void serialize(DataOutputStream output) throws IOException;

}
